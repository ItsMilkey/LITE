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
}
