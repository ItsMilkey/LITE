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
        config.setPorcentajeAhorro(20.0); // 20% para ahorro
        config.setAutomatizarAhorroEnMetas(true);

        meta1 = new MetaAhorro();
        meta1.setId(10L);
        meta1.setNombre("Vacaciones");
        meta1.setMontoActual(50000.0);

        meta2 = new MetaAhorro();
        meta2.setId(20L);
        meta2.setNombre("Fondo Emergencia");
        meta2.setMontoActual(100000.0);

        catAhorro = new Categoria(1L, "Ahorro", "ic_savings", "#3F51B5", TipoPresupuesto.AHORRO);
    }

    @Test
    @DisplayName("Debe distribuir el porcentaje de ahorro entre las metas cuando automatizarAhorroEnMetas es true")
    void procesarDistribucion_conAutomatizacionActiva_distribuyeCorrectamente() {
        double montoIngreso = 1000000.0; // 1.000.000 -> 20% ahorro = 200.000

        AsignacionMetaPresupuesto asig1 = new AsignacionMetaPresupuesto();
        asig1.setMeta(meta1);
        asig1.setPorcentajeAsignacion(60.0); // 60% de 200.000 = 120.000

        AsignacionMetaPresupuesto asig2 = new AsignacionMetaPresupuesto();
        asig2.setMeta(meta2);
        asig2.setPorcentajeAsignacion(40.0); // 40% de 200.000 = 80.000

        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));
        when(asignacionMetaPresupuestoRepository.findByConfiguracionId(1L)).thenReturn(List.of(asig1, asig2));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.of(catAhorro));

        smartSplitProcessor.procesarDistribucion(usuario, montoIngreso);

        // Verificamos actualización de saldos en metas
        assertEquals(170000.0, meta1.getMontoActual()); // 50.000 + 120.000
        assertEquals(180000.0, meta2.getMontoActual()); // 100.000 + 80.000
        verify(metaAhorroRepository, times(1)).save(meta1);
        verify(metaAhorroRepository, times(1)).save(meta2);

        // Verificamos guardado de 2 movimientos de tipo ABONO_META con montos negativos
        ArgumentCaptor<Movimiento> captor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(2)).save(captor.capture());

        List<Movimiento> movimientos = captor.getAllValues();
        assertEquals(2, movimientos.size());

        Movimiento mov1 = movimientos.get(0);
        assertEquals(-120000.0, mov1.getMonto());
        assertEquals(TipoMovimiento.ABONO_META, mov1.getTipoMovimiento());
        assertEquals(catAhorro, mov1.getCategoria());
        assertEquals("Abono Auto: Vacaciones", mov1.getDescripcion());

        Movimiento mov2 = movimientos.get(1);
        assertEquals(-80000.0, mov2.getMonto());
        assertEquals(TipoMovimiento.ABONO_META, mov2.getTipoMovimiento());
        assertEquals(catAhorro, mov2.getCategoria());
        assertEquals("Abono Auto: Fondo Emergencia", mov2.getDescripcion());
    }

    @Test
    @DisplayName("Cuando automatizarAhorroEnMetas es false, no debe generar sub-movimientos ni modificar metas")
    void procesarDistribucion_conAutomatizacionDesactivada_mantieneLiquidezEnSaldoPrincipal() {
        config.setAutomatizarAhorroEnMetas(false);
        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));

        smartSplitProcessor.procesarDistribucion(usuario, 1000000.0);

        // No se debe consultar asignaciones, ni modificar metas ni guardar movimientos
        verifyNoInteractions(asignacionMetaPresupuestoRepository);
        verifyNoInteractions(metaAhorroRepository);
        verifyNoInteractions(movimientoRepository);
        // Los saldos de las metas permanecen inalterados
        assertEquals(50000.0, meta1.getMontoActual());
        assertEquals(100000.0, meta2.getMontoActual());
    }

    @Test
    @DisplayName("No debe hacer nada si la configuración presupuestaria está inactiva")
    void procesarDistribucion_conConfiguracionInactiva_noRealizaAccion() {
        config.setActivo(false);
        when(configuracionPresupuestoRepository.findByUsuarioRut("12345678-9")).thenReturn(Optional.of(config));

        smartSplitProcessor.procesarDistribucion(usuario, 500000.0);

        verifyNoInteractions(asignacionMetaPresupuestoRepository);
        verifyNoInteractions(metaAhorroRepository);
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("No debe hacer nada si el monto del ingreso es menor o igual a cero")
    void procesarDistribucion_conMontoInvalido_noRealizaAccion() {
        smartSplitProcessor.procesarDistribucion(usuario, 0.0);
        smartSplitProcessor.procesarDistribucion(usuario, -100.0);

        verifyNoInteractions(configuracionPresupuestoRepository);
    }
}
