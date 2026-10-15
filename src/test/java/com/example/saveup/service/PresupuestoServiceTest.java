package com.example.saveup.service;

import com.example.saveup.dto.ConfiguracionPresupuestoRequestDTO;
import com.example.saveup.dto.EjecucionCategoriaDTO;
import com.example.saveup.dto.EjecucionPresupuestoDTO;
import com.example.saveup.dto.HistorialPresupuestoDTO;
import com.example.saveup.dto.PresupuestoCategoriaItemDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PresupuestoCategoria;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoEjecucion;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PresupuestoCategoriaRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T5 — Reglas de negocio del presupuesto: validación de límites, ejecución por
 * categoría, historial (rango y mes sin ingresos) y suma de porcentajes.
 * Unitario con Mockito: sin Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class PresupuestoServiceTest {

    private static final String RUT = "12345678-9";

    @Mock
    private PresupuestoCategoriaRepository presupuestoCategoriaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;

    @Mock
    private MetaAhorroRepository metaAhorroRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private PresupuestoService presupuestoService;

    private Usuario usuario;
    private Categoria comida;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setRut(RUT);
        comida = new Categoria(1L, "Comida", "ic_fastfood", "#FFC107", TipoPresupuesto.NECESIDAD);
    }

    // ───────────────────────── Límites por categoría ─────────────────────────

    @Test
    @DisplayName("PUT con límite ≤ 0 → IllegalArgumentException (400)")
    void reemplazarCategorias_limiteNoPositivo_400() {
        PresupuestoCategoriaItemDTO item = new PresupuestoCategoriaItemDTO(1L, BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> presupuestoService.reemplazarCategorias(RUT, List.of(item)));

        verify(presupuestoCategoriaRepository, never()).deleteByUsuarioRut(any());
    }

    @Test
    @DisplayName("PUT con categoría inexistente → IllegalArgumentException (400)")
    void reemplazarCategorias_categoriaInexistente_400() {
        PresupuestoCategoriaItemDTO item = new PresupuestoCategoriaItemDTO(99L, new BigDecimal("100.00"));
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> presupuestoService.reemplazarCategorias(RUT, List.of(item)));

        verify(presupuestoCategoriaRepository, never()).deleteByUsuarioRut(any());
    }

    @Test
    @DisplayName("PUT válido reemplaza el conjunto: borra los actuales y guarda los nuevos")
    void reemplazarCategorias_valido_reemplaza() {
        PresupuestoCategoriaItemDTO item = new PresupuestoCategoriaItemDTO(1L, new BigDecimal("150.00"));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(comida));
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));

        var resultado = presupuestoService.reemplazarCategorias(RUT, List.of(item));

        assertEquals(1, resultado.size());
        assertEquals(new BigDecimal("150.00"), resultado.get(0).getLimiteMensual());
        verify(presupuestoCategoriaRepository).deleteByUsuarioRut(RUT);
        verify(presupuestoCategoriaRepository).saveAll(any());
    }

    // ───────────────────────── Suma de porcentajes ─────────────────────────

    @Test
    @DisplayName("Suma 50 + 30 + 20 = 100.00 → válida")
    void porcentajes_suman100_ok() {
        presupuestoService.validarSumaPorcentajes(request("50.00", "30.00", "20.00"));
    }

    @Test
    @DisplayName("Suma con distinta escala 33.33 + 33.33 + 33.34 = 100.00 → válida (compareTo)")
    void porcentajes_distintaEscala_suman100_ok() {
        presupuestoService.validarSumaPorcentajes(request("33.33", "33.33", "33.34"));
    }

    @Test
    @DisplayName("Suma ≠ 100 → IllegalArgumentException (400)")
    void porcentajes_noSuman100_400() {
        assertThrows(IllegalArgumentException.class,
                () -> presupuestoService.validarSumaPorcentajes(request("50.00", "30.00", "21.00")));
    }

    @Test
    @DisplayName("Algún porcentaje negativo → IllegalArgumentException (400)")
    void porcentajes_negativos_400() {
        // 120 + 10 - 30 = 100, suma válida pero el ahorro es negativo.
        assertThrows(IllegalArgumentException.class,
                () -> presupuestoService.validarSumaPorcentajes(request("120.00", "10.00", "-30.00")));
    }

    // ───────────────────────── Ejecución por categoría ─────────────────────────

    @Test
    @DisplayName("gastado suma abs(GASTO_GENERAL + PAGO_DEUDA) de la categoría; 80% → ALERTA")
    void ejecucionCategorias_sumaGastoYPagoDeuda() {
        PresupuestoCategoria limite = new PresupuestoCategoria(1L, usuario, comida, new BigDecimal("100.00"));
        Categoria otro = new Categoria(2L, "Ocio", "ic_fun", "#00FF00", TipoPresupuesto.DESEO);

        when(presupuestoCategoriaRepository.findByUsuarioRut(RUT)).thenReturn(List.of(limite));
        when(movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(eq(RUT), any(), any()))
                .thenReturn(List.of(
                        movimiento(TipoMovimiento.GASTO_GENERAL, "-50.00", comida),
                        movimiento(TipoMovimiento.PAGO_DEUDA, "-30.00", comida),
                        movimiento(TipoMovimiento.GASTO_GENERAL, "-99.00", otro),
                        movimiento(TipoMovimiento.INGRESO_GENERAL, "500.00", comida)));

        List<EjecucionCategoriaDTO> resultado = presupuestoService.obtenerEjecucionCategorias(RUT, 5, 2026);

        assertEquals(1, resultado.size());
        assertEquals(new BigDecimal("80.00"), resultado.get(0).getGastado());
        assertEquals(new BigDecimal("80.00"), resultado.get(0).getPorcentajeUsado());
        assertEquals(EstadoEjecucion.ALERTA, resultado.get(0).getEstado());
    }

    @Test
    @DisplayName("Frontera 79.99% → OK y 80.00% → ALERTA en la ejecución por categoría")
    void ejecucionCategorias_frontera79_99_ok_y80_alerta() {
        Categoria ocio = new Categoria(2L, "Ocio", "ic_fun", "#00FF00", TipoPresupuesto.DESEO);
        PresupuestoCategoria limiteA = new PresupuestoCategoria(1L, usuario, comida, new BigDecimal("100.00"));
        PresupuestoCategoria limiteB = new PresupuestoCategoria(2L, usuario, ocio, new BigDecimal("100.00"));

        when(presupuestoCategoriaRepository.findByUsuarioRut(RUT)).thenReturn(List.of(limiteA, limiteB));
        when(movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(eq(RUT), any(), any()))
                .thenReturn(List.of(
                        movimiento(TipoMovimiento.GASTO_GENERAL, "-79.99", comida),
                        movimiento(TipoMovimiento.GASTO_GENERAL, "-80.00", ocio)));

        List<EjecucionCategoriaDTO> resultado = presupuestoService.obtenerEjecucionCategorias(RUT, 5, 2026);

        assertEquals(new BigDecimal("79.99"), resultado.get(0).getPorcentajeUsado());
        assertEquals(EstadoEjecucion.OK, resultado.get(0).getEstado());
        assertEquals(new BigDecimal("80.00"), resultado.get(1).getPorcentajeUsado());
        assertEquals(EstadoEjecucion.ALERTA, resultado.get(1).getEstado());
    }

    @Test
    @DisplayName("Límite 0 o nulo: gastado se calcula pero porcentajeUsado queda en 0.00 sin división por cero")
    void ejecucionCategorias_limiteCeroONulo_noDividePorCero() {
        Categoria ocio = new Categoria(2L, "Ocio", "ic_fun", "#00FF00", TipoPresupuesto.DESEO);
        PresupuestoCategoria limiteCero = new PresupuestoCategoria(1L, usuario, comida, BigDecimal.ZERO);
        PresupuestoCategoria limiteNulo = new PresupuestoCategoria(2L, usuario, ocio, null);

        when(presupuestoCategoriaRepository.findByUsuarioRut(RUT)).thenReturn(List.of(limiteCero, limiteNulo));
        when(movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(eq(RUT), any(), any()))
                .thenReturn(List.of(movimiento(TipoMovimiento.GASTO_GENERAL, "-50.00", comida)));

        List<EjecucionCategoriaDTO> resultado = presupuestoService.obtenerEjecucionCategorias(RUT, 5, 2026);

        assertEquals(2, resultado.size());
        // El gasto sí se agrega aunque no haya límite...
        assertEquals(new BigDecimal("50.00"), resultado.get(0).getGastado());
        // ...pero el porcentaje se protege en 0.00 (sin ArithmeticException) y el estado es OK.
        assertEquals(0, resultado.get(0).getPorcentajeUsado().compareTo(new BigDecimal("0.00")));
        assertEquals(EstadoEjecucion.OK, resultado.get(0).getEstado());
        assertEquals(0, resultado.get(1).getPorcentajeUsado().compareTo(new BigDecimal("0.00")));
        assertEquals(EstadoEjecucion.OK, resultado.get(1).getEstado());
    }

    // ───────────────────────────── Historial ─────────────────────────────

    @Test
    @DisplayName("Historial con más de 24 meses → IllegalArgumentException (400)")
    void historial_masDe24Meses_400() {
        assertThrows(IllegalArgumentException.class,
                () -> presupuestoService.obtenerHistorial(RUT, "2024-01", "2026-02"));
    }

    @Test
    @DisplayName("Historial con formato inválido → IllegalArgumentException (400)")
    void historial_formatoInvalido_400() {
        assertThrows(IllegalArgumentException.class,
                () -> presupuestoService.obtenerHistorial(RUT, "2024-13", "2025-01"));
    }

    @Test
    @DisplayName("Un mes sin ingresos devuelve ceros sin dividir por 0")
    void historial_mesSinIngresos_ceros() {
        when(configuracionPresupuestoRepository.findByUsuarioRut(RUT)).thenReturn(Optional.empty());
        when(movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(eq(RUT), any(), any()))
                .thenReturn(List.of());

        List<HistorialPresupuestoDTO> historial = presupuestoService.obtenerHistorial(RUT, "2026-03", "2026-03");

        assertEquals(1, historial.size());
        EjecucionPresupuestoDTO ejecucion = historial.get(0).getEjecucion();
        assertEquals(0, ejecucion.getTotalIngresos().compareTo(BigDecimal.ZERO));
        assertEquals(0, ejecucion.getGastoNecesidades().compareTo(BigDecimal.ZERO));
        assertEquals(0, ejecucion.getPresupuestoNecesidades().compareTo(new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("Historial de exactamente 24 meses → 24 entradas, en orden ascendente")
    void historial_24Meses_ok() {
        when(configuracionPresupuestoRepository.findByUsuarioRut(RUT)).thenReturn(Optional.empty());
        when(movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(eq(RUT), any(), any()))
                .thenReturn(List.of());

        List<HistorialPresupuestoDTO> historial = presupuestoService.obtenerHistorial(RUT, "2024-01", "2025-12");

        assertEquals(24, historial.size());
        assertEquals(2024, historial.get(0).getAnio());
        assertEquals(1, historial.get(0).getMes());
        assertEquals(2025, historial.get(23).getAnio());
        assertEquals(12, historial.get(23).getMes());
        assertTrue(historial.get(0).getEjecucion() != null);
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private ConfiguracionPresupuestoRequestDTO request(String nec, String des, String aho) {
        ConfiguracionPresupuestoRequestDTO dto = new ConfiguracionPresupuestoRequestDTO();
        dto.setPorcentajeNecesidades(new BigDecimal(nec));
        dto.setPorcentajeDeseos(new BigDecimal(des));
        dto.setPorcentajeAhorro(new BigDecimal(aho));
        return dto;
    }

    private Movimiento movimiento(TipoMovimiento tipo, String monto, Categoria categoria) {
        Movimiento m = new Movimiento();
        m.setTipoMovimiento(tipo);
        m.setMonto(new BigDecimal(monto));
        m.setCategoria(categoria);
        m.setDescripcion("test");
        return m;
    }
}
