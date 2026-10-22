package com.example.saveup.service;

import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.ModoPago;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private SmartSplitProcessor smartSplitProcessor;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private DeudaService deudaService;

    @InjectMocks
    private MovimientoService movimientoService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setRut("12345678-9");
        usuario.setNombre("Juan Pérez");
    }

    @Test
    @DisplayName("registrarMovimiento con ingreso y aplicarPresupuesto delega Smart-Split a SmartSplitProcessor")
    void registrarMovimiento_conSmartSplit_delegaAProcesador() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("500000.00"));
        dto.setDescripcion("Sueldo mensual");
        dto.setTipoMovimiento(TipoMovimiento.INGRESO_GENERAL);
        dto.setAplicarPresupuesto(true);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> {
            Movimiento m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        MovimientoResponseDTO response = movimientoService.registrarMovimiento(dto);

        assertNotNull(response);
        assertEquals(0, new java.math.BigDecimal("500000.00").compareTo(response.getMonto()));
        assertEquals("Sueldo mensual", response.getDescripcion());

        // Verificamos que se delegó la distribución al procesador de dominio, con
        // el ingreso ya persistido (T4: así el abono queda vinculado al origen).
        ArgumentCaptor<Movimiento> origenCaptor = ArgumentCaptor.forClass(Movimiento.class);
        verify(smartSplitProcessor, times(1)).procesarDistribucion(
                origenCaptor.capture(), eq(new java.math.BigDecimal("500000.00")));
        assertEquals(usuario, origenCaptor.getValue().getUsuario());
        verify(securityUtils).getAuthenticatedRut();
    }

    @Test
    @DisplayName("registrarMovimiento con gasto NO invoca SmartSplitProcessor")
    void registrarMovimiento_conGasto_noInvocaSmartSplit() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("-35000.00"));
        dto.setDescripcion("Compra supermercado");
        dto.setTipoMovimiento(TipoMovimiento.GASTO_GENERAL);
        dto.setAplicarPresupuesto(true); // Aunque venga true, es un gasto

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoResponseDTO response = movimientoService.registrarMovimiento(dto);

        assertNotNull(response);
        verifyNoInteractions(smartSplitProcessor);
    }

    @Test
    @DisplayName("obtenerSaldoActual retorna BigDecimal con centavos exactos")
    void obtenerSaldoActual_conCentavos_retornaBigDecimalEscalaDos() {
        when(usuarioRepository.existsById("12345678-9")).thenReturn(true);
        when(movimientoRepository.findSaldoByUsuarioRut("12345678-9"))
                .thenReturn(new java.math.BigDecimal("12345.67"));

        java.math.BigDecimal saldo = movimientoService.obtenerSaldoActual("12345678-9");

        assertNotNull(saldo);
        assertEquals(0, new java.math.BigDecimal("12345.67").compareTo(saldo));
        assertEquals(2, saldo.scale());
    }

    @Test
    @DisplayName("obtenerSaldoActual cuando no hay movimientos retorna BigDecimal.ZERO con escala 2")
    void obtenerSaldoActual_sinMovimientos_retornaCeroConEscalaDos() {
        when(usuarioRepository.existsById("12345678-9")).thenReturn(true);
        when(movimientoRepository.findSaldoByUsuarioRut("12345678-9")).thenReturn(null);

        java.math.BigDecimal saldo = movimientoService.obtenerSaldoActual("12345678-9");

        assertNotNull(saldo);
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(saldo));
        assertEquals(2, saldo.scale());
    }

    // ─────────────────── Signo canónico (T1d) ───────────────────

    @Test
    @DisplayName("T1d: un gasto enviado con signo POSITIVO se guarda negativo (el signo lo fija el tipo)")
    void registrarMovimiento_gastoConSignoPosativo_seGuardaNegativo() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("35000.00")); // el cliente manda positivo
        dto.setDescripcion("Compra supermercado");
        dto.setTipoMovimiento(TipoMovimiento.GASTO_GENERAL);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoResponseDTO response = movimientoService.registrarMovimiento(dto);

        assertEquals(0, new java.math.BigDecimal("-35000.00").compareTo(response.getMonto()),
                "El saldo debe bajar: el gasto se guarda negativo aunque el cliente lo envíe positivo");
    }

    @Test
    @DisplayName("T1d: un ingreso enviado con signo NEGATIVO se guarda positivo")
    void registrarMovimiento_ingresoConSignoNegativo_seGuardaPositivo() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("-500000.00"));
        dto.setDescripcion("Sueldo mensual");
        dto.setTipoMovimiento(TipoMovimiento.INGRESO_GENERAL);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoResponseDTO response = movimientoService.registrarMovimiento(dto);

        assertEquals(0, new java.math.BigDecimal("500000.00").compareTo(response.getMonto()));
    }

    @Test
    @DisplayName("T1d: escenario — ingreso 20.000 y dos gastos (±5.000) quedan en −5.000 y el saldo es 10.000")
    void escenarioSaldoCompuesto_ingresoYGastosConSignosMixtos() {
        List<Movimiento> guardados = new ArrayList<>();

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsById("12345678-9")).thenReturn(true);
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> {
            Movimiento m = inv.getArgument(0);
            m.setId((long) guardados.size() + 1);
            m.setFecha(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
            guardados.add(m);
            return m;
        });
        when(movimientoRepository.findSaldoByUsuarioRut("12345678-9")).thenAnswer(inv -> guardados.stream()
                .map(Movimiento::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));

        registrar(new java.math.BigDecimal("20000.00"), TipoMovimiento.INGRESO_GENERAL);
        registrar(new java.math.BigDecimal("5000.00"), TipoMovimiento.GASTO_GENERAL);  // enviado positivo
        registrar(new java.math.BigDecimal("-5000.00"), TipoMovimiento.GASTO_GENERAL); // enviado negativo

        assertEquals(0, new java.math.BigDecimal("-5000.00").compareTo(guardados.get(1).getMonto()),
                "El gasto enviado positivo debe guardarse negativo");
        assertEquals(0, new java.math.BigDecimal("-5000.00").compareTo(guardados.get(2).getMonto()),
                "El gasto enviado negativo debe seguir negativo");
        assertEquals(0, new java.math.BigDecimal("10000.00").compareTo(
                movimientoService.obtenerSaldoActual("12345678-9")),
                "20.000 − 5.000 − 5.000 = 10.000");
    }

    private void registrar(java.math.BigDecimal monto, TipoMovimiento tipo) {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(monto);
        dto.setDescripcion("Movimiento " + tipo);
        dto.setTipoMovimiento(tipo);
        movimientoService.registrarMovimiento(dto);
    }

    @Test
    @DisplayName("T1d: ABONO_META se rechaza con 400 (se registra solo desde /api/v1/metas)")
    void registrarMovimiento_abonoMeta_rechazado() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("50000.00"));
        dto.setDescripcion("Abono directo");
        dto.setTipoMovimiento(TipoMovimiento.ABONO_META);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> movimientoService.registrarMovimiento(dto));

        assertTrue(ex.getMessage().contains("sección de metas"),
                "El mensaje debe indicar dónde sí se registra este tipo");
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T1d: RETIRO_META se rechaza con 400 (se registra solo desde /api/v1/metas)")
    void registrarMovimiento_retiroMeta_rechazado() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("50000.00"));
        dto.setDescripcion("Retiro directo");
        dto.setTipoMovimiento(TipoMovimiento.RETIRO_META);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");

        assertThrows(IllegalArgumentException.class, () -> movimientoService.registrarMovimiento(dto));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T1d: monto 0 se rechaza (no puede existir un movimiento sin efecto en el saldo)")
    void registrarMovimiento_montoCero_rechazado() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(java.math.BigDecimal.ZERO);
        dto.setDescripcion("Movimiento vacío");
        dto.setTipoMovimiento(TipoMovimiento.GASTO_GENERAL);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));

        assertThrows(IllegalArgumentException.class, () -> movimientoService.registrarMovimiento(dto));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T2: PAGO_DEUDA delega en DeudaService.registrarPago (único productor) y devuelve el movimiento creado")
    void registrarMovimiento_pagoDeuda_delegaEnDeudaService() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("20000.00")); // positivo: el cliente no sabe la convención
        dto.setDescripcion("Cuota");
        dto.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        dto.setDeudaId(7L);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(deudaService.registrarPago(eq(7L), any(PagoDeudaDTO.class)))
                .thenReturn(new DeudaService.ResultadoPago(null, movimientoPago(-20000L)));

        MovimientoResponseDTO response = movimientoService.registrarMovimiento(dto);

        assertEquals(0, new java.math.BigDecimal("-20000.00").compareTo(response.getMonto()),
                "El movimiento lo crea DeudaService con el signo canónico");

        ArgumentCaptor<PagoDeudaDTO> captor = ArgumentCaptor.forClass(PagoDeudaDTO.class);
        verify(deudaService).registrarPago(eq(7L), captor.capture());
        assertEquals(0, new java.math.BigDecimal("20000.00").compareTo(captor.getValue().getMonto()),
                "El monto viaja en positivo: el signo lo impone el servicio de deudas");
        assertEquals(ModoPago.MONTO_PERSONALIZADO, captor.getValue().getModoPago());

        // El endpoint de movimientos ya no guarda el PAGO_DEUDA por su cuenta
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T2: un monto negativo desde /api/v1/movimientos se imputa igual que uno positivo")
    void registrarMovimiento_pagoDeudaConMontoNegativo_equivaleAPositivo() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("-20000.00"));
        dto.setDescripcion("Cuota");
        dto.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        dto.setDeudaId(7L);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(deudaService.registrarPago(eq(7L), any(PagoDeudaDTO.class)))
                .thenReturn(new DeudaService.ResultadoPago(null, movimientoPago(-20000L)));

        movimientoService.registrarMovimiento(dto);

        ArgumentCaptor<PagoDeudaDTO> captor = ArgumentCaptor.forClass(PagoDeudaDTO.class);
        verify(deudaService).registrarPago(eq(7L), captor.capture());
        assertEquals(0, new java.math.BigDecimal("20000.00").compareTo(captor.getValue().getMonto()),
                "abs(): las dos rutas de entrada producen el mismo pago");
    }

    @Test
    @DisplayName("T2: PAGO_DEUDA sin deudaId → 400 (no se puede delegar el pago)")
    void registrarMovimiento_pagoDeudaSinDeudaId_rechazado() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("10000.00"));
        dto.setDescripcion("Cuota sin deuda");
        dto.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");

        assertThrows(IllegalArgumentException.class, () -> movimientoService.registrarMovimiento(dto));
        verifyNoInteractions(movimientoRepository, deudaService);
    }

    private MovimientoResponseDTO movimientoPago(long monto) {
        Movimiento movimiento = new Movimiento();
        movimiento.setId(50L);
        movimiento.setMonto(java.math.BigDecimal.valueOf(monto).setScale(2));
        movimiento.setDescripcion("Cuota");
        movimiento.setFecha(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
        movimiento.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        return MovimientoResponseDTO.from(movimiento);
    }
}
