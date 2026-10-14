package com.example.saveup.service;

import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DeudaRepository deudaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private SmartSplitProcessor smartSplitProcessor;

    @Mock
    private SecurityUtils securityUtils;

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

        // Verificamos que se delegó la distribución al procesador de dominio
        verify(smartSplitProcessor, times(1)).procesarDistribucion(usuario, new java.math.BigDecimal("500000.00"));
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
    @DisplayName("T1d: ABONO_META se rechaza con 400 (se registra solo desde /api/metas)")
    void registrarMovimiento_abonoMeta_rechazado() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("50000.00"));
        dto.setDescripcion("Abono directo");
        dto.setTipoMovimiento(TipoMovimiento.ABONO_META);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> movimientoService.registrarMovimiento(dto));

        assertTrue(ex.getMessage().contains("/api/metas"),
                "El mensaje debe indicar dónde sí se registra este tipo");
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T1d: RETIRO_META se rechaza con 400 (se registra solo desde /api/metas)")
    void registrarMovimiento_retiroMeta_rechazado() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("50000.00"));
        dto.setDescripcion("Retiro directo");
        dto.setTipoMovimiento(TipoMovimiento.RETIRO_META);

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));

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
    @DisplayName("T1d: PAGO_DEUDA se normaliza a negativo y se compara contra el total de la deuda")
    void registrarMovimiento_pagoDeuda_seNormalizaNegativo() {
        MovimientoRegistroDTO dto = new MovimientoRegistroDTO();
        dto.setMonto(new java.math.BigDecimal("20000.00")); // positivo, el cliente no sabe la convención
        dto.setDescripcion("Cuota");
        dto.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        dto.setDeudaId(7L);

        com.example.saveup.model.Deuda deuda = new com.example.saveup.model.Deuda();
        deuda.setId(7L);
        deuda.setUsuario(usuario);
        deuda.setMontoTotal(new java.math.BigDecimal("100000.00"));

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(deudaRepository.findById(7L)).thenReturn(Optional.of(deuda));
        when(deudaRepository.findTotalPagadoPorDeuda(7L)).thenReturn(java.math.BigDecimal.ZERO);
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoResponseDTO response = movimientoService.registrarMovimiento(dto);

        assertEquals(0, new java.math.BigDecimal("-20000.00").compareTo(response.getMonto()));
        verify(securityUtils).validarPropietario("12345678-9");
    }
}
