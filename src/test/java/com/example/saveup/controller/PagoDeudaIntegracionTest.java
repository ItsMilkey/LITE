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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pagos de deuda end-to-end sobre HTTP con repositorios simulados (sin BD).
 * Cubre las dos rutas de entrada de PAGO_DEUDA, los 409/403 y el escenario del usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PagoDeudaIntegracionTest {

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

    private final List<Deuda> deudas = new ArrayList<>();
    private final List<Movimiento> movimientos = new ArrayList<>();
    private final AtomicLong siguienteDeudaId = new AtomicLong(1L);
    private final AtomicLong siguienteMovimientoId = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();

        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Deudor Test");

        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        when(categoriaRepository.findByNombre("Deudas")).thenReturn(Optional.of(
                new Categoria(1L, "Deudas", "ic_payment", "#795548", TipoPresupuesto.NECESIDAD)));

        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> {
            Deuda d = inv.getArgument(0);
            if (d.getId() == null) {
                aplicarPrePersist(d);
                d.setId(siguienteDeudaId.getAndIncrement());
                deudas.add(d);
            }
            return d;
        });

        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(inv -> {
            Movimiento m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(siguienteMovimientoId.getAndIncrement());
                m.setFecha(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
                movimientos.add(m);
            }
            return m;
        });

        when(deudaRepository.findById(anyLong())).thenAnswer(inv -> deudas.stream()
                .filter(d -> d.getId().equals(inv.getArgument(0)))
                .findFirst());

        when(deudaRepository.findDeudaDTOById(anyLong())).thenAnswer(inv -> deudas.stream()
                .filter(d -> d.getId().equals(inv.getArgument(0)))
                .map(this::proyectar)
                .findFirst());

        when(deudaRepository.findDeudasDTOByUsuarioRut(RUT)).thenAnswer(inv -> deudas.stream()
                .map(this::proyectar)
                .toList());

        when(deudaRepository.findTotalPagadoPorDeuda(anyLong())).thenAnswer(inv -> {
            BigDecimal total = movimientos.stream()
                    .filter(m -> esPagoDe((Long) inv.getArgument(0), m))
                    .map(Movimiento::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return total.signum() == 0 ? BigDecimal.ZERO : total;
        });

        when(deudaRepository.countPagosPorDeuda(anyLong())).thenAnswer(inv ->
                (int) movimientos.stream().filter(m -> esPagoDe((Long) inv.getArgument(0), m)).count());

        when(movimientoRepository.findSaldoByUsuarioRut(anyString())).thenAnswer(inv -> movimientos.stream()
                .map(Movimiento::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ──────────────────────── helpers ────────────────────────

    /** Simula el @PrePersist que no corre sin JPA: estado y fecha de creación. */
    private static void aplicarPrePersist(Deuda d) {
        if (d.getEstado() == null) {
            d.setEstado(EstadoDeuda.PENDIENTE);
        }
        if (d.getFechaCreacion() == null) {
            d.setFechaCreacion(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
        }
    }

    private boolean esPagoDe(Long deudaId, Movimiento m) {
        return m.getTipoMovimiento() == TipoMovimiento.PAGO_DEUDA
                && m.getDeuda() != null
                && deudaId.equals(m.getDeuda().getId());
    }

    /** Simula la proyección JPQL: suma y conteo de los PAGO_DEUDA de la deuda. */
    private DeudaResponseDTO proyectar(Deuda d) {
        BigDecimal pagos = movimientos.stream()
                .filter(m -> esPagoDe(d.getId(), m))
                .map(Movimiento::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantidad = movimientos.stream().filter(m -> esPagoDe(d.getId(), m)).count();

        return new DeudaResponseDTO(d.getId(), d.getNombre(), d.getDescripcion(), d.getMontoTotal(),
                d.getCantidadCuotas(), d.getEstado(), d.getFechaCreacion(), pagos, cantidad,
                d.getTipoDeuda(), d.getModalidadCalculo(), d.getMontoCapital(), d.getTasaMensual(),
                d.getValorCuota(), d.getGastosIniciales(), d.getCostoAdicionalPorCuota(),
                d.getCostoTotalCredito(), d.getCargaAnualEquivalente(), d.getFechaPrimeraCuota(),
                d.getCuotasPagadasPrevias(), d.getMontoPagadoPrevio());
    }

    private Long crearDeuda(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), DeudaResponseDTO.class).getId();
    }

    private DeudaResponseDTO deudaDe(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/deudas/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<DeudaResponseDTO> lista = objectMapper.readValue(result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeudaResponseDTO.class));
        return lista.stream().filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
    }

    private BigDecimal saldo() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/saldos/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("saldo").decimalValue();
    }

    // ──────────────────────── tests ────────────────────────

    @Test
    @DisplayName("Ambas rutas de entrada (POST /api/v1/deudas/{id}/pagar y POST /api/v1/movimientos) producen el mismo pago")
    void ambasRutasProducenElMismoPago() throws Exception {
        Long deudaA = crearDeuda("""
                {"nombre":"Deuda A","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);
        Long deudaB = crearDeuda("""
                {"nombre":"Deuda B","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);

        // Ruta 1: endpoint de pagos de la deuda
        mockMvc.perform(post("/api/v1/deudas/" + deudaA + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":10000,"descripcion":"Cuota 1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotasPagadas").value(1))
                .andExpect(jsonPath("$.totalPorPagar").value(90000.00))
                .andExpect(jsonPath("$.montoPagado").value(10000.00));

        // Ruta 2: POST /api/v1/movimientos con tipo PAGO_DEUDA
        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":10000,"descripcion":"Cuota 1","tipoMovimiento":"PAGO_DEUDA","deudaId":%d}
                                """.formatted(deudaB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoMovimiento").value("PAGO_DEUDA"))
                .andExpect(jsonPath("$.monto").value(-10000.00));

        assertEquals(2, movimientos.size(), "Un solo movimiento por ruta");
        Movimiento pagoA = movimientos.stream().filter(m -> m.getDeuda().getId().equals(deudaA)).findFirst().orElseThrow();
        Movimiento pagoB = movimientos.stream().filter(m -> m.getDeuda().getId().equals(deudaB)).findFirst().orElseThrow();
        assertEquals(0, pagoA.getMonto().compareTo(pagoB.getMonto()), "Mismo monto (negativo) por las dos rutas");
        assertEquals(pagoA.getDescripcion(), pagoB.getDescripcion());
        assertEquals(pagoA.getTipoMovimiento(), pagoB.getTipoMovimiento());

        // Las dos deudas quedan igual: 1 cuota pagada y 90.000 por pagar
        for (Long id : List.of(deudaA, deudaB)) {
            DeudaResponseDTO deuda = deudaDe(id);
            assertEquals(1, deuda.getCuotasPagadas());
            assertEquals(0, new BigDecimal("90000.00").compareTo(deuda.getTotalPorPagar()));
        }
    }

    @Test
    @DisplayName("Un monto negativo desde /api/v1/movimientos equivale a enviarlo positivo")
    void pagoConMontoNegativoPorMovimientos() throws Exception {
        Long deuda = crearDeuda("""
                {"nombre":"Deuda","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);

        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":-10000,"descripcion":"Cuota","tipoMovimiento":"PAGO_DEUDA","deudaId":%d}
                                """.formatted(deuda)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(-10000.00));

        assertEquals(0, new BigDecimal("90000.00").compareTo(deudaDe(deuda).getTotalPorPagar()));
        assertEquals(1, deudaDe(deuda).getCuotasPagadas());
    }

    @Test
    @DisplayName("CUOTA_CALCULADA cobra la cuota de la próxima impaga, sin enviar monto")
    void pagoCuotaCalculada() throws Exception {
        Long deuda = crearDeuda("""
                {"nombre":"Deuda","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);

        mockMvc.perform(post("/api/v1/deudas/" + deuda + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cuota calculada","modoPago":"CUOTA_CALCULADA"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotasPagadas").value(1))
                .andExpect(jsonPath("$.totalPorPagar").value(90000.00));

        assertEquals(0, new BigDecimal("-10000.00").compareTo(movimientos.getFirst().getMonto()));
    }

    @Test
    @DisplayName("Escenario del usuario: saldo 20.000, deuda 100.000/10 sin interés, pago CUOTA_CALCULADA")
    void escenarioUsuario_saldoCuotasYTotalPorPagar() throws Exception {
        Movimiento ingreso = new Movimiento();
        ingreso.setId(siguienteMovimientoId.getAndIncrement());
        ingreso.setUsuario(usuario);
        ingreso.setMonto(new BigDecimal("20000.00"));
        ingreso.setDescripcion("Sueldo");
        ingreso.setFecha(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
        ingreso.setTipoMovimiento(TipoMovimiento.INGRESO_GENERAL);
        movimientos.add(ingreso);

        assertEquals(0, new BigDecimal("20000.00").compareTo(saldo()));

        Long deuda = crearDeuda("""
                {"nombre":"Deuda","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);

        mockMvc.perform(post("/api/v1/deudas/" + deuda + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cuota 1","modoPago":"CUOTA_CALCULADA"}
                                """))
                .andExpect(status().isOk());

        DeudaResponseDTO deudaPagada = deudaDe(deuda);
        assertEquals(1, deudaPagada.getCuotasPagadas());
        assertEquals(0, new BigDecimal("90000.00").compareTo(deudaPagada.getTotalPorPagar()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(saldo()), "El pago baja el saldo de 20.000 a 10.000");
    }

    @Test
    @DisplayName("Sobrepago → 409")
    void sobrepagoDevuelve409() throws Exception {
        Long deuda = crearDeuda("""
                {"nombre":"Deuda","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);

        mockMvc.perform(post("/api/v1/deudas/" + deuda + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":150000,"descripcion":"Pago excesivo"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("DEUDA_SOBREPAGO"));
    }

    @Test
    @DisplayName("Cubrir el total marca la deuda PAGADA y un nuevo pago → 409")
    void deudaPagadaNoAceptaMasPagos() throws Exception {
        Long deuda = crearDeuda("""
                {"nombre":"Deuda","condiciones":{"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":10}}
                """);

        mockMvc.perform(post("/api/v1/deudas/" + deuda + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":100000,"descripcion":"Liquidación"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));

        mockMvc.perform(post("/api/v1/deudas/" + deuda + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":10000,"descripcion":"Cuota extra"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));
    }

    @Test
    @DisplayName("Pago a una deuda de otro usuario → 403")
    void deudaAjenaDevuelve403() {
        Usuario otro = new Usuario();
        otro.setRut("99999999-9");
        otro.setNombre("Otro");

        Deuda ajena = new Deuda();
        ajena.setId(500L);
        ajena.setUsuario(otro);
        ajena.setNombre("Deuda ajena");
        ajena.setMontoTotal(new BigDecimal("50000.00"));
        ajena.setCantidadCuotas(5);
        ajena.setMontoCapital(new BigDecimal("50000.00"));
        ajena.setEstado(EstadoDeuda.PENDIENTE);
        ajena.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        ajena.setGastosIniciales(BigDecimal.ZERO);
        ajena.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        ajena.setTasaMensual(BigDecimal.ZERO);
        ajena.setValorCuota(new BigDecimal("10000.00"));
        ajena.setCuotasPagadasPrevias(0);
        ajena.setMontoPagadoPrevio(BigDecimal.ZERO);
        deudas.add(ajena);

        try {
            mockMvc.perform(post("/api/v1/deudas/500/pagar")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"monto":10000,"descripcion":"Cuota ajena"}
                                    """))
                    .andExpect(status().isForbidden());
        } catch (Exception e) {
            fail("La petición no debió fallar: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("El listado enriquece también una deuda CUOTA_CONOCIDA")
    void listadoConDeudaCuotaConocida() throws Exception {
        Long deuda = crearDeuda("""
                {"nombre":"Deuda CC","condiciones":{"modalidad":"CUOTA_CONOCIDA","montoCapital":100000,"cantidadCuotas":10,"valorCuota":10000}}
                """);

        mockMvc.perform(get("/api/v1/deudas/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalPorPagar").value(100000.00))
                .andExpect(jsonPath("$[0].saldoCapital").value(100000.00))
                .andExpect(jsonPath("$[0].valorCuota").value(10000.00))
                .andExpect(jsonPath("$[0].cuotasPagadas").value(0))
                .andExpect(jsonPath("$[0].tasaMensualFraccion").doesNotExist())
                .andExpect(jsonPath("$[0].tasaMensual").exists());

        assertNotNull(deudaDe(deuda));
    }
}
