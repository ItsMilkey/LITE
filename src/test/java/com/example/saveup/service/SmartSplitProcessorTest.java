package com.example.saveup.service;

import com.example.saveup.model.*;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartSplitProcessorTest {

    @Mock
    private ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;

    @Mock
    private AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;

    @Mock
    private MetaAhorroRepository metaAhorroRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private SmartSplitProcessor smartSplitProcessor;

    private Usuario usuario;
    private ConfiguracionPresupuesto config;
    private MetaAhorro meta1;
    private MetaAhorro meta2;
    private Categoria catAhorro;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setRut("12345678-9");
        usuario.setNombre("Test User");

        config = new ConfiguracionPresupuesto();
        config.setId(1L);
        config.setUsuario(usuario);
        config.setActivo(true);
        config.setPorcentajeAhorro(new java.math.BigDecimal("20.00")); // 20% para ahorro
        config.setAutomatizarAhorroEnMetas(true);

        meta1 = new MetaAhorro();
        meta1.setId(10L);
        meta1.setNombre("Vacaciones");
        meta1.setMontoActual(new java.math.BigDecimal("50000.00"));

        meta2 = new MetaAhorro();
        meta2.setId(20L);
        meta2.setNombre("Fondo Emergencia");
        meta2.setMontoActual(new java.math.BigDecimal("100000.00"));

        catAhorro = new Categoria(1L, "Ahorro", "ic_savings", "#3F51B5", TipoPresupuesto.AHORRO);
    }

    @Test
    @DisplayName("Debe distribuir el porcentaje de ahorro entre las metas cuando automatizarAhorroEnMetas es true")
    void procesarDistribucion_conAutomatizacionActiva_distribuyeCorrectamente() {
        java.math.BigDecimal montoIngreso = new java.math.BigDecimal("1000000.00"); // 1.000.000 -> 20% ahorro = 200.000

        AsignacionMetaPresupuesto asig1 = new AsignacionMetaPresupuesto();
        asig1.setMeta(meta1);
        asig1.setPorcentajeAsignacion(new java.math.BigDecimal("60.00")); // 60% de 200.000 = 120.000

        AsignacionMetaPresupuesto asig2 = new AsignacionMetaPresupuesto();
        asig2.setMeta(meta2);
        asig2.setPorcentajeAsignacion(new java.math.BigDecimal("40.00")); // 40% de 200.000 = 80.000

        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));
        when(asignacionMetaPresupuestoRepository.findByConfiguracionId(1L)).thenReturn(List.of(asig1, asig2));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.of(catAhorro));

        smartSplitProcessor.procesarDistribucion(usuario, montoIngreso);

        // Verificamos actualización de saldos en metas
        assertEquals(0, new java.math.BigDecimal("170000.00").compareTo(meta1.getMontoActual())); // 50.000 + 120.000
        assertEquals(0, new java.math.BigDecimal("180000.00").compareTo(meta2.getMontoActual())); // 100.000 + 80.000
        verify(metaAhorroRepository, times(1)).save(meta1);
        verify(metaAhorroRepository, times(1)).save(meta2);

        // Verificamos guardado de 2 movimientos de tipo ABONO_META con montos negativos
        ArgumentCaptor<Movimiento> captor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(2)).save(captor.capture());

        List<Movimiento> movimientos = captor.getAllValues();
        assertEquals(2, movimientos.size());

        Movimiento mov1 = movimientos.get(0);
        assertEquals(0, new java.math.BigDecimal("-120000.00").compareTo(mov1.getMonto()));
        assertEquals(TipoMovimiento.ABONO_META, mov1.getTipoMovimiento());
        assertEquals(catAhorro, mov1.getCategoria());
        assertEquals("Abono Auto: Vacaciones", mov1.getDescripcion());

        Movimiento mov2 = movimientos.get(1);
        assertEquals(0, new java.math.BigDecimal("-80000.00").compareTo(mov2.getMonto()));
        assertEquals(TipoMovimiento.ABONO_META, mov2.getTipoMovimiento());
        assertEquals(catAhorro, mov2.getCategoria());
        assertEquals("Abono Auto: Fondo Emergencia", mov2.getDescripcion());
    }

    @Test
    @DisplayName("Cuando automatizarAhorroEnMetas es false, no debe generar sub-movimientos ni modificar metas")
    void procesarDistribucion_conAutomatizacionDesactivada_mantieneLiquidezEnSaldoPrincipal() {
        config.setAutomatizarAhorroEnMetas(false);
        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));

        smartSplitProcessor.procesarDistribucion(usuario, new java.math.BigDecimal("1000000.00"));

        // No se debe consultar asignaciones, ni modificar metas ni guardar movimientos
        verifyNoInteractions(asignacionMetaPresupuestoRepository);
        verifyNoInteractions(metaAhorroRepository);
        verifyNoInteractions(movimientoRepository);
        // Los saldos de las metas permanecen inalterados
        assertEquals(0, new java.math.BigDecimal("50000.00").compareTo(meta1.getMontoActual()));
        assertEquals(0, new java.math.BigDecimal("100000.00").compareTo(meta2.getMontoActual()));
    }

    @Test
    @DisplayName("Smart-Split con 3 asignaciones (33.33 / 33.33 / 33.34) reparte exactamente el ahorro y asigna el residuo a la última")
    void procesarDistribucion_conTresAsignaciones_reparteExactamenteConResiduoALaUltima() {
        // Ingreso 1000.00 -> 10% ahorro = 100.00
        config.setPorcentajeAhorro(new java.math.BigDecimal("10.00"));
        java.math.BigDecimal montoIngreso = new java.math.BigDecimal("1000.00");

        MetaAhorro meta3 = new MetaAhorro();
        meta3.setId(30L);
        meta3.setNombre("Educación");
        meta3.setMontoActual(java.math.BigDecimal.ZERO);

        AsignacionMetaPresupuesto asig1 = new AsignacionMetaPresupuesto();
        asig1.setMeta(meta1);
        asig1.setPorcentajeAsignacion(new java.math.BigDecimal("33.33")); // 33.33% de 100 = 33.33

        AsignacionMetaPresupuesto asig2 = new AsignacionMetaPresupuesto();
        asig2.setMeta(meta2);
        asig2.setPorcentajeAsignacion(new java.math.BigDecimal("33.33")); // 33.33% de 100 = 33.33

        AsignacionMetaPresupuesto asig3 = new AsignacionMetaPresupuesto();
        asig3.setMeta(meta3);
        asig3.setPorcentajeAsignacion(new java.math.BigDecimal("33.34")); // Última asignación: 100.00 - 66.66 = 33.34

        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));
        when(asignacionMetaPresupuestoRepository.findByConfiguracionId(1L)).thenReturn(List.of(asig1, asig2, asig3));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.of(catAhorro));

        smartSplitProcessor.procesarDistribucion(usuario, montoIngreso);

        // Verificamos que los movimientos suman exactamente 100.00
        ArgumentCaptor<Movimiento> captor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(3)).save(captor.capture());

        List<Movimiento> movimientos = captor.getAllValues();
        assertEquals(3, movimientos.size());

        java.math.BigDecimal mov1Monto = movimientos.get(0).getMonto();
        java.math.BigDecimal mov2Monto = movimientos.get(1).getMonto();
        java.math.BigDecimal mov3Monto = movimientos.get(2).getMonto();

        assertEquals(0, new java.math.BigDecimal("-33.33").compareTo(mov1Monto));
        assertEquals(0, new java.math.BigDecimal("-33.33").compareTo(mov2Monto));
        assertEquals(0, new java.math.BigDecimal("-33.34").compareTo(mov3Monto));

        java.math.BigDecimal totalAbonado = mov1Monto.add(mov2Monto).add(mov3Monto).abs();
        assertEquals(0, new java.math.BigDecimal("100.00").compareTo(totalAbonado));
    }

    @Test
    @DisplayName("No debe hacer nada si la configuración presupuestaria está inactiva")
    void procesarDistribucion_conConfiguracionInactiva_noRealizaAccion() {
        config.setActivo(false);
        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));

        smartSplitProcessor.procesarDistribucion(usuario, new java.math.BigDecimal("500000.00"));

        verifyNoInteractions(asignacionMetaPresupuestoRepository);
        verifyNoInteractions(metaAhorroRepository);
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("No debe hacer nada si el monto del ingreso es menor o igual a cero")
    void procesarDistribucion_conMontoInvalido_noRealizaAccion() {
        smartSplitProcessor.procesarDistribucion(usuario, java.math.BigDecimal.ZERO);
        smartSplitProcessor.procesarDistribucion(usuario, new java.math.BigDecimal("-100.00"));

        verifyNoInteractions(configuracionPresupuestoRepository);
    }
}
