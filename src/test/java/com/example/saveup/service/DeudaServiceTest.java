package com.example.saveup.service;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
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
}
