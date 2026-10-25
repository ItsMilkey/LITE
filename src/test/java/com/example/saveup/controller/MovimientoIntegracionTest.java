package com.example.saveup.controller;

import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de POST /api/v1/movimientos (sin BD, repositorios simulados):
 * qué tipos se aceptan y qué responde cada violación.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MovimientoIntegracionTest {

    private static final String RUT = "12345678-5";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private DeudaRepository deudaRepository;

    @MockBean
    private MovimientoRepository movimientoRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private CategoriaRepository categoriaRepository;

    private String token;
    private Usuario usuario;
    private Deuda deuda;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();

        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Usuario Test");

        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsById(RUT)).thenReturn(true);
        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(
                new Categoria(1L, "Deudas", "ic_payment", "#795548", TipoPresupuesto.NECESIDAD)));

        deuda = new Deuda();
        deuda.setId(1L);
        deuda.setUsuario(usuario);
        deuda.setNombre("Deuda");
        deuda.setMontoTotal(new BigDecimal("100000.00"));
        deuda.setCantidadCuotas(10);
        deuda.setMontoCapital(new BigDecimal("100000.00"));
        deuda.setEstado(EstadoDeuda.PENDIENTE);
        deuda.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        deuda.setTasaMensual(BigDecimal.ZERO);
        deuda.setValorCuota(new BigDecimal("10000.00"));
        deuda.setGastosIniciales(BigDecimal.ZERO);
        deuda.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        deuda.setCuotasPagadasPrevias(0);
        deuda.setMontoPagadoPrevio(BigDecimal.ZERO);

        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> {
            Movimiento m = inv.getArgument(0);
            m.setId(99L);
            m.setFecha(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
            return m;
        });
        when(deudaRepository.findTotalPagadoPorDeuda(anyLong())).thenReturn(BigDecimal.ZERO);
        when(deudaRepository.findDeudaDTOById(anyLong())).thenAnswer(inv -> Optional.of(new DeudaResponseDTO(
                1L, "Deuda", "Desc", new BigDecimal("100000.00"), 10, EstadoDeuda.PENDIENTE, Date.from(Instant.parse("2026-10-15T12:00:00Z")),
                new BigDecimal("-10000.00"), 1L, deuda.getTipoDeuda() != null ? deuda.getTipoDeuda()
                        : com.example.saveup.model.enums.TipoDeuda.OTRO,
                ModalidadCalculo.SIN_INTERES, new BigDecimal("100000.00"), BigDecimal.ZERO,
                new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"),
                BigDecimal.ZERO, null, 0, BigDecimal.ZERO)));
        when(deudaRepository.countPagosPorDeuda(anyLong())).thenReturn(0);
    }

    private org.springframework.test.web.servlet.ResultActions postMovimiento(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/movimientos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("ABONO_META y RETIRO_META siguen rechazándose con 400 (se registran solo desde la sección de metas)")
    void tiposDeMeta_rechazados400() throws Exception {
        postMovimiento("""
                {"monto":50000,"descripcion":"Abono directo","tipoMovimiento":"ABONO_META"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("sección de metas")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/api/v1/metas"))));

        postMovimiento("""
                {"monto":50000,"descripcion":"Retiro directo","tipoMovimiento":"RETIRO_META"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("sección de metas")));
    }

    @Test
    @DisplayName("PAGO_DEUDA sin deudaId → 400 (no se puede delegar el pago)")
    void pagoDeudaSinDeudaId_400() throws Exception {
        postMovimiento("""
                {"monto":10000,"descripcion":"Cuota","tipoMovimiento":"PAGO_DEUDA"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("Monto 0 → 400 (no puede existir un movimiento sin efecto en el saldo)")
    void montoCero_400() throws Exception {
        postMovimiento("""
                {"monto":0,"descripcion":"Movimiento vacío","tipoMovimiento":"GASTO_GENERAL"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("PAGO_DEUDA sin monto → 400: /api/v1/movimientos exige monto explícito (el modo CUOTA_CALCULADA es de /api/v1/deudas/{id}/pagar)")
    void cuotaCalculadaSinMonto_400() throws Exception {
        postMovimiento("""
                {"descripcion":"Cuota calculada","tipoMovimiento":"PAGO_DEUDA","deudaId":1,"modoPago":"CUOTA_CALCULADA"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MONTO_PERSONALIZADO (por defecto) sin monto → 400")
    void sinMontoPorDefecto_400() throws Exception {
        postMovimiento("""
                {"descripcion":"Cuota sin monto","tipoMovimiento":"PAGO_DEUDA","deudaId":1}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sobrepago por /api/v1/movimientos → 409 (mismo código que /api/v1/deudas/{id}/pagar)")
    void sobrepago_409() throws Exception {
        postMovimiento("""
                {"monto":150000,"descripcion":"Pago excesivo","tipoMovimiento":"PAGO_DEUDA","deudaId":1}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DEUDA_SOBREPAGO"));
    }

    @Test
    @DisplayName("Pago válido por /api/v1/movimientos → 201 con el movimiento negativo")
    void pagoValido_201() throws Exception {
        postMovimiento("""
                {"monto":10000,"descripcion":"Cuota 1","tipoMovimiento":"PAGO_DEUDA","deudaId":1}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(-10000.00))
                .andExpect(jsonPath("$.categoria.nombre").value("Deudas"));
    }

    @Test
    @DisplayName("Un movimiento sin tipo → 400")
    void sinTipo_400() throws Exception {
        postMovimiento("""
                {"monto":10000,"descripcion":"Sin tipo"}
                """)
                .andExpect(status().isBadRequest());
    }
}
