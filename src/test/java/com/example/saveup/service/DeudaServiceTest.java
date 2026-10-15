package com.example.saveup.service;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.dto.PagoDeudaResponseDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO.CuotaSimulacionDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.ModoPago;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.finanzas.CondicionesCredito;
import com.example.saveup.service.finanzas.CuotaAmortizacion;
import com.example.saveup.service.finanzas.ImputacionPagos;
import com.example.saveup.service.finanzas.MotorFinanciero;
import com.example.saveup.service.finanzas.ResultadoCalculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeudaServiceTest {

    private static final String RUT = "11223344-5";

    @Mock
    private DeudaRepository deudaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private SecurityUtils securityUtils;

    private DeudaService deudaService;

    private Usuario usuario;
    private Deuda deuda;

    @BeforeEach
    void setUp() {
        deudaService = new DeudaService(deudaRepository, usuarioRepository, movimientoRepository,
                categoriaRepository, securityUtils, new MotorFinanciero());

        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Deudor Test");

        deuda = new Deuda();
        deuda.setId(1L);
        deuda.setNombre("Crédito Universitario");
        deuda.setMontoTotal(new BigDecimal("100000.00"));
        deuda.setCantidadCuotas(10);
        deuda.setEstado(EstadoDeuda.PENDIENTE);
        deuda.setUsuario(usuario);
        deuda.setFechaCreacion(new Date());
        deuda.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        deuda.setMontoCapital(new BigDecimal("100000.00"));
        deuda.setTasaMensual(BigDecimal.ZERO);
        deuda.setValorCuota(new BigDecimal("10000.00"));
        deuda.setGastosIniciales(BigDecimal.ZERO);
        deuda.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        deuda.setCuotasPagadasPrevias(0);
        deuda.setMontoPagadoPrevio(BigDecimal.ZERO);
    }

    // ──────────────────────── helpers ────────────────────────

    private static final Date HOY = new Date();

    /** Deuda SIN_INTERES sin pagos, como la devuelve la proyección JPQL. */
    private static DeudaResponseDTO proyeccion(Long id, BigDecimal montoTotal, int cuotas, BigDecimal pagosReales) {
        return proyeccion(id, "Deuda", montoTotal, cuotas, EstadoDeuda.PENDIENTE, pagosReales, 0L,
                ModalidadCalculo.SIN_INTERES, montoTotal, BigDecimal.ZERO, null, 0, BigDecimal.ZERO, null);
    }

    /** Igual, pero con nombre. */
    private static DeudaResponseDTO proyeccion(Long id, String nombre, BigDecimal montoTotal, int cuotas,
                                               BigDecimal pagosReales) {
        return proyeccion(id, nombre, montoTotal, cuotas, EstadoDeuda.PENDIENTE, pagosReales, 0L,
                ModalidadCalculo.SIN_INTERES, montoTotal, BigDecimal.ZERO, null, 0, BigDecimal.ZERO, null);
    }

    /** Igual, pero con estado y cantidad de pagos. */
    private static DeudaResponseDTO proyeccion(Long id, BigDecimal montoTotal, int cuotas, EstadoDeuda estado,
                                               BigDecimal pagosReales, Long pagos) {
        return proyeccion(id, "Deuda", montoTotal, cuotas, estado, pagosReales, pagos,
                ModalidadCalculo.SIN_INTERES, montoTotal, BigDecimal.ZERO, null, 0, BigDecimal.ZERO, null);
    }

    /** Igual, pero con las condiciones financieras completas. */
    private static DeudaResponseDTO proyeccion(Long id, BigDecimal montoTotal, int cuotas, EstadoDeuda estado,
                                               BigDecimal pagosReales, Long pagos, ModalidadCalculo modalidad,
                                               BigDecimal capital, BigDecimal tasaFraccion, BigDecimal valorCuota,
                                               int previas, BigDecimal previo, LocalDate fechaPrimeraCuota) {
        return proyeccion(id, "Deuda", montoTotal, cuotas, estado, pagosReales, pagos, modalidad, capital,
                tasaFraccion, valorCuota, previas, previo, fechaPrimeraCuota);
    }

    /** Proyección JPQL completa (mismo orden de parámetros que el constructor del DTO). */
    private static DeudaResponseDTO proyeccion(Long id, String nombre, BigDecimal montoTotal, int cuotas,
                                               EstadoDeuda estado, BigDecimal pagosReales, Long pagos,
                                               ModalidadCalculo modalidad, BigDecimal capital,
                                               BigDecimal tasaFraccion, BigDecimal valorCuota,
                                               int previas, BigDecimal previo, LocalDate fechaPrimeraCuota) {
        return new DeudaResponseDTO(id, nombre, "Desc", montoTotal, cuotas, estado, HOY,
                pagosReales, pagos, TipoDeuda.OTRO, modalidad, capital, tasaFraccion, valorCuota,
                BigDecimal.ZERO, BigDecimal.ZERO, montoTotal, BigDecimal.ZERO,
                fechaPrimeraCuota, previas, previo);
    }

    private Categoria categoriaDeudas() {
        return new Categoria(1L, "Deudas", "ic_payment", "#795548", TipoPresupuesto.NECESIDAD);
    }

    /** Construye una deuda persistida a partir de una condición del motor (para pruebas con interés). */
    private Deuda deudaDelMotor(ModalidadCalculo modalidad, BigDecimal capital, int cuotas,
                                BigDecimal tasaFraccion, BigDecimal valorCuota) {
        MotorFinanciero motor = new MotorFinanciero();
        ResultadoCalculo calculo = motor.calcular(new CondicionesCredito(
                modalidad, capital, cuotas, tasaFraccion, valorCuota, BigDecimal.ZERO, BigDecimal.ZERO));

        Deuda d = new Deuda();
        d.setId(1L);
        d.setNombre("Deuda con interés");
        d.setMontoTotal(calculo.montoTotal());
        d.setCantidadCuotas(cuotas);
        d.setEstado(EstadoDeuda.PENDIENTE);
        d.setUsuario(usuario);
        d.setFechaCreacion(new Date());
        d.setModalidadCalculo(modalidad);
        d.setMontoCapital(capital);
        d.setTasaMensual(calculo.tasaMensual());
        d.setValorCuota(calculo.valorCuota());
        d.setGastosIniciales(BigDecimal.ZERO);
        d.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        d.setCuotasPagadasPrevias(0);
        d.setMontoPagadoPrevio(BigDecimal.ZERO);
        return d;
    }

    private void stubBuscarDeuda() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
    }

    private void stubGuardarPago() {
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ──────────────────────── crear / listar ────────────────────────

    @Test
    @DisplayName("crearDeuda debe inferir automáticamente el RUT del SecurityContext")
    void crearDeuda_asignaRutDesdeContextoSeguro() {
        DeudaCreacionDTO dto = new DeudaCreacionDTO();
        dto.setNombre("Crédito Universitario");
        dto.setDescripcion("Cuotas");
        dto.setMontoTotal(new BigDecimal("100000.00"));
        dto.setCantidadCuotas(10);

        when(securityUtils.getAuthenticatedRut()).thenReturn(RUT);
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(deudaRepository.save(any(Deuda.class))).thenReturn(deuda);
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, "Crédito Universitario", new BigDecimal("100000.00"), 10, BigDecimal.ZERO)));

        DeudaResponseDTO result = deudaService.crearDeuda(dto);

        assertNotNull(result);
        assertEquals("Crédito Universitario", result.getNombre());
        assertEquals(0, new BigDecimal("100000.00").compareTo(result.getTotalPorPagar()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(result.getSaldoCapital()));
        assertEquals(0, result.getCuotasPagadas());
        verify(securityUtils).getAuthenticatedRut();
        verify(deudaRepository).save(any(Deuda.class));
    }

    @Test
    @DisplayName("obtenerDeudasPorUsuario debe utilizar la proyección JPQL optimizada (1 sola query, sin N+1)")
    void obtenerDeudasPorUsuario_ejecutaConsultaOptimizadaSinNPlusOne() {
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        DeudaResponseDTO dto1 = proyeccion(1L, "Tarjeta Crédito", new BigDecimal("50000.00"), 5,
                new BigDecimal("-20000.00"));
        DeudaResponseDTO dto2 = proyeccion(2L, "Préstamo Auto", new BigDecimal("200000.00"), 24,
                new BigDecimal("-50000.00"));

        when(deudaRepository.findDeudasDTOByUsuarioRut(RUT)).thenReturn(List.of(dto1, dto2));

        List<DeudaResponseDTO> result = deudaService.obtenerDeudasPorUsuario(RUT);

        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("20000.00").compareTo(result.get(0).getMontoPagado()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(result.get(0).getMontoRestante()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(result.get(0).getTotalPorPagar()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(result.get(0).getSaldoCapital()));
        assertEquals(2, result.get(0).getCuotasPagadas());

        // Verificamos que se llamó a la proyección directa y NO a consultas individuales por cada deuda
        verify(deudaRepository, times(1)).findDeudasDTOByUsuarioRut(RUT);
        verify(deudaRepository, never()).findTotalPagadoPorDeuda(any());
        verify(deudaRepository, never()).countPagosPorDeuda(any());
    }

    @Test
    @DisplayName("El servicio calcula tasa anual efectiva, valor de cuota y fecha de última cuota con el motor")
    void obtenerDeudasPorUsuario_enriqueceConMotorYCalendario() {
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        DeudaResponseDTO conTasa = proyeccion(1L, "Con tasa", new BigDecimal("110000.00"), 12,
                EstadoDeuda.PENDIENTE, BigDecimal.ZERO, 0L, ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), new BigDecimal("0.015"), new BigDecimal("9166.86"),
                0, BigDecimal.ZERO, LocalDate.of(2027, 1, 31));
        DeudaResponseDTO sinValorCuota = proyeccion(2L, new BigDecimal("100000.00"), 10, BigDecimal.ZERO);

        when(deudaRepository.findDeudasDTOByUsuarioRut(RUT)).thenReturn(List.of(conTasa, sinValorCuota));

        List<DeudaResponseDTO> result = deudaService.obtenerDeudasPorUsuario(RUT);

        // porcentaje de la tasa mensual (fracción → porcentaje, regla 40)
        assertEquals(0, new BigDecimal("1.5000").compareTo(result.get(0).getTasaMensualPorcentaje()));
        // tasa anual efectiva = (1 + 0.015)^12 − 1 = 19.56 %
        assertEquals(0, new BigDecimal("19.56").compareTo(result.get(0).getTasaAnualEfectiva()));
        // el valor de cuota persistido se respeta
        assertEquals(0, new BigDecimal("9166.86").compareTo(result.get(0).getValorCuota()));
        // 12 cuotas desde el 31-ene-2027 → última el 31-dic-2027
        assertEquals(LocalDate.of(2027, 12, 31), result.get(0).getFechaUltimaCuota());

        // sin valor de cuota persistido, se divide el total en las cuotas
        assertEquals(0, new BigDecimal("10000.00").compareTo(result.get(1).getValorCuota()));
        // sin fecha de primera cuota no hay fecha de última cuota
        assertNull(result.get(1).getFechaUltimaCuota());
    }

    @Test
    @DisplayName("La tasa mensual se expone como porcentaje; la fracción cruda no sale en el JSON")
    void deudaResponseDTO_exponeTasaComoPorcentaje() throws NoSuchFieldException {
        assertNotNull(DeudaResponseDTO.class.getDeclaredField("tasaMensualPorcentaje"));
        // El dato crudo existe en el DTO para que el servicio recalcule, pero no se expone
        assertNotNull(DeudaResponseDTO.class.getDeclaredField("tasaMensualFraccion"));

        when(usuarioRepository.existsById(RUT)).thenReturn(true);
        DeudaResponseDTO conTasa = proyeccion(1L, new BigDecimal("100000.00"), 12,
                EstadoDeuda.PENDIENTE, BigDecimal.ZERO, 0L, ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000.00"), new BigDecimal("0.015"), new BigDecimal("9166.86"),
                0, BigDecimal.ZERO, null);
        when(deudaRepository.findDeudasDTOByUsuarioRut(RUT)).thenReturn(List.of(conTasa));

        List<DeudaResponseDTO> result = deudaService.obtenerDeudasPorUsuario(RUT);

        // 0.015 de fracción se muestra como 1.5 % de porcentaje
        assertEquals(0, new BigDecimal("1.5000").compareTo(result.get(0).getTasaMensualPorcentaje()));
    }

    @Test
    @DisplayName("Listar una deuda CUOTA_CONOCIDA la enriquece sin lanzar excepción")
    void obtenerDeudasPorUsuario_deudaCuotaConocida_enriquece() {
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        DeudaResponseDTO cuotaConocida = proyeccion(1L, new BigDecimal("100000.00"), 10,
                EstadoDeuda.PENDIENTE, BigDecimal.ZERO, 0L, ModalidadCalculo.CUOTA_CONOCIDA,
                new BigDecimal("100000.00"), new BigDecimal("0.03"), new BigDecimal("10000.00"),
                0, BigDecimal.ZERO, LocalDate.of(2026, 10, 7));

        when(deudaRepository.findDeudasDTOByUsuarioRut(RUT)).thenReturn(List.of(cuotaConocida));

        List<DeudaResponseDTO> result = deudaService.obtenerDeudasPorUsuario(RUT);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("3.0000").compareTo(cuotaConocida.getTasaMensualPorcentaje()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(cuotaConocida.getValorCuota()));
        assertEquals(LocalDate.of(2027, 7, 7), cuotaConocida.getFechaUltimaCuota());
        assertEquals(0, cuotaConocida.getCuotasPagadas());
        assertEquals(0, new BigDecimal("100000.00").compareTo(cuotaConocida.getSaldoCapital()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(cuotaConocida.getTotalPorPagar()));
    }

    @Test
    @DisplayName("Una deuda que no se puede recalcular deja sus derivados en null y no rompe el listado")
    void obtenerDeudasPorUsuario_deudaInvalida_mantieneListado() {
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        DeudaResponseDTO valida = proyeccion(1L, new BigDecimal("50000.00"), 5, BigDecimal.ZERO);
        DeudaResponseDTO invalida = proyeccion(2L, new BigDecimal("100000.00"), 10, BigDecimal.ZERO);
        invalida.setMontoCapital(BigDecimal.ZERO); // dato histórico inválido: el motor rechaza

        when(deudaRepository.findDeudasDTOByUsuarioRut(RUT)).thenReturn(List.of(valida, invalida));

        List<DeudaResponseDTO> result = deudaService.obtenerDeudasPorUsuario(RUT);

        assertEquals(2, result.size());
        assertNotNull(valida.getTotalPorPagar());
        assertNull(invalida.getTotalPorPagar(), "Los campos derivados quedan en null si el motor falla");
        assertNull(invalida.getSaldoCapital());
        assertNull(invalida.getMontoPagado());
        assertEquals(0, invalida.getCuotasPagadas());
    }

    // ──────────────────────── registrarPago ────────────────────────

    @Test
    @DisplayName("registrarPago actualiza la deuda a PAGADA si cubre el total")
    void registrarPago_saldoTotal_marcaDeudaPagada() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new BigDecimal("100000.00"));
        pagoDTO.setDescripcion("Pago total");

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(proyeccion(1L,
                new BigDecimal("100000.00"), 10, EstadoDeuda.PAGADA, new BigDecimal("-100000.00"), 1L)));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(EstadoDeuda.PAGADA, deuda.getEstado());
        assertEquals(0, new BigDecimal("-100000.00").compareTo(result.movimiento().getMonto()));
        assertEquals(10, result.deuda().getCuotasPagadas());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getTotalPorPagar()));
        verify(movimientoRepository).save(any(Movimiento.class));
        verify(securityUtils).validarPropietario(usuario.getRut());
    }

    @Test
    @DisplayName("DeudaService y registrarPago manejan centavos con precisión")
    void registrarPago_conCentavos_calculaMontoRestanteExacto() {
        deuda.setMontoTotal(new BigDecimal("1250.75"));
        deuda.setMontoCapital(new BigDecimal("1250.75"));

        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new BigDecimal("450.25"));
        pagoDTO.setDescripcion("Pago parcial con centavos");

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(proyeccion(1L,
                new BigDecimal("1250.75"), 10, EstadoDeuda.PENDIENTE, new BigDecimal("-450.25"), 1L,
                ModalidadCalculo.SIN_INTERES, new BigDecimal("1250.75"), BigDecimal.ZERO, null, 0, BigDecimal.ZERO, null)));

        DeudaResponseDTO result = deudaService.registrarPago(1L, pagoDTO).deuda();

        assertNotNull(result);
        assertEquals(0, new BigDecimal("450.25").compareTo(result.getMontoPagado()));
        assertEquals(0, new BigDecimal("800.50").compareTo(result.getMontoRestante()));
        assertEquals(3, result.getCuotasPagadas(), "Un pago parcial no suma una cuota completa");
        assertEquals(EstadoDeuda.PENDIENTE, deuda.getEstado());
    }

    @Test
    @DisplayName("T1d: el pago se guarda negativo aunque el DTO traiga el monto positivo")
    void registrarPago_movimientoQuedaNegativo() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new BigDecimal("20000.00")); // positivo: el cliente no sabe la convención
        pagoDTO.setDescripcion("Cuota 1");

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, BigDecimal.ZERO)));

        deudaService.registrarPago(1L, pagoDTO);

        org.mockito.ArgumentCaptor<Movimiento> captor =
                org.mockito.ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());

        assertEquals(0, new BigDecimal("-20000.00").compareTo(captor.getValue().getMonto()),
                "PAGO_DEUDA baja el saldo: el movimiento debe quedar negativo");
        assertEquals(TipoMovimiento.PAGO_DEUDA, captor.getValue().getTipoMovimiento());
    }

    @Test
    @DisplayName("T1d: pago con monto 0 → error y no se guarda nada")
    void registrarPago_montoCero_error() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(BigDecimal.ZERO);
        pagoDTO.setDescripcion("Cuota inválida");

        stubBuscarDeuda();
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> deudaService.registrarPago(1L, pagoDTO));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("MONTO_PERSONALIZADO sin monto → 400 (IllegalArgumentException)")
    void registrarPago_montoNulo_error() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Cuota sin monto");

        stubBuscarDeuda();
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> deudaService.registrarPago(1L, pagoDTO));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Sobrepago → 409 (IllegalStateException)")
    void registrarPago_sobrepago_devuelve409() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new BigDecimal("100001.00"));
        pagoDTO.setDescripcion("Pago excesivo");

        stubBuscarDeuda();
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> deudaService.registrarPago(1L, pagoDTO));
        assertTrue(ex.getMessage().contains("supera el total por pagar"));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Deuda CANCELADA o PAGADA → 409")
    void registrarPago_deudaCerrada_devuelve409() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new BigDecimal("10000.00"));
        pagoDTO.setDescripcion("Cuota");

        for (EstadoDeuda estadoCerrado : List.of(EstadoDeuda.PAGADA, EstadoDeuda.CANCELADA)) {
            deuda.setEstado(estadoCerrado);
            stubBuscarDeuda();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> deudaService.registrarPago(1L, pagoDTO));
            assertTrue(ex.getMessage().contains("pendientes"));
            verifyNoInteractions(movimientoRepository);
        }
    }

    @Test
    @DisplayName("CUOTA_CALCULADA cobra la cuota completa de la próxima impaga")
    void registrarPago_cuotaCalculada_completa() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new BigDecimal("500.00")); // se ignora
        pagoDTO.setDescripcion("Cuota 1");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, new BigDecimal("-10000.00"))));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(0, new BigDecimal("-10000.00").compareTo(result.movimiento().getMonto()));
        assertEquals(1, result.deuda().getCuotasPagadas());
        assertEquals(0, new BigDecimal("90000.00").compareTo(result.deuda().getTotalPorPagar()));
    }

    @Test
    @DisplayName("CUOTA_CALCULADA sobre una cuota parcial cobra solo lo que falta")
    void registrarPago_cuotaCalculada_sobreCuotaParcial_cobraElRestante() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Completar cuota 2");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(new BigDecimal("-15000.00"));
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, new BigDecimal("-20000.00"))));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(0, new BigDecimal("-5000.00").compareTo(result.movimiento().getMonto()));
        assertEquals(2, result.deuda().getCuotasPagadas());
    }

    @Test
    @DisplayName("CUOTA_CALCULADA con costo adicional cobra cuota + costo adicional")
    void registrarPago_cuotaCalculada_conCostoAdicional() {
        deuda.setMontoTotal(new BigDecimal("105000.00"));
        deuda.setCostoAdicionalPorCuota(new BigDecimal("500"));
        deuda.setValorCuota(new BigDecimal("10500.00"));

        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Cuota con costo");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("105000.00"), 10, new BigDecimal("-10500.00"))));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(0, new BigDecimal("-10500.00").compareTo(result.movimiento().getMonto()));
    }

    @Test
    @DisplayName("Pago de la última cuota → deuda PAGADA")
    void registrarPago_ultimaCuota_marcaPagada() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Última cuota");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(new BigDecimal("-90000.00"));
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(proyeccion(1L,
                new BigDecimal("100000.00"), 10, EstadoDeuda.PAGADA, new BigDecimal("-100000.00"), 10L)));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(EstadoDeuda.PAGADA, deuda.getEstado());
        assertEquals(10, result.deuda().getCuotasPagadas());
    }

    @Test
    @DisplayName("Escenario del usuario: saldo 20.000, deuda 100.000/10 sin interés, CUOTA_CALCULADA")
    void escenarioUsuario_pagoCuotaCalculada_resultado() {        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Cuota calculada");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        stubBuscarDeuda();
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, new BigDecimal("-10000.00"))));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        // el pago baja 10.000 el saldo (20.000 → 10.000) y deja 1 cuota pagada
        assertEquals(0, new BigDecimal("-10000.00").compareTo(result.movimiento().getMonto()));
        assertEquals(1, result.deuda().getCuotasPagadas());
        assertEquals(0, new BigDecimal("90000.00").compareTo(result.deuda().getTotalPorPagar()));
        assertEquals(0, new BigDecimal("90000.00").compareTo(result.deuda().getSaldoCapital()));
    }

    // ──────────────────────── Cierre de la deuda con interés (regla 40) ────────────────────────

    @Test
    @DisplayName("CUOTA_CALCULADA sobre deuda TASA_CONOCIDA: última cuota → PAGADA, totalPorPagar y saldoCapital 0,00")
    void registrarPago_ultimaCuotaTasaConocida_cierraEnCero() {
        Deuda conTasa = deudaDelMotor(ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.015"), null);
        ResultadoCalculo calculo = new MotorFinanciero().calcular(new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"), 12,
                new BigDecimal("0.015"), null, BigDecimal.ZERO, BigDecimal.ZERO));
        BigDecimal ultimaCuota = calculo.tabla().getLast().cuota();
        BigDecimal pagadoPrevio = calculo.montoTotal().subtract(ultimaCuota);

        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Última cuota (tasa conocida)");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(conTasa));
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(pagadoPrevio.negate());
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, calculo.montoTotal(), 12, EstadoDeuda.PAGADA,
                        calculo.montoTotal().negate(), 12L, ModalidadCalculo.TASA_CONOCIDA,
                        new BigDecimal("100000"), new BigDecimal("0.015"), calculo.valorCuota(),
                        0, BigDecimal.ZERO, null)));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(0, ultimaCuota.negate().compareTo(result.movimiento().getMonto()),
                "CUOTA_CALCULADA cobra exactamente la última cuota, con interés incluido");
        assertEquals(EstadoDeuda.PAGADA, conTasa.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getTotalPorPagar()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getSaldoCapital()));
        assertEquals(12, result.deuda().getCuotasPagadas());
    }

    @Test
    @DisplayName("CUOTA_CALCULADA sobre deuda CUOTA_CONOCIDA: última cuota → PAGADA, totalPorPagar y saldoCapital 0,00")
    void registrarPago_ultimaCuotaCuotaConocida_cierraEnCero() {
        Deuda cuotaConocida = deudaDelMotor(ModalidadCalculo.CUOTA_CONOCIDA,
                new BigDecimal("100000"), 12, null, new BigDecimal("8884.88"));
        ResultadoCalculo calculo = new MotorFinanciero().calcular(new CondicionesCredito(
                ModalidadCalculo.CUOTA_CONOCIDA, new BigDecimal("100000"), 12,
                null, new BigDecimal("8884.88"), BigDecimal.ZERO, BigDecimal.ZERO));
        BigDecimal ultimaCuota = calculo.tabla().getLast().cuota();
        BigDecimal pagadoPrevio = calculo.montoTotal().subtract(ultimaCuota);

        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setDescripcion("Última cuota (cuota conocida)");
        pagoDTO.setModoPago(ModoPago.CUOTA_CALCULADA);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(cuotaConocida));
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(pagadoPrevio.negate());
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, calculo.montoTotal(), 12, EstadoDeuda.PAGADA,
                        calculo.montoTotal().negate(), 12L, ModalidadCalculo.CUOTA_CONOCIDA,
                        new BigDecimal("100000"), null, new BigDecimal("8884.88"),
                        0, BigDecimal.ZERO, null)));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(0, ultimaCuota.negate().compareTo(result.movimiento().getMonto()));
        assertEquals(EstadoDeuda.PAGADA, cuotaConocida.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getTotalPorPagar()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getSaldoCapital()));
        assertEquals(12, result.deuda().getCuotasPagadas());
    }

    @Test
    @DisplayName("MONTO_PERSONALIZADO que cubre el total de una deuda con tasa → PAGADA con saldos en 0,00")
    void registrarPago_liquidacionConTasa_cierraEnCero() {
        Deuda conTasa = deudaDelMotor(ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.015"), null);
        BigDecimal montoTotal = conTasa.getMontoTotal();

        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(montoTotal);
        pagoDTO.setDescripcion("Liquidación total");

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(conTasa));
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(categoriaDeudas()));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(BigDecimal.ZERO);
        stubGuardarPago();
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, montoTotal, 12, EstadoDeuda.PAGADA,
                        montoTotal.negate(), 1L, ModalidadCalculo.TASA_CONOCIDA,
                        new BigDecimal("100000"), new BigDecimal("0.015"), conTasa.getValorCuota(),
                        0, BigDecimal.ZERO, null)));

        DeudaService.ResultadoPago result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(EstadoDeuda.PAGADA, conTasa.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getTotalPorPagar()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deuda().getSaldoCapital()));
        assertEquals(12, result.deuda().getCuotasPagadas(),
                "Al cubrir el total, las 12 cuotas quedan imputadas como PAGADA");
    }

    // ──────────────────────── detalle y pagos (T3) ────────────────────────

    private static Movimiento pago(long id, String monto, long fechaMillis) {
        Movimiento m = new Movimiento();
        m.setId(id);
        m.setMonto(new BigDecimal(monto));
        m.setFecha(new Date(fechaMillis));
        m.setDescripcion("Pago " + id);
        m.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        return m;
    }

    private void stubPagos(Movimiento... pagos) {
        when(movimientoRepository.findByDeudaIdAndTipoMovimientoOrderByFechaAscIdAsc(1L, TipoMovimiento.PAGO_DEUDA))
                .thenReturn(List.of(pagos));
    }

    private static BigDecimal suma(List<ImputacionPagos.ImputacionCuota> filas,
                                   java.util.function.Function<ImputacionPagos.ImputacionCuota, BigDecimal> componente) {
        BigDecimal total = BigDecimal.ZERO;
        for (ImputacionPagos.ImputacionCuota fila : filas) {
            total = total.add(componente.apply(fila));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("obtenerDeuda valida la propiedad y devuelve el DTO enriquecido")
    void obtenerDeuda_propia_devuelveEnriquecida() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, BigDecimal.ZERO)));

        DeudaResponseDTO dto = deudaService.obtenerDeuda(1L);

        assertNotNull(dto);
        assertEquals(0, new BigDecimal("100000.00").compareTo(dto.getTotalPorPagar()));
        verify(securityUtils).validarPropietario(RUT);
    }

    @Test
    @DisplayName("Anti-IDOR: obtenerDeuda de una deuda ajena propaga AccessDeniedException (403)")
    void obtenerDeuda_ajena_deniega() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        doThrow(new AccessDeniedException("Acceso denegado")).when(securityUtils).validarPropietario(RUT);

        assertThrows(AccessDeniedException.class, () -> deudaService.obtenerDeuda(1L));
        verify(deudaRepository, never()).findDeudaDTOById(any());
    }

    @Test
    @DisplayName("Anti-IDOR: obtenerPagos de una deuda ajena propaga AccessDeniedException (403)")
    void obtenerPagos_ajena_deniega() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        doThrow(new AccessDeniedException("Acceso denegado")).when(securityUtils).validarPropietario(RUT);

        assertThrows(AccessDeniedException.class, () -> deudaService.obtenerPagos(1L, 0, 20));
        verify(movimientoRepository, never())
                .findByDeudaIdAndTipoMovimientoOrderByFechaAscIdAsc(any(), any());
    }

    @Test
    @DisplayName("Anti-IDOR: obtenerAmortizacion de una deuda ajena propaga AccessDeniedException (403)")
    void obtenerAmortizacion_ajena_deniega() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        doThrow(new AccessDeniedException("Acceso denegado")).when(securityUtils).validarPropietario(RUT);

        assertThrows(AccessDeniedException.class, () -> deudaService.obtenerAmortizacion(1L));
    }

    @Test
    @DisplayName("Tras un pago parcial, el detalle expone la próxima cuota impaga y lo que le falta")
    void obtenerDeuda_pagoParcial_muestraProximaCuota() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        // 25.000 pagados de 10 cuotas de 10.000: la cuota 3 queda PARCIAL con 5.000
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, new BigDecimal("-25000.00"))));

        DeudaResponseDTO dto = deudaService.obtenerDeuda(1L);

        assertEquals(3, dto.getProximaCuotaNumero());
        assertEquals(0, new BigDecimal("5000.00").compareTo(dto.getProximaCuotaMontoPendiente()));
        assertNull(dto.getProximaFechaVencimiento());
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.getInteresesPagados()));
    }

    @Test
    @DisplayName("La próxima cuota impaga expone su vencimiento cuando hay calendario")
    void obtenerDeuda_conFecha_muestraVencimientoProximo() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, new BigDecimal("100000.00"), 10, EstadoDeuda.PENDIENTE,
                        new BigDecimal("-20000.00"), 2L, ModalidadCalculo.SIN_INTERES,
                        new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("10000.00"),
                        0, BigDecimal.ZERO, LocalDate.of(2026, 1, 31))));

        DeudaResponseDTO dto = deudaService.obtenerDeuda(1L);

        assertEquals(3, dto.getProximaCuotaNumero());
        assertEquals(0, new BigDecimal("10000.00").compareTo(dto.getProximaCuotaMontoPendiente()));
        assertEquals(LocalDate.of(2026, 3, 31), dto.getProximaFechaVencimiento());
    }

    @Test
    @DisplayName("interesesPagados suma el interés imputado, incluidas las cuotas previas")
    void obtenerDeuda_interesesPagados_incluyePrevias() {
        Deuda conTasa = deudaDelMotor(ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"), 12,
                new BigDecimal("0.015"), null);
        conTasa.setCuotasPagadasPrevias(2);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(conTasa));
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(
                proyeccion(1L, conTasa.getMontoTotal(), 12, EstadoDeuda.PENDIENTE,
                        new BigDecimal("-15000.00"), 1L, ModalidadCalculo.TASA_CONOCIDA,
                        new BigDecimal("100000"), new BigDecimal("0.015"), conTasa.getValorCuota(),
                        2, BigDecimal.ZERO, null)));

        DeudaResponseDTO dto = deudaService.obtenerDeuda(1L);

        List<CuotaAmortizacion> tabla = new MotorFinanciero().calcular(new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"), 12,
                new BigDecimal("0.015"), null, BigDecimal.ZERO, BigDecimal.ZERO)).tabla();
        BigDecimal esperado = suma(ImputacionPagos.imputar(tabla, 2, new BigDecimal("15000")),
                ImputacionPagos.ImputacionCuota::interes);

        assertEquals(0, esperado.compareTo(dto.getInteresesPagados()));
        assertTrue(dto.getInteresesPagados().signum() > 0, "Con tasa conocida debe haber interés pagado");
    }

    @Test
    @DisplayName("El cronograma marca estado, monto pagado y previa según la imputación")
    void obtenerAmortizacion_marcaEstadosYPrevias() {
        Deuda conTasa = deudaDelMotor(ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"), 12,
                new BigDecimal("0.015"), null);
        conTasa.setCuotasPagadasPrevias(2);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(conTasa));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(new BigDecimal("-15000.00"));

        List<CuotaSimulacionDTO> tabla = deudaService.obtenerAmortizacion(1L);

        assertEquals(12, tabla.size());
        assertTrue(tabla.get(0).getPrevia(), "La cuota 1 es previa");
        assertTrue(tabla.get(1).getPrevia(), "La cuota 2 es previa");
        assertEquals(ImputacionPagos.EstadoCuota.PAGADA, tabla.get(0).getEstado());
        assertFalse(tabla.get(11).getPrevia());
        assertEquals(ImputacionPagos.EstadoCuota.PENDIENTE, tabla.get(11).getEstado());
        // Los 15.000 reales cubren la cuota 3 y dejan la 4 en PARCIAL.
        assertEquals(ImputacionPagos.EstadoCuota.PARCIAL, tabla.get(3).getEstado());
        assertTrue(tabla.get(3).getMontoPagado().signum() > 0);
    }

    @Test
    @DisplayName("Deuda sin pagos: historial paginado vacío")
    void obtenerPagos_sinPagos_listaVacia() {        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        stubPagos();

        PageResponseDTO<PagoDeudaResponseDTO> page = deudaService.obtenerPagos(1L, 0, 20);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalItems());
        assertEquals(0, page.getTotalPages());
        verify(securityUtils).validarPropietario(RUT);
    }

    @Test
    @DisplayName("Historial ordenado por fecha desc, con el desglose capital/interés/costos de cada pago")
    void obtenerPagos_ordenDescYDesglose() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        stubPagos(
                pago(1L, "-25000.00", 1_000L),
                pago(2L, "-5000.00", 2_000L));

        PageResponseDTO<PagoDeudaResponseDTO> page = deudaService.obtenerPagos(1L, 0, 20);

        assertEquals(2, page.getContent().size());
        assertEquals(2L, page.getContent().get(0).getId(), "El pago más reciente va primero");
        assertEquals(0, new BigDecimal("5000.00").compareTo(page.getContent().get(0).getCapital()));
        assertEquals(0, new BigDecimal("25000.00").compareTo(page.getContent().get(1).getCapital()));
        assertEquals(0, BigDecimal.ZERO.compareTo(page.getContent().get(0).getInteres()));
        assertEquals(0, BigDecimal.ZERO.compareTo(page.getContent().get(0).getCostos()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(page.getContent().get(0).getMonto()));
        assertEquals(2, page.getTotalItems());
        assertEquals(1, page.getTotalPages());
    }

    @Test
    @DisplayName("El historial pagina en memoria y respeta page/size")
    void obtenerPagos_paginacion() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        stubPagos(
                pago(1L, "-10000.00", 1_000L),
                pago(2L, "-10000.00", 2_000L),
                pago(3L, "-10000.00", 3_000L));

        PageResponseDTO<PagoDeudaResponseDTO> primera = deudaService.obtenerPagos(1L, 0, 2);
        PageResponseDTO<PagoDeudaResponseDTO> segunda = deudaService.obtenerPagos(1L, 1, 2);

        assertEquals(2, primera.getContent().size());
        assertEquals(3L, primera.getContent().get(0).getId());
        assertEquals(2L, primera.getContent().get(1).getId());
        assertEquals(2, primera.getTotalPages());
        assertEquals(3, segunda.getTotalItems());
        assertEquals(1, segunda.getContent().size());
        assertEquals(1L, segunda.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Una página fuera de rango devuelve contenido vacío sin romper (no 500)")
    void obtenerPagos_paginaFueraDeRango_vacia() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        stubPagos(pago(1L, "-10000.00", 1_000L));

        PageResponseDTO<PagoDeudaResponseDTO> page = deudaService.obtenerPagos(1L, Integer.MAX_VALUE, 20);

        assertTrue(page.getContent().isEmpty());
        assertEquals(1, page.getTotalItems());
        assertEquals(1, page.getTotalPages());
        assertEquals(Integer.MAX_VALUE, page.getCurrentPage());
    }

    @Test
    @DisplayName("Invariante T3: Σ desgloses de los pagos + imputado a las previas == imputación total del cronograma")
    void obtenerPagos_sumaDesglosesMasPrevias_igualaImputacionTotal() {
        Deuda conTasa = deudaDelMotor(ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"), 12,
                new BigDecimal("0.015"), null);
        conTasa.setCuotasPagadasPrevias(1);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(conTasa));
        stubPagos(
                pago(1L, "-15000.00", 1_000L),
                pago(2L, "-20000.00", 2_000L));

        PageResponseDTO<PagoDeudaResponseDTO> page = deudaService.obtenerPagos(1L, 0, 20);

        BigDecimal capitalPagos = BigDecimal.ZERO;
        BigDecimal interesPagos = BigDecimal.ZERO;
        BigDecimal costosPagos = BigDecimal.ZERO;
        for (PagoDeudaResponseDTO p : page.getContent()) {
            capitalPagos = capitalPagos.add(p.getCapital());
            interesPagos = interesPagos.add(p.getInteres());
            costosPagos = costosPagos.add(p.getCostos());
        }

        List<CuotaAmortizacion> tabla = new MotorFinanciero().calcular(new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"), 12,
                new BigDecimal("0.015"), null, BigDecimal.ZERO, BigDecimal.ZERO)).tabla();
        List<ImputacionPagos.ImputacionCuota> total = ImputacionPagos.imputar(tabla, 1, new BigDecimal("35000"));

        BigDecimal capitalPrevias = BigDecimal.ZERO;
        BigDecimal interesPrevias = BigDecimal.ZERO;
        BigDecimal costosPrevias = BigDecimal.ZERO;
        for (ImputacionPagos.ImputacionCuota fila : total) {
            if (fila.previa()) {
                capitalPrevias = capitalPrevias.add(fila.capital());
                interesPrevias = interesPrevias.add(fila.interes());
                costosPrevias = costosPrevias.add(fila.costos());
            }
        }

        assertEquals(0, suma(total, ImputacionPagos.ImputacionCuota::capital)
                        .compareTo(capitalPagos.add(capitalPrevias)),
                "Σ capital desglosado + previas = capital imputado total");
        assertEquals(0, suma(total, ImputacionPagos.ImputacionCuota::interes)
                        .compareTo(interesPagos.add(interesPrevias)),
                "Σ interés desglosado + previas = interés imputado total");
        assertEquals(0, suma(total, ImputacionPagos.ImputacionCuota::costos)
                        .compareTo(costosPagos.add(costosPrevias)),
                "Σ costos desglosado + previas = costos imputados totales");
    }
}
