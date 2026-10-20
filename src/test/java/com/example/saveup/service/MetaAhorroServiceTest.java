package com.example.saveup.service;

import com.example.saveup.dto.AbonoRetiroDTO;
import com.example.saveup.dto.MetaAhorroCreacionDTO;
import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MetaAhorroRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaAhorroServiceTest {

    @Mock
    private MetaAhorroRepository metaAhorroRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MetaAhorroService metaAhorroService;

    private Usuario usuario;
    private MetaAhorro meta;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setRut("12345678-9");
        usuario.setNombre("Ahorrador Test");

        meta = new MetaAhorro();
        meta.setId(1L);
        meta.setNombre("Fondo de emergencia");
        meta.setMontoObjetivo(new java.math.BigDecimal("500000.00"));
        meta.setMontoActual(new java.math.BigDecimal("100000.00"));
        meta.setFechaLimite(LocalDate.of(2027, 11, 7));
        meta.setUsuario(usuario);
    }

    @Test
    @DisplayName("crearMeta asigna el usuario autenticado desde el contexto de seguridad")
    void crearMeta_asignaRutAutenticado() {
        MetaAhorroCreacionDTO dto = new MetaAhorroCreacionDTO();
        dto.setNombre("Fondo de emergencia");
        dto.setMontoObjetivo(new java.math.BigDecimal("500000.00"));
        dto.setFechaLimite(LocalDate.of(2027, 11, 7));

        when(securityUtils.getAuthenticatedRut()).thenReturn("12345678-9");
        when(usuarioRepository.findById("12345678-9")).thenReturn(Optional.of(usuario));
        when(metaAhorroRepository.save(any(MetaAhorro.class))).thenAnswer(inv -> {
            MetaAhorro m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        MetaAhorroResponseDTO response = metaAhorroService.crearMeta(dto);

        assertNotNull(response);
        assertEquals("Fondo de emergencia", response.getNombre());
        verify(securityUtils).getAuthenticatedRut();
        verify(metaAhorroRepository).save(any(MetaAhorro.class));
    }

    @Test
    @DisplayName("realizarAbono valida propiedad de la meta y registra movimiento negativo en saldo corriente")
    void realizarAbono_validaPropietarioYGuardaMovimiento() {
        AbonoRetiroDTO dto = new AbonoRetiroDTO();
        dto.setMonto(new java.math.BigDecimal("50000.00"));
        dto.setDescripcion("Abono manual");

        Categoria catAhorro = new Categoria(1L, "Ahorro", "ic_savings", "#3F51B5", TipoPresupuesto.AHORRO);

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.of(catAhorro));
        when(metaAhorroRepository.save(any(MetaAhorro.class))).thenAnswer(inv -> inv.getArgument(0));

        MetaAhorroResponseDTO response = metaAhorroService.realizarAbono(1L, dto);

        assertNotNull(response);
        assertEquals(0, new java.math.BigDecimal("150000.00").compareTo(meta.getMontoActual()));
        verify(securityUtils).validarPropietario(usuario.getRut());
        verify(movimientoRepository).save(any(Movimiento.class));
    }

    // ─────────────────── Signo canónico de movimientos de meta (T1d) ───────────────────

    @Test
    @DisplayName("T1d: el abono se guarda negativo aunque el DTO traiga el monto positivo")
    void realizarAbono_movimientoQuedaNegativo() {
        AbonoRetiroDTO dto = new AbonoRetiroDTO();
        dto.setMonto(new java.math.BigDecimal("50000.00"));
        dto.setDescripcion("Abono manual");

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.empty());
        when(metaAhorroRepository.save(any(MetaAhorro.class))).thenAnswer(inv -> inv.getArgument(0));

        metaAhorroService.realizarAbono(1L, dto);

        org.mockito.ArgumentCaptor<Movimiento> captor =
                org.mockito.ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());

        assertEquals(0, new java.math.BigDecimal("-50000.00").compareTo(captor.getValue().getMonto()),
                "ABONO_META sale del saldo principal: el movimiento debe ser negativo");
        assertEquals(TipoMovimiento.ABONO_META, captor.getValue().getTipoMovimiento());
    }

    @Test
    @DisplayName("T1d: el retiro se guarda positivo aunque el DTO traiga el monto negativo")
    void realizarRetiro_movimientoQuedaPositivo() {
        AbonoRetiroDTO dto = new AbonoRetiroDTO();
        dto.setMonto(new java.math.BigDecimal("-20000.00")); // el cliente manda negativo
        dto.setDescripcion("Retiro");

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(metaAhorroRepository.findTotalAhorradoByMetaId(1L)).thenReturn(new java.math.BigDecimal("100000.00"));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.empty());
        when(metaAhorroRepository.save(any(MetaAhorro.class))).thenAnswer(inv -> inv.getArgument(0));

        metaAhorroService.realizarRetiro(1L, dto);

        org.mockito.ArgumentCaptor<Movimiento> captor =
                org.mockito.ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());

        assertEquals(0, new java.math.BigDecimal("20000.00").compareTo(captor.getValue().getMonto()),
                "RETIRO_META vuelve al saldo principal: el movimiento debe ser positivo");
        assertEquals(TipoMovimiento.RETIRO_META, captor.getValue().getTipoMovimiento());
    }

    @Test
    @DisplayName("T1d: abono con monto 0 → error y no se guarda nada")
    void realizarAbono_montoCero_error() {
        AbonoRetiroDTO dto = new AbonoRetiroDTO();
        dto.setMonto(java.math.BigDecimal.ZERO);
        dto.setDescripcion("Abono vacío");

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));

        assertThrows(IllegalArgumentException.class, () -> metaAhorroService.realizarAbono(1L, dto));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T1d: al eliminar una meta, el reintegro (RETIRO_META) es igual al total ahorrado y deja el saldo como antes de los abonos")
    void eliminarMeta_reintegroDevuelveElTotalAhorrado() {
        // La meta tiene 100.000 ahorrados, producto de abonos que restaron del saldo.
        java.math.BigDecimal totalAhorrado = new java.math.BigDecimal("100000.00");

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(metaAhorroRepository.findTotalAhorradoByMetaId(1L))
                .thenReturn(totalAhorrado.negate()); // los abonos están guardados en negativo
        when(movimientoRepository.findByMetaAhorroId(1L)).thenReturn(List.of());

        metaAhorroService.eliminarMeta(1L);

        org.mockito.ArgumentCaptor<Movimiento> captor =
                org.mockito.ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());

        Movimiento reintegro = captor.getValue();
        assertEquals(TipoMovimiento.RETIRO_META, reintegro.getTipoMovimiento());
        assertEquals(0, totalAhorrado.compareTo(reintegro.getMonto()),
                "El reintegro debe ser POSITIVO e igual al total ahorrado");

        // Invariante de saldo: los abonos restaron 100.000 y el reintegro devuelve 100.000,
        // así que el saldo del usuario queda igual que antes de haber abonado.
        java.math.BigDecimal saldoConAbonos = reintegro.getMonto().multiply(new java.math.BigDecimal("-1"));
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(saldoConAbonos.add(reintegro.getMonto())),
                "saldo tras abonos + reintegro = 0: el saldo vuelve a su valor original");

        verify(metaAhorroRepository).delete(meta);
    }

    @Test
    @DisplayName("T1d: eliminar una meta sin ahorros no crea movimiento de reintegro")
    void eliminarMeta_sinAhorros_noCreaReintegro() {
        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(metaAhorroRepository.findTotalAhorradoByMetaId(1L)).thenReturn(null);
        when(movimientoRepository.findByMetaAhorroId(1L)).thenReturn(List.of());

        metaAhorroService.eliminarMeta(1L);

        verify(movimientoRepository, never()).save(any(Movimiento.class));
        verify(metaAhorroRepository).delete(meta);
    }

    @Test
    @DisplayName("T1d: la meta por defecto 'Ahorros' no se puede eliminar")
    void eliminarMeta_metaPorDefecto_error() {
        meta.setNombre("Ahorros");

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));

        ErrorNegocio ex = assertThrows(ErrorNegocio.class, () -> metaAhorroService.eliminarMeta(1L));
        assertEquals(CodigoError.CONFLICTO_REGLA_NEGOCIO, ex.getCodigo());
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("T1d: invariante — montoActual de la meta = −(suma de sus movimientos)")
    void invariante_montoActual_esNegativoDeLaSumaDeMovimientos() {
        // La meta arranca en 0 para poder comprobar la invariante tal cual la define T1d
        meta.setMontoActual(java.math.BigDecimal.ZERO);

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(categoriaRepository.findByNombre("Ahorro")).thenReturn(Optional.empty());
        when(metaAhorroRepository.save(any(MetaAhorro.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        AbonoRetiroDTO abono = new AbonoRetiroDTO();
        abono.setMonto(new java.math.BigDecimal("-50000.00")); // el cliente manda negativo
        abono.setDescripcion("Abono");
        metaAhorroService.realizarAbono(1L, abono);

        when(metaAhorroRepository.findTotalAhorradoByMetaId(1L))
                .thenReturn(new java.math.BigDecimal("-50000.00")); // el abono quedó negativo
        AbonoRetiroDTO retiro = new AbonoRetiroDTO();
        retiro.setMonto(new java.math.BigDecimal("-20000.00")); // el cliente manda negativo
        retiro.setDescripcion("Retiro");
        metaAhorroService.realizarRetiro(1L, retiro);

        org.mockito.ArgumentCaptor<Movimiento> captor =
                org.mockito.ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(2)).save(captor.capture());

        java.math.BigDecimal sumaMovimientos = captor.getAllValues().stream()
                .map(Movimiento::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        assertEquals(0, sumaMovimientos.negate().compareTo(meta.getMontoActual()),
                "montoActual debe ser el negativo de la suma de los movimientos de la meta");
        assertEquals(0, new java.math.BigDecimal("30000.00").compareTo(meta.getMontoActual()),
                "50000 abonados − 20000 retirados = 30000, con las dos entradas en negativo");
    }

    @Test
    @DisplayName("T1d: un retiro mayor al total ahorrado sigue rechazándose aunque el monto venga negativo")
    void realizarRetiro_mayorAlAhorrado_error() {
        meta.setMontoActual(new java.math.BigDecimal("100000.00"));

        AbonoRetiroDTO dto = new AbonoRetiroDTO();
        dto.setMonto(new java.math.BigDecimal("-150000.00")); // negativo, pero mayor al ahorrado
        dto.setDescripcion("Retiro excesivo");

        when(metaAhorroRepository.findById(1L)).thenReturn(Optional.of(meta));
        when(metaAhorroRepository.findTotalAhorradoByMetaId(1L))
                .thenReturn(new java.math.BigDecimal("100000.00"));

        ErrorNegocio ex = assertThrows(ErrorNegocio.class,
                () -> metaAhorroService.realizarRetiro(1L, dto));
        assertEquals(CodigoError.CONFLICTO_REGLA_NEGOCIO, ex.getCodigo());
        verifyNoInteractions(movimientoRepository);
    }
}
