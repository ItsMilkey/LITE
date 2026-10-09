package com.example.saveup.service;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.finanzas.CalendarioCuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeudaServiceTest {

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

    @InjectMocks
    private DeudaService deudaService;

    private Usuario usuario;
    private Deuda deuda;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setRut("11223344-5");
        usuario.setNombre("Deudor Test");

        deuda = new Deuda();
        deuda.setId(1L);
        deuda.setNombre("Crédito Universitario");
        deuda.setMontoTotal(new java.math.BigDecimal("100000.00"));
        deuda.setCantidadCuotas(10);
        deuda.setEstado(EstadoDeuda.PENDIENTE);
        deuda.setUsuario(usuario);
        deuda.setFechaCreacion(new Date());
    }

    @Test
    @DisplayName("crearDeuda debe inferir automáticamente el RUT del SecurityContext")
    void crearDeuda_asignaRutDesdeContextoSeguro() {
        DeudaCreacionDTO dto = new DeudaCreacionDTO();
        dto.setNombre("Crédito Universitario");
        dto.setDescripcion("Cuotas");
        dto.setMontoTotal(new java.math.BigDecimal("100000.00"));
        dto.setCantidadCuotas(10);

        when(securityUtils.getAuthenticatedRut()).thenReturn("11223344-5");
        when(usuarioRepository.findById("11223344-5")).thenReturn(Optional.of(usuario));
        when(deudaRepository.save(any(Deuda.class))).thenReturn(deuda);

        DeudaResponseDTO response = new DeudaResponseDTO(1L, "Crédito Universitario", "Cuotas", new java.math.BigDecimal("100000.00"), 10,
                EstadoDeuda.PENDIENTE, new Date(), java.math.BigDecimal.ZERO, 0L);
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(response));

        DeudaResponseDTO result = deudaService.crearDeuda(dto);

        assertNotNull(result);
        assertEquals("Crédito Universitario", result.getNombre());
        verify(securityUtils).getAuthenticatedRut();
        verify(deudaRepository).save(any(Deuda.class));
    }

    @Test
    @DisplayName("obtenerDeudasPorUsuario debe utilizar la proyección JPQL optimizada (1 sola query, sin N+1)")
    void obtenerDeudasPorUsuario_ejecutaConsultaOptimizadaSinNPlusOne() {
        when(usuarioRepository.existsById("11223344-5")).thenReturn(true);

        DeudaResponseDTO dto1 = new DeudaResponseDTO(1L, "Tarjeta Crédito", "Mastercard", new java.math.BigDecimal("50000.00"), 5,
                EstadoDeuda.PENDIENTE, new Date(), new java.math.BigDecimal("-20000.00"), 2L);
        DeudaResponseDTO dto2 = new DeudaResponseDTO(2L, "Préstamo Auto", "Cuotas banco", new java.math.BigDecimal("200000.00"), 24,
                EstadoDeuda.PENDIENTE, new Date(), new java.math.BigDecimal("-50000.00"), 6L);

        when(deudaRepository.findDeudasDTOByUsuarioRut("11223344-5")).thenReturn(List.of(dto1, dto2));

        List<DeudaResponseDTO> result = deudaService.obtenerDeudasPorUsuario("11223344-5");

        assertEquals(2, result.size());
        assertEquals(0, new java.math.BigDecimal("20000.00").compareTo(result.get(0).getMontoPagado()));
        assertEquals(0, new java.math.BigDecimal("30000.00").compareTo(result.get(0).getMontoRestante()));
        assertEquals(2, result.get(0).getCuotasPagadas());

        // Verificamos que se llamó a la proyección directa y NO a consultas individuales por cada deuda
        verify(deudaRepository, times(1)).findDeudasDTOByUsuarioRut("11223344-5");
        verify(deudaRepository, never()).findTotalPagadoPorDeuda(any());
        verify(deudaRepository, never()).countPagosPorDeuda(any());
    }

    @Test
    @DisplayName("DeudaResponseDTO calcula correctamente montoPagado, montoRestante y cuotasPagadas")
    void deudaResponseDTO_calculaCamposAgregados() {
        DeudaResponseDTO dto = new DeudaResponseDTO(
                5L, "Crédito", "Desc", new java.math.BigDecimal("100000.00"), 10, EstadoDeuda.PENDIENTE, new Date(), new java.math.BigDecimal("-40000.00"), 4L
        );

        assertEquals(0, new java.math.BigDecimal("40000.00").compareTo(dto.getMontoPagado()));
        assertEquals(0, new java.math.BigDecimal("60000.00").compareTo(dto.getMontoRestante()));
        assertEquals(4, dto.getCuotasPagadas());
    }

    @Test
    @DisplayName("registrarPago actualiza la deuda a PAGADA si cubre el total")
    void registrarPago_saldoTotal_marcaDeudaPagada() {
        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new java.math.BigDecimal("100000.00"));
        pagoDTO.setDescripcion("Pago total");

        Categoria catDeudas = new Categoria(1L, "Deudas", "ic_payment", "#795548", TipoPresupuesto.NECESIDAD);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(catDeudas));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(new java.math.BigDecimal("-100000.00"));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> inv.getArgument(0));

        DeudaResponseDTO dtoEsperado = new DeudaResponseDTO(1L, "Crédito Universitario", "Cuotas", new java.math.BigDecimal("100000.00"), 10,
                EstadoDeuda.PAGADA, new Date(), new java.math.BigDecimal("-100000.00"), 1L);
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(dtoEsperado));

        DeudaResponseDTO result = deudaService.registrarPago(1L, pagoDTO);

        assertEquals(EstadoDeuda.PAGADA, deuda.getEstado());
        verify(movimientoRepository).save(any(Movimiento.class));
        verify(securityUtils).validarPropietario(usuario.getRut());
    }

    @Test
    @DisplayName("DeudaResponseDTO y registrarPago manejan centavos con precisión")
    void registrarPago_conCentavos_calculaMontoRestanteExacto() {
        deuda.setMontoTotal(new java.math.BigDecimal("1250.75"));

        PagoDeudaDTO pagoDTO = new PagoDeudaDTO();
        pagoDTO.setMonto(new java.math.BigDecimal("450.25"));
        pagoDTO.setDescripcion("Pago parcial con centavos");

        Categoria catDeudas = new Categoria(1L, "Deudas", "ic_payment", "#795548", TipoPresupuesto.NECESIDAD);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(catDeudas));
        when(deudaRepository.findTotalPagadoPorDeuda(1L)).thenReturn(new java.math.BigDecimal("-450.25"));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> inv.getArgument(0));

        DeudaResponseDTO dtoEsperado = new DeudaResponseDTO(1L, "Crédito Universitario", "Cuotas", new java.math.BigDecimal("1250.75"), 10,
                EstadoDeuda.PENDIENTE, new Date(), new java.math.BigDecimal("-450.25"), 1L);
        when(deudaRepository.findDeudaDTOById(1L)).thenReturn(Optional.of(dtoEsperado));

        DeudaResponseDTO result = deudaService.registrarPago(1L, pagoDTO);

        assertNotNull(result);
        assertEquals(0, new java.math.BigDecimal("450.25").compareTo(result.getMontoPagado()));
        assertEquals(0, new java.math.BigDecimal("800.50").compareTo(result.getMontoRestante()));
        assertEquals(EstadoDeuda.PENDIENTE, deuda.getEstado());
    }

    // ──────────────────────── Bug 1: fechaUltimaCuota ────────────────────────

    @Test
    @DisplayName("DeudaResponseDTO calcula fechaUltimaCuota usando la misma lógica que CalendarioCuotas (fin de mes)")
    void deudaResponseDTO_fechaUltimaCuota_usaLogicaCalendarioCuotas() {
        CalendarioCuotas calendario = new CalendarioCuotas();

        // Caso 1: 31-ene-2027 con 2 cuotas → última = 28-feb-2027 (no existe 31-feb)
        LocalDate primera1 = LocalDate.of(2027, 1, 31);
        DeudaResponseDTO dto1 = new DeudaResponseDTO(1L, "Test", "Desc", new BigDecimal("100000"), 2,
                EstadoDeuda.PENDIENTE, new Date(), BigDecimal.ZERO, 0L,
                TipoDeuda.OTRO, ModalidadCalculo.SIN_INTERES, new BigDecimal("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, primera1, 0, BigDecimal.ZERO);

        LocalDate esperada1 = calendario.fechasVencimiento(primera1, 2).get(1);
        assertEquals(esperada1, dto1.getFechaUltimaCuota(),
                "fechaUltimaCuota debe coincidir con CalendarioCuotas para fin de mes");

        // Caso 2: 31-ene-2027 con 3 cuotas → última = 31-mar-2027
        LocalDate primera2 = LocalDate.of(2027, 1, 31);
        DeudaResponseDTO dto2 = new DeudaResponseDTO(2L, "Test", "Desc", new BigDecimal("100000"), 3,
                EstadoDeuda.PENDIENTE, new Date(), BigDecimal.ZERO, 0L,
                TipoDeuda.OTRO, ModalidadCalculo.SIN_INTERES, new BigDecimal("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, primera2, 0, BigDecimal.ZERO);

        LocalDate esperada2 = calendario.fechasVencimiento(primera2, 3).get(2);
        assertEquals(esperada2, dto2.getFechaUltimaCuota(),
                "fechaUltimaCuota debe coincidir con CalendarioCuotas");

        // Caso 3: 31-ene-2027 con 1 cuota → última = 31-ene-2027
        LocalDate primera3 = LocalDate.of(2027, 1, 31);
        DeudaResponseDTO dto3 = new DeudaResponseDTO(3L, "Test", "Desc", new BigDecimal("100000"), 1,
                EstadoDeuda.PENDIENTE, new Date(), BigDecimal.ZERO, 0L,
                TipoDeuda.OTRO, ModalidadCalculo.SIN_INTERES, new BigDecimal("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, primera3, 0, BigDecimal.ZERO);

        LocalDate esperada3 = calendario.fechasVencimiento(primera3, 1).get(0);
        assertEquals(esperada3, dto3.getFechaUltimaCuota(),
                "fechaUltimaCuota debe coincidir con CalendarioCuotas para 1 cuota");
    }

    // ──────────────────────── Bug 2: tasaMensual naming ────────────────────────

    @Test
    @DisplayName("DeudaResponseDTO expone tasaMensual como porcentaje con nombre tasaMensualPorcentaje")
    void deudaResponseDTO_tasaMensualPorcentaje_existeYEsPorcentaje() throws NoSuchFieldException {
        // Verificar que el campo tasaMensualPorcentaje existe
        Field field = DeudaResponseDTO.class.getDeclaredField("tasaMensualPorcentaje");
        assertNotNull(field, "DeudaResponseDTO debe tener el campo tasaMensualPorcentaje");

        // Verificar que el campo tasaMensual NO existe (para evitar ambigüedad)
        try {
            DeudaResponseDTO.class.getDeclaredField("tasaMensual");
            fail("DeudaResponseDTO NO debe tener el campo tasaMensual (ambiguo con la fracción del motor)");
        } catch (NoSuchFieldException e) {
            // Esperado: el campo tasaMensual no debe existir
        }

        // Verificar que el valor es un porcentaje (1.5, no 0.015)
        DeudaResponseDTO dto = new DeudaResponseDTO(1L, "Test", "Desc", new BigDecimal("100000"), 12,
                EstadoDeuda.PENDIENTE, new Date(), BigDecimal.ZERO, 0L,
                TipoDeuda.OTRO, ModalidadCalculo.TASA_CONOCIDA, new BigDecimal("100000"),
                new BigDecimal("0.015"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.of(2027, 1, 31), 0, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("1.5000").compareTo(dto.getTasaMensualPorcentaje()),
                "tasaMensualPorcentaje debe ser 1.5 (porcentaje), no 0.015 (fracción)");
    }
}
