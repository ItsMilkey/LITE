package com.example.saveup.service;

import com.example.saveup.dto.AbonoRetiroDTO;
import com.example.saveup.dto.MetaAhorroCreacionDTO;
import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
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

import java.util.Date;
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
        meta.setFechaLimite(new Date());
        meta.setUsuario(usuario);
    }

    @Test
    @DisplayName("crearMeta asigna el usuario autenticado desde el contexto de seguridad")
    void crearMeta_asignaRutAutenticado() {
        MetaAhorroCreacionDTO dto = new MetaAhorroCreacionDTO();
        dto.setNombre("Fondo de emergencia");
        dto.setMontoObjetivo(new java.math.BigDecimal("500000.00"));
        dto.setFechaLimite(new Date());

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
}
