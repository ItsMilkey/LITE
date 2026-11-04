package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.PlanMensualRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10d (sesión 2) — integración real (H2, MockMvc y repositorios) de las
 * partidas vinculadas a una deuda: monto y categoría derivados (D36), errores
 * de vínculo (D37–D40), vínculo desactualizado (D42), unicidad por mes (D44) y
 * avisos al copiar (D45). Cada test corre en una transacción con rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeudaVinculadaIntegracionTest {

    private static final String RUT = "11111111-1";
    private static final String RUT_AJENO = "22222222-2";
    private static final YearMonth MES = YearMonth.of(2026, 11);
    private static final YearMonth OTRO_MES = YearMonth.of(2026, 10);
    private static final LocalDate PRIMERA_CUOTA = LocalDate.of(2026, 11, 5);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private DeudaRepository deudaRepository;
    @Autowired
    private PlanMensualRepository planMensualRepository;
    @Autowired
    private PartidaPlanRepository partidaPlanRepository;

    private String token;
    private String tokenAjeno;
    private Usuario usuario;
    private Usuario ajeno;
    private Categoria deudas;
    private Categoria alimentacion;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        tokenAjeno = jwtService.generarTokenConExpiracion(RUT_AJENO, "ajeno@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "Ana", "Pérez"));
        ajeno = usuarioRepository.save(nuevoUsuario(RUT_AJENO, "Beto", "Soto"));
        deudas = categoria("DEUDAS", "Pago de Deudas", TipoPresupuesto.NECESIDAD);
        alimentacion = categoria("ALIMENTACION_BASE", "Alimentación Base", TipoPresupuesto.NECESIDAD);
    }

    // ─────────────────────────── D36: monto y categoría ───────────────────────────

    @Test
    @DisplayName("D36: cuota 43.000 + costo adicional 500 da monto 43.500 y categoría DEUDAS")
    void d36_montoDerivadoDeLaCuota() throws Exception {
        Deuda deuda = deudaRepository.save(deuda(usuario, "430000.00", 10, PRIMERA_CUOTA, "500.00"));

        MvcResult resultado = postPartidaDeuda(MES, "Cuota crédito", deuda.getId(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidas.length()").value(1))
                .andExpect(jsonPath("$.partidas[0].montoPlanificado").value(43500.00))
                .andExpect(jsonPath("$.partidas[0].categoriaId").value(deudas.getId()))
                .andExpect(jsonPath("$.partidas[0].categoriaCodigo").value("DEUDAS"))
                .andExpect(jsonPath("$.partidas[0].deudaId").value(deuda.getId()))
                .andExpect(jsonPath("$.partidas[0].estadoCuota").value("PENDIENTE"))
                .andExpect(jsonPath("$.partidas[0].vinculoDesactualizado").value(false))
                .andExpect(jsonPath("$.avisos.length()").value(0))
                .andReturn();

        JsonNode partida = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("partidas").get(0);
        assertEquals(0, new BigDecimal("43500.00").compareTo(
                partidaPlanRepository.findById(partida.path("id").asLong())
                        .orElseThrow().getMontoPlanificado()));
    }

    // ─────────────────────────── D37: deuda no vinculable ───────────────────────────

    @Test
    @DisplayName("D37: deuda sin fecha de cuotas, sin cuota en el mes o cancelada dan 409")
    void d37_deudaNoVinculable() throws Exception {
        Deuda sinFechas = deudaRepository.save(deuda(usuario, "430000.00", 10, null, "0.00"));
        Deuda sinCuotaEnElMes = deudaRepository.save(
                deuda(usuario, "43000.00", 1, LocalDate.of(2026, 9, 5), "0.00"));
        Deuda cancelada = deudaRepository.save(deuda(usuario, "430000.00", 10, PRIMERA_CUOTA, "0.00"));
        cancelada.setEstado(EstadoDeuda.CANCELADA);
        deudaRepository.save(cancelada);

        postPartidaDeuda(MES, "Sin fechas", sinFechas.getId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString("fechas de cuotas")));
        postPartidaDeuda(MES, "Sin cuota", sinCuotaEnElMes.getId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));
        postPartidaDeuda(MES, "Cancelada", cancelada.getId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));
    }

    // ─────────────────────────── D38: propiedad ───────────────────────────

    @Test
    @DisplayName("D38: una deuda ajena da 403 y una inexistente da 404")
    void d38_deudaAjenaOInexistente() throws Exception {
        Deuda ajena = deudaRepository.save(deuda(ajeno, "430000.00", 10, PRIMERA_CUOTA, "0.00"));

        postPartidaDeuda(MES, "Ajena", ajena.getId(), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        postPartidaDeuda(MES, "Fantasma", 999_999L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // ─────────────────────────── D39: cuerpo inválido ───────────────────────────

    @Test
    @DisplayName("D39: enviar montoPlanificado o una categoría distinta con deudaId da 400")
    void d39_cuerpoInvalido() throws Exception {
        Deuda deuda = deudaRepository.save(deuda(usuario, "430000.00", 10, PRIMERA_CUOTA, "0.00"));

        postPartidaDeuda(MES, "Con monto", deuda.getId(), ",\"montoPlanificado\":43500.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        postPartidaDeuda(MES, "Con categoría", deuda.getId(),
                ",\"categoriaId\":" + alimentacion.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // La categoría DEUDAS explícita sí es válida.
        postPartidaDeuda(MES, "Con DEUDAS", deuda.getId(),
                ",\"categoriaId\":" + deudas.getId())
                .andExpect(status().isCreated());
    }

    // ─────────────────────────── D40: PATCH de monto ───────────────────────────

    @Test
    @DisplayName("D40: el PATCH de monto sobre una partida vinculada da 409 y no aplica nada")
    void d40_patchMontoVinculado() throws Exception {
        Deuda deuda = deudaRepository.save(deuda(usuario, "430000.00", 10, PRIMERA_CUOTA, "0.00"));
        long vinculada = crearPartidaDeuda(MES, "Cuota crédito", deuda.getId());
        long normal = crearPartida(MES, "Supermercado", alimentacion, "250000.00");

        patchMontos(MES, "[{\"partidaId\":" + vinculada + ",\"monto\":1.00},"
                + "{\"partidaId\":" + normal + ",\"monto\":300000.00}]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString("cuota")));

        assertEquals(0, new BigDecimal("43000.00").compareTo(
                partidaPlanRepository.findById(vinculada).orElseThrow().getMontoPlanificado()));
        assertEquals(0, new BigDecimal("250000.00").compareTo(
                partidaPlanRepository.findById(normal).orElseThrow().getMontoPlanificado()));
    }

    // ─────────────────────────── D42: vínculo desactualizado ───────────────────────────

    @Test
    @DisplayName("D42: si la deuda ya no tiene cuota ese mes se conserva el último monto y vinculoDesactualizado")
    void d42_vinculoDesactualizado() throws Exception {
        Deuda deuda = deudaRepository.save(deuda(usuario, "430000.00", 10, PRIMERA_CUOTA, "0.00"));
        crearPartidaDeuda(MES, "Cuota crédito", deuda.getId());

        deuda.setFechaPrimeraCuota(LocalDate.of(2026, 1, 5));
        deudaRepository.save(deuda);

        mockMvc.perform(get("/api/v1/planes/{mes}", MES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas[0].montoPlanificado").value(43000.00))
                .andExpect(jsonPath("$.partidas[0].vinculoDesactualizado").value(true))
                .andExpect(jsonPath("$.partidas[0].deudaId").value(deuda.getId()));
    }

    // ─────────────────────────── D44: unicidad por deuda y mes ───────────────────────────

    @Test
    @DisplayName("D44: una segunda partida para la misma deuda y mes da 409")
    void d44_unaPartidaPorDeudaYMes() throws Exception {
        Deuda deuda = deudaRepository.save(deuda(usuario, "430000.00", 10, PRIMERA_CUOTA, "0.00"));
        crearPartidaDeuda(MES, "Cuota crédito", deuda.getId());

        postPartidaDeuda(MES, "Cuota duplicada", deuda.getId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString("deuda")));

        assertEquals(1, partidaPlanRepository.buscarDelPlan(
                planMensualRepository.findByUsuarioRutAndMes(RUT, MES.atDay(1))
                        .orElseThrow().getId()).size());

        // La misma deuda en otro mes con cuota sí se puede vincular.
        postPartidaDeuda(YearMonth.of(2026, 12), "Cuota diciembre", deuda.getId(), null)
                .andExpect(status().isCreated());
    }

    // ─────────────────────────── D45: copiar y avisos ───────────────────────────

    @Test
    @DisplayName("D45: al copiar, la partida sin cuota en el destino no se copia y aparece en avisos")
    void d45_copiarConAviso() throws Exception {
        Deuda deuda = deudaRepository.save(
                deuda(usuario, "43000.00", 1, PRIMERA_CUOTA, "500.00"));
        long vinculada = crearPartidaDeuda(MES, "Cuota única", deuda.getId());
        crearPartida(MES, "Supermercado", alimentacion, "250000.00");

        MvcResult resultado = mockMvc.perform(
                        post("/api/v1/planes/{mes}/copiar-desde/{origen}",
                                YearMonth.of(2026, 12), MES)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidas.length()").value(1))
                .andExpect(jsonPath("$.partidas[0].nombre").value("Supermercado"))
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0].motivo").value("SIN_COUOTA_EN_EL_MES"))
                .andExpect(jsonPath("$.avisos[0].partidaId").value(vinculada))
                .andExpect(jsonPath("$.avisos[0].nombre").value("Cuota única"))
                .andExpect(jsonPath("$.avisos[0].deudaId").value(deuda.getId()))
                .andReturn();

        JsonNode copiado = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(copiado.path("avisos").get(0).path("mensaje").asText().contains("cuota"));

        mockMvc.perform(get("/api/v1/planes/{mes}", YearMonth.of(2026, 12))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas.length()").value(1))
                .andExpect(jsonPath("$.avisos.length()").value(0));

        mockMvc.perform(get("/api/v1/planes/{mes}", MES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos.length()").value(0));
    }

    // ─────────────────────────── helpers ───────────────────────────

    private ResultActions postPartidaDeuda(YearMonth mes, String nombre, Long deudaId,
                                           String extra) throws Exception {
        String cuerpo = "{\"nombre\":" + json(nombre) + ",\"deudaId\":" + deudaId
                + (extra == null ? "" : extra) + "}";
        return mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private long crearPartidaDeuda(YearMonth mes, String nombre, Long deudaId) throws Exception {
        MvcResult resultado = postPartidaDeuda(mes, nombre, deudaId, null)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode plan = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long id = plan.path("partidas").get(plan.path("partidas").size() - 1).path("id").asLong();
        PartidaPlan partida = partidaPlanRepository.findById(id).orElseThrow();
        assertEquals(deudaId, partida.getDeuda().getId());
        return id;
    }

    private long crearPartida(YearMonth mes, String nombre, Categoria categoria, String monto)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":" + json(nombre) + ",\"categoriaId\":"
                                + categoria.getId() + ",\"montoPlanificado\":" + monto + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode plan = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return plan.path("partidas").get(plan.path("partidas").size() - 1).path("id").asLong();
    }

    private ResultActions patchMontos(YearMonth mes, String cambios) throws Exception {
        return mockMvc.perform(patch("/api/v1/planes/{mes}/montos", mes)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cambios\":" + cambios + "}"));
    }

    private Categoria categoria(String codigo, String nombre, TipoPresupuesto tipo) {
        return categoriaRepository.findByCodigo(codigo).orElseGet(() ->
                categoriaRepository.save(new Categoria(null, codigo, nombre, null, null, tipo)));
    }

    private Usuario nuevoUsuario(String rut, String nombre, String apellido) {
        Usuario creado = new Usuario();
        creado.setRut(rut);
        creado.setNombre(nombre);
        creado.setApellido(apellido);
        creado.setEmail(rut + "@example.com");
        creado.setContrasena("$2a$10$abcdefghijklmnopqrstuv");
        creado.setFechaRegistro(new Date());
        return creado;
    }

    private static Deuda deuda(Usuario dueno, String capital, int cuotas,
                               LocalDate primeraCuota, String costoAdicional) {
        Deuda deuda = new Deuda();
        deuda.setUsuario(dueno);
        deuda.setNombre("Crédito " + capital);
        deuda.setMontoTotal(new BigDecimal(capital));
        deuda.setCantidadCuotas(cuotas);
        deuda.setMontoCapital(new BigDecimal(capital));
        deuda.setEstado(EstadoDeuda.PENDIENTE);
        deuda.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        deuda.setTasaMensual(BigDecimal.ZERO);
        deuda.setValorCuota(new BigDecimal(capital)
                .divide(BigDecimal.valueOf(cuotas), 2, java.math.RoundingMode.HALF_UP));
        deuda.setGastosIniciales(BigDecimal.ZERO);
        deuda.setCostoAdicionalPorCuota(new BigDecimal(costoAdicional));
        deuda.setCuotasPagadasPrevias(0);
        deuda.setMontoPagadoPrevio(BigDecimal.ZERO);
        deuda.setCostoTotalCredito(BigDecimal.ZERO);
        deuda.setCargaAnualEquivalente(BigDecimal.ZERO);
        deuda.setFechaPrimeraCuota(primeraCuota);
        deuda.setFechaCreacion(new Date());
        return deuda;
    }

    private String json(String valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }
}
