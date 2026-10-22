package com.example.saveup.controller;

import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.SimulacionCreditoRequestDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.example.saveup.service.SimulacionService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeudaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SimulacionService simulacionService;

    @MockBean
    private DeudaRepository deudaRepository;

    @MockBean
    private MovimientoRepository movimientoRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    private static final String RUT = "12345678-5";

    private String validToken;
    private Usuario usuario;
    private final AtomicReference<Deuda> savedDeudaRef = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        validToken = jwtService.generateToken(RUT, "test@example.com");

        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Deudor Test");

        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> {
            Deuda d = inv.getArgument(0);
            d.setId(1L);
            savedDeudaRef.set(d);
            return d;
        });
        when(deudaRepository.findDeudaDTOById(anyLong())).thenAnswer(inv -> {
            Deuda d = savedDeudaRef.get();
            if (d == null) {
                return Optional.empty();
            }
            return Optional.of(new DeudaResponseDTO(
                    d.getId(), d.getNombre(), d.getDescripcion(), d.getMontoTotal(), d.getCantidadCuotas(),
                    d.getEstado(), d.getFechaCreacion(), BigDecimal.ZERO, 0L,
                    d.getTipoDeuda(), d.getModalidadCalculo(), d.getMontoCapital(), d.getTasaMensual(),
                    d.getValorCuota(), d.getGastosIniciales(), d.getCostoAdicionalPorCuota(),
                    d.getCostoTotalCredito(), d.getCargaAnualEquivalente(), d.getFechaPrimeraCuota(),
                    d.getCuotasPagadasPrevias(), d.getMontoPagadoPrevio()));
        });
    }

    private DeudaResponseDTO crearYObtenerDTO(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), DeudaResponseDTO.class);
    }

    // ──────────────────────── 1. Payload antiguo ────────────────────────

    @Test
    @DisplayName("POST /api/v1/deudas con payload antiguo → 201 SIN_INTERES, valorCuota = montoTotal/cuotas")
    void crearDeuda_payloadAntiguo_retorna201SinInteres() throws Exception {
        String json = """
                {"nombre":"Crédito Viejo","descripcion":"compatibilidad","montoTotal":100000,"cantidadCuotas":10}
                """;

        mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modalidadCalculo").value("SIN_INTERES"))
                .andExpect(jsonPath("$.valorCuota").value(10000.00))
                .andExpect(jsonPath("$.montoTotal").value(100000.00))
                .andExpect(jsonPath("$.tasaMensual").value(0.0));
    }

    // ──────────────────────── 2. Una deuda por modalidad vs simulador ────────────────────────

    @Test
    @DisplayName("Deuda SIN_INTERES coincide con el simulador para las mismas condiciones")
    void crearDeuda_sinInteres_coincideConSimulador() throws Exception {
        String json = """
                {"nombre":"Deuda SI","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """;

        DeudaResponseDTO dto = crearYObtenerDTO(json);

        SimulacionCreditoRequestDTO req = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(10)
                .build();
        var sim = simulacionService.simularCredito(req);

        assertEquals(sim.getValorCuota(), dto.getValorCuota());
        assertEquals(sim.getMontoTotal(), dto.getMontoTotal());
        assertEquals(sim.getCostoTotalCredito(), dto.getCostoTotalCredito());
        assertEquals(sim.getCondiciones().getTasaMensual(), dto.getTasaMensual());
        assertEquals(sim.getCargaAnualEquivalente(), dto.getCargaAnualEquivalente());
    }

    @Test
    @DisplayName("Deuda TASA_CONOCIDA coincide con el simulador para las mismas condiciones")
    void crearDeuda_tasaConocida_coincideConSimulador() throws Exception {
        String json = """
                {"nombre":"Deuda TC","condiciones":{"modalidad":"TASA_CONOCIDA","montoCapital":100000,"cantidadCuotas":12,"tasaMensual":1.5}}
                """;

        DeudaResponseDTO dto = crearYObtenerDTO(json);

        SimulacionCreditoRequestDTO req = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.5"))
                .build();
        var sim = simulacionService.simularCredito(req);

        assertEquals(sim.getValorCuota(), dto.getValorCuota());
        assertEquals(sim.getMontoTotal(), dto.getMontoTotal());
        assertEquals(sim.getCostoTotalCredito(), dto.getCostoTotalCredito());
        assertEquals(sim.getCondiciones().getTasaMensual(), dto.getTasaMensual());
        assertEquals(sim.getCargaAnualEquivalente(), dto.getCargaAnualEquivalente());
    }

    @Test
    @DisplayName("Deuda CUOTA_CONOCIDA coincide con el simulador para las mismas condiciones")
    void crearDeuda_cuotaConocida_coincideConSimulador() throws Exception {
        String json = """
                {"nombre":"Deuda CC","condiciones":{"modalidad":"CUOTA_CONOCIDA","montoCapital":100000,"cantidadCuotas":12,"valorCuota":8884.88}}
                """;

        DeudaResponseDTO dto = crearYObtenerDTO(json);

        SimulacionCreditoRequestDTO req = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.CUOTA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .valorCuota(new BigDecimal("8884.88"))
                .build();
        var sim = simulacionService.simularCredito(req);

        assertEquals(sim.getValorCuota(), dto.getValorCuota());
        assertEquals(sim.getMontoTotal(), dto.getMontoTotal());
        assertEquals(sim.getCostoTotalCredito(), dto.getCostoTotalCredito());
        assertEquals(sim.getCondiciones().getTasaMensual(), dto.getTasaMensual());
        assertEquals(sim.getCargaAnualEquivalente(), dto.getCargaAnualEquivalente());
    }

    // ──────────────────────── 3. Creación por fechas ────────────────────────

    @Test
    @DisplayName("Creación por fechas (7-oct-2026 a 8-nov-2027) → 14 cuotas y fechaPrimeraCuota persistida")
    void crearDeuda_porFechas_calcula14Cuotas() throws Exception {
        String json = """
                {"nombre":"Deuda Fechas","condiciones":{"modalidad":"SIN_INTERES","montoCapital":140000,"fechaPrimeraCuota":"2026-10-07","fechaUltimaCuota":"2027-11-08"}}
                """;

        mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadCuotas").value(14))
                .andExpect(jsonPath("$.fechaPrimeraCuota").value("2026-10-07"))
                .andExpect(jsonPath("$.fechaUltimaCuota").value("2027-11-07"));
    }

    // ──────────────────────── 4. cuotasPagadasPrevias = 1 ────────────────────────

    @Test
    @DisplayName("cuotasPagadasPrevias=1 en 100.000/10 cuotas sin interés → montoPagado 10.000, restante 90.000, cuotasPagadas 1, saldo sin cambio")
    void crearDeuda_cuotasPagadasPrevias1_noMueveSaldo() throws Exception {
        String json = """
                {"nombre":"Deuda Previas","cuotasPagadasPrevias":1,"condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """;

        DeudaResponseDTO dto = crearYObtenerDTO(json);

        assertEquals(0, new BigDecimal("10000.00").compareTo(dto.getMontoPagado()));
        assertEquals(0, new BigDecimal("90000.00").compareTo(dto.getMontoRestante()));
        assertEquals(1, dto.getCuotasPagadas());

        // El saldo del usuario NO cambia: no se crea ningún movimiento
        verify(movimientoRepository, never()).save(any());
    }

    // ──────────────────────── 5. cuotasPagadasPrevias ≥ cantidadCuotas → 400 ────────────────────────

    @Test
    @DisplayName("cuotasPagadasPrevias ≥ cantidadCuotas → 400 Bad Request con mensaje de error")
    void crearDeuda_cuotasPagadasPreviasMayorIgualACuotas_retorna400() throws Exception {
        String json = """
                {"nombre":"Deuda Inválida","cuotasPagadasPrevias":10,"condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """;

        mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    // ──────────────────────── 6. Editar con pagos → 409 ────────────────────────

    @Test
    @DisplayName("Editar una deuda que ya tiene pagos → 409 Conflict")
    void editarDeuda_conPagos_retorna409() throws Exception {
        Deuda deuda = new Deuda();
        deuda.setId(1L);
        deuda.setUsuario(usuario);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(deudaRepository.countPagosPorDeuda(1L)).thenReturn(1);

        String json = """
                {"nombre":"Deuda Edit","condiciones":{"modalidad":"SIN_INTERES","montoCapital":50000,"cantidadCuotas":5}}
                """;

        mockMvc.perform(put("/api/v1/deudas/1")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));
    }

    // ──────────────────────── 7. Amortización ajena → 403 ────────────────────────

    @Test
    @DisplayName("Amortización de una deuda ajena → 403 Forbidden")
    void obtenerAmortizacion_deudaAjena_retorna403() throws Exception {
        Usuario otro = new Usuario();
        otro.setRut("99999999-9");
        otro.setNombre("Otro");

        Deuda ajena = new Deuda();
        ajena.setId(1L);
        ajena.setUsuario(otro);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(ajena));

        mockMvc.perform(get("/api/v1/deudas/1/amortizacion")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.title").value("Prohibido"));
    }

    // ──────────────────────── 8. Amortización CUOTA_CONOCIDA → 200 ────────────────────────

    @Test
    @DisplayName("Amortización de una deuda CUOTA_CONOCIDA → 200 con tabla de 10 cuotas")
    void obtenerAmortizacion_cuotaConocida_retorna200() throws Exception {
        Deuda cc = new Deuda();
        cc.setId(1L);
        cc.setUsuario(usuario);
        cc.setModalidadCalculo(ModalidadCalculo.CUOTA_CONOCIDA);
        cc.setMontoCapital(new BigDecimal("100000"));
        cc.setCantidadCuotas(10);
        cc.setValorCuota(new BigDecimal("10000"));
        cc.setGastosIniciales(BigDecimal.ZERO);
        cc.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        cc.setTasaMensual(BigDecimal.ZERO);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(cc));

        mockMvc.perform(get("/api/v1/deudas/1/amortizacion")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[9].numero").value(10));
    }

    // ──────────────────────── 9. Detalle propio → 200 ────────────────────────

    @Test
    @DisplayName("GET /api/v1/deudas/{id} propio → 200 con la próxima cuota")
    void obtenerDeuda_propia_retorna200() throws Exception {
        crearYObtenerDTO("""
                {"nombre":"Deuda Detalle","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(savedDeudaRef.get()));

        mockMvc.perform(get("/api/v1/deudas/1")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.proximaCuotaNumero").value(1))
                .andExpect(jsonPath("$.proximaCuotaMontoPendiente").value(10000.00))
                .andExpect(jsonPath("$.totalPorPagar").value(100000.00));
    }

    // ──────────────────────── 10. Detalle ajeno → 403 (anti-IDOR) ────────────────────────

    @Test
    @DisplayName("GET /api/v1/deudas/{id} de otro usuario → 403 Forbidden")
    void obtenerDeuda_ajena_retorna403() throws Exception {
        Usuario otro = new Usuario();
        otro.setRut("99999999-9");
        otro.setNombre("Otro");

        Deuda ajena = new Deuda();
        ajena.setId(1L);
        ajena.setUsuario(otro);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(ajena));

        mockMvc.perform(get("/api/v1/deudas/1")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────── 11. Historial de pagos ────────────────────────

    @Test
    @DisplayName("GET /api/v1/deudas/{id}/pagos devuelve el historial con desglose, lo más reciente primero")
    void obtenerPagos_retornaHistorial() throws Exception {
        Deuda deudaPagos = new Deuda();
        deudaPagos.setId(1L);
        deudaPagos.setUsuario(usuario);
        deudaPagos.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        deudaPagos.setMontoCapital(new BigDecimal("100000"));
        deudaPagos.setCantidadCuotas(10);
        deudaPagos.setTasaMensual(BigDecimal.ZERO);
        deudaPagos.setValorCuota(new BigDecimal("10000"));
        deudaPagos.setGastosIniciales(BigDecimal.ZERO);
        deudaPagos.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        deudaPagos.setCuotasPagadasPrevias(0);
        deudaPagos.setMontoPagadoPrevio(BigDecimal.ZERO);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deudaPagos));

        Movimiento p1 = new Movimiento();
        p1.setId(1L);
        p1.setMonto(new BigDecimal("-10000.00"));
        p1.setFecha(new Date(1_000L));
        p1.setDescripcion("Cuota 1");
        p1.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);

        Movimiento p2 = new Movimiento();
        p2.setId(2L);
        p2.setMonto(new BigDecimal("-5000.00"));
        p2.setFecha(new Date(2_000L));
        p2.setDescripcion("Abono");
        p2.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);

        when(movimientoRepository.findByDeudaIdAndTipoMovimientoOrderByFechaAscIdAsc(1L, TipoMovimiento.PAGO_DEUDA))
                .thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/deudas/1/pagos")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].capital").value(5000.00))
                .andExpect(jsonPath("$.content[1].capital").value(10000.00));
    }

    @Test
    @DisplayName("GET /api/v1/deudas/{id}/pagos de otro usuario → 403 Forbidden (anti-IDOR)")
    void obtenerPagos_ajena_retorna403() throws Exception {
        Usuario otro = new Usuario();
        otro.setRut("99999999-9");
        otro.setNombre("Otro");

        Deuda ajena = new Deuda();
        ajena.setId(1L);
        ajena.setUsuario(otro);
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(ajena));

        mockMvc.perform(get("/api/v1/deudas/1/pagos")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
    }
}
