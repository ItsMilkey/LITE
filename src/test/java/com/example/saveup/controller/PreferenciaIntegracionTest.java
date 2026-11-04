package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PreferenciaCategoriaUsuarioRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.example.saveup.service.EliminacionDeCuentaService;
import com.example.saveup.service.movimiento.FechasMovimiento;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10d (sesión 1) — integración real (H2, MockMvc y repositorios) de los casos
 * D01 a D11 (preferencias de tipo, tipo efectivo en la ejecución y el plan) y
 * D20, D21, D23, D24 y D25 (sugerencia de regla sobre la serie). Cada test
 * corre en una transacción con rollback. Los asserts de valores decimales van
 * con el cuerpo leído con Jackson: así un nulo se distingue de un 0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PreferenciaIntegracionTest {

    private static final String RUT = "11111111-1";
    private static final String RUT_AJENO = "22222222-2";

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
    private MovimientoRepository movimientoRepository;
    @Autowired
    private PreferenciaCategoriaUsuarioRepository preferenciaRepository;
    @Autowired
    private EliminacionDeCuentaService eliminacionDeCuentaService;

    private String token;
    private String tokenAjeno;
    private Usuario usuario;
    private Usuario ajeno;
    private Categoria ocio;          // DESEO por defecto, reclasificable
    private Categoria suscripciones; // DESEO por defecto, reclasificable
    private Categoria sueldo;        // tipo OTROS: no reclasificable
    private Categoria ahorroCat;     // AHORRO: no reclasificable
    private Categoria otrosCat;      // OTROS: no reclasificable
    private Categoria vivienda;      // NECESIDAD: reclasificable

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        tokenAjeno = jwtService.generarTokenConExpiracion(RUT_AJENO, "ajeno@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "Ana", "Pérez"));
        ajeno = usuarioRepository.save(nuevoUsuario(RUT_AJENO, "Beto", "Soto"));

        ocio = categoria("OCIO_RECREACION", "Ocio y Recreación", TipoPresupuesto.DESEO);
        suscripciones = categoria("SUSCRIPCIONES_DIGITAL", "Suscripciones Digitales", TipoPresupuesto.DESEO);
        sueldo = categoria("SUELDO", "Sueldo", TipoPresupuesto.OTROS);
        ahorroCat = categoria("AHORRO_META", "Ahorro", TipoPresupuesto.AHORRO);
        otrosCat = categoria("OTROS", "Otros", TipoPresupuesto.OTROS);
        vivienda = categoria("VIVIENDA_SUMINISTROS", "Vivienda y Suministros", TipoPresupuesto.NECESIDAD);
    }

    // ─────────────────── preferencias: lectura y CRUD ───────────────────

    @Test
    @DisplayName("D01: GET /preferencias sin preferencias → tipoEfectivo = tipoPorDefecto en todas")
    void d01_todosPorDefecto() throws Exception {
        MvcResult respuesta = mockMvc.perform(get("/api/v1/categorias/preferencias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        for (JsonNode fila : cuerpo) {
            assertTrue(fila.path("tipoPreferido").isNull(), fila.path("codigo").asText());
            assertEquals(fila.path("tipoPorDefecto").asText(),
                    fila.path("tipoEfectivo").asText(), fila.path("codigo").asText());
        }
        JsonNode ocioFila = fila(cuerpo, "OCIO_RECREACION");
        assertEquals("DESEO", ocioFila.path("tipoPorDefecto").asText());
    }

    @Test
    @DisplayName("D02: PUT con NECESIDAD para una categoría DESEO → tipoEfectivo NECESIDAD")
    void d02_reclasificar() throws Exception {
        putTipo(ocio.getId(), "NECESIDAD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ocio.getId()))
                .andExpect(jsonPath("$.tipoPorDefecto").value("DESEO"))
                .andExpect(jsonPath("$.tipoPreferido").value("NECESIDAD"))
                .andExpect(jsonPath("$.tipoEfectivo").value("NECESIDAD"));

        MvcResult respuesta = mockMvc.perform(get("/api/v1/categorias/preferencias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode ocioFila = fila(objectMapper.readTree(respuesta.getResponse()
                .getContentAsString()), "OCIO_RECREACION");
        assertEquals("NECESIDAD", ocioFila.path("tipoPreferido").asText());
        assertEquals("NECESIDAD", ocioFila.path("tipoEfectivo").asText());
    }

    @Test
    @DisplayName("D03: preferir tipo a SUELDO, AHORRO u OTROS → 400 DATOS_INVALIDOS")
    void d03_noReclasificable() throws Exception {
        for (Categoria noReclasificable : List.of(sueldo, ahorroCat, otrosCat)) {
            putTipo(noReclasificable.getId(), "NECESIDAD")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
    }

    @Test
    @DisplayName("D04: tipo AHORRO u OTROS en el cuerpo → 400 DATOS_INVALIDOS")
    void d04_tiposBloqueados() throws Exception {
        putTipo(ocio.getId(), "AHORRO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        putTipo(ocio.getId(), "OTROS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("D05: tipo \"XYZ\" → 400 SOLICITUD_INVALIDA")
    void d05_tipoDesconocido() throws Exception {
        mockMvc.perform(put("/api/v1/categorias/" + ocio.getId() + "/tipo-preferido")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("D06: DELETE vuelve al por defecto y es idempotente (204 con y sin preferencia)")
    void d06_eliminacionIdempotente() throws Exception {
        putTipo(ocio.getId(), "NECESIDAD").andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/categorias/" + ocio.getId() + "/tipo-preferido")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        // Segundo DELETE sin preferencia previa: 204 igual.
        mockMvc.perform(delete("/api/v1/categorias/" + ocio.getId() + "/tipo-preferido")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        MvcResult respuesta = mockMvc.perform(get("/api/v1/categorias/preferencias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode ocioFila = fila(objectMapper.readTree(respuesta.getResponse()
                .getContentAsString()), "OCIO_RECREACION");
        assertTrue(ocioFila.path("tipoPreferido").isNull());
        assertEquals("DESEO", ocioFila.path("tipoEfectivo").asText());
    }

    @Test
    @DisplayName("D07: la preferencia de un usuario no la ve ni le afecta el otro")
    void d07_aislamientoEntreUsuarios() throws Exception {
        putTipo(ocio.getId(), "NECESIDAD").andExpect(status().isOk());

        MvcResult respuestaAjena = mockMvc.perform(get("/api/v1/categorias/preferencias")
                        .header("Authorization", "Bearer " + tokenAjeno))
                .andExpect(status().isOk()).andReturn();
        JsonNode ocioAjeno = fila(objectMapper.readTree(respuestaAjena.getResponse()
                .getContentAsString()), "OCIO_RECREACION");
        assertTrue(ocioAjeno.path("tipoPreferido").isNull(), "no ve el preferido ajeno");
        assertEquals("DESEO", ocioAjeno.path("tipoEfectivo").asText());

        // El otro usuario crea su propia fila (una por usuario).
        putTipoAjeno(ocio.getId(), "DESEO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEfectivo").value("DESEO"));
        assertEquals(2, preferenciaRepository.count());
    }

    // ─────────────────── tipo efectivo en la ejecución y el plan ───────────────────

    @Test
    @DisplayName("D08: un gasto en la categoría preferida como NECESIDAD cuenta en necesidades")
    void d08_ejecucionConTipoEfectivo() throws Exception {
        putTipo(ocio.getId(), "NECESIDAD").andExpect(status().isOk());
        YearMonth mes = FechasMovimiento.mesActual().minusMonths(1);
        movimientoRepository.save(movimiento(usuario, fecha(mes, 5), TipoMovimiento.INGRESO_GENERAL,
                "100000.00", null));
        movimientoRepository.save(movimiento(usuario, fecha(mes, 10), TipoMovimiento.GASTO_GENERAL,
                "-20000.00", ocio));

        MvcResult respuesta = mockMvc.perform(get("/api/v1/presupuestos/ejecucion/me")
                        .param("month", String.valueOf(mes.getMonthValue()))
                        .param("year", String.valueOf(mes.getYear()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        assertEquals(0, cuerpo.path("gastoNecesidades").decimalValue()
                .compareTo(new BigDecimal("20000")));
        assertEquals(0, cuerpo.path("gastoDeseos").decimalValue().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("D08b: gasto en categoría OTROS no entra ni en necesidades ni en deseos (criterio de T5)")
    void d08b_otrosIgnorado() throws Exception {
        YearMonth mes = FechasMovimiento.mesActual().minusMonths(1);
        movimientoRepository.save(movimiento(usuario, fecha(mes, 5), TipoMovimiento.INGRESO_GENERAL,
                "100000.00", null));
        movimientoRepository.save(movimiento(usuario, fecha(mes, 10), TipoMovimiento.GASTO_GENERAL,
                "-70000.00", otrosCat));

        MvcResult respuesta = mockMvc.perform(get("/api/v1/presupuestos/ejecucion/me")
                        .param("month", String.valueOf(mes.getMonthValue()))
                        .param("year", String.valueOf(mes.getYear()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        assertEquals(0, cuerpo.path("gastoNecesidades").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, cuerpo.path("gastoDeseos").decimalValue().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("D09: el plan refleja el tipo efectivo en porCategoria.tipo y trae reparto y guía nula")
    void d09_planConTipoEfectivo() throws Exception {
        putTipo(ocio.getId(), "NECESIDAD").andExpect(status().isOk());
        YearMonth mes = YearMonth.of(2026, 8);

        MvcResult respuesta = mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Cine\", \"categoriaId\": " + ocio.getId()
                                + ", \"montoPlanificado\": 150.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.porCategoria[0].categoriaId").value(ocio.getId()))
                .andExpect(jsonPath("$.porCategoria[0].tipo").value("NECESIDAD"))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());

        assertEquals(3, cuerpo.path("repartoPorTipo").size());
        assertTrue(cuerpo.has("guia") && cuerpo.path("guia").isNull(),
                "sin configuración la guía es nula");
        JsonNode repartoNecesidades = reparto(cuerpo, "NECESIDAD");
        assertEquals(0, repartoNecesidades.path("planificado").decimalValue()
                .compareTo(new BigDecimal("150")));
        // Sin ingreso de referencia, porcentajes y diferencia son nulos.
        assertTrue(repartoNecesidades.path("porcentajeDelIngreso").isNull());
        assertTrue(repartoNecesidades.path("diferenciaPuntos").isNull());
        assertEquals(0, reparto(cuerpo, "DESEO").path("planificado").decimalValue()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, reparto(cuerpo, "OTROS").path("planificado").decimalValue()
                .compareTo(BigDecimal.ZERO));
    }

    // ────────────────────────────── errores ──────────────────────────────

    @Test
    @DisplayName("D10: categoría inexistente → 404 en PUT y en DELETE")
    void d10_categoriaInexistente() throws Exception {
        putTipo(9999L, "NECESIDAD").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/categorias/9999/tipo-preferido")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("D11: el borrado de cuenta elimina las preferencias")
    void d11_borradoDeCuenta() throws Exception {
        putTipo(ocio.getId(), "NECESIDAD").andExpect(status().isOk());
        putTipoAjeno(ocio.getId(), "NECESIDAD").andExpect(status().isOk());
        assertEquals(2, preferenciaRepository.count());

        eliminacionDeCuentaService.eliminarTodo(usuario);

        assertEquals(1, preferenciaRepository.count(), "solo queda la del usuario ajeno");
    }

    // ─────────────────────────── sugerencia de regla ───────────────────────────

    @Test
    @DisplayName("D20: con 0 o 1 mes no hay sugerencia; motivo DATOS_INSUFICIENTES")
    void d20_datosInsuficientes() throws Exception {
        // 0 meses.
        MvcResult respuesta = mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAnalizados.length()").value(0))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        assertTrue(cuerpo.path("sugerencia").isNull());
        assertEquals("DATOS_INSUFICIENTES", cuerpo.path("motivo").asText());
        assertTrue(cuerpo.path("actual").path("necesidades").isNull());

        // 1 mes con ingreso: hay actual, pero no sugerencia.
        guardarMes(FechasMovimiento.mesActual().minusMonths(1), "1000.00", "-500.00");
        respuesta = mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        assertTrue(cuerpo.path("sugerencia").isNull());
        assertEquals("DATOS_INSUFICIENTES", cuerpo.path("motivo").asText());
        assertEquals(0, cuerpo.path("actual").path("necesidades").decimalValue()
                .compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("D21: meses con ingreso 0 se excluyen y solo aportan los 3 más recientes")
    void d21_limiteDeMeses() throws Exception {
        YearMonth actual = FechasMovimiento.mesActual();
        guardarMes(actual.minusMonths(4), "1000.00", "-400.00"); // fuera por el tope de 3
        guardarMes(actual.minusMonths(3), "1000.00", "-450.00");
        guardarMes(actual.minusMonths(2), "1000.00", "-500.00");
        guardarMes(actual.minusMonths(1), "1000.00", "-550.00");
        guardarMes(actual.minusMonths(6), "0.00", "-333.00");    // fuera por ingreso 0

        MvcResult respuesta = mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAnalizados.length()").value(3))
                .andExpect(jsonPath("$.mesesAnalizados[0]").value(actual.minusMonths(3).toString()))
                .andExpect(jsonPath("$.mesesAnalizados[2]").value(actual.minusMonths(1).toString()))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());

        List<String> meses = new ArrayList<>();
        cuerpo.path("mesesAnalizados").forEach(nodo -> meses.add(nodo.asText()));
        assertFalse(meses.contains(actual.minusMonths(4).toString()),
                "el 4.º mes con ingreso queda fuera del tope de 3");
        assertFalse(meses.contains(actual.minusMonths(6).toString()),
                "el mes con ingreso 0 no se analiza");
        // Mediana de {45, 50, 55} = 50; si m-4 se colara, sería 47,5.
        assertEquals(0, cuerpo.path("actual").path("necesidades").decimalValue()
                .compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("D21b: serie de D16 vía HTTP → actual 58,00/24,00/18,00; sugerencia 60/25/15")
    void d21b_sugerenciaDosMeses() throws Exception {
        YearMonth actual = FechasMovimiento.mesActual();
        guardarMes(actual.minusMonths(3), "1000.00", "-560.00", "-240.00");
        guardarMes(actual.minusMonths(2), "1000.00", "-580.00", "-220.00");
        guardarMes(actual.minusMonths(1), "1000.00", "-600.00", "-250.00");

        MvcResult respuesta = mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAnalizados.length()").value(3))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        JsonNode actual_ = cuerpo.path("actual");
        JsonNode sugerencia = cuerpo.path("sugerencia");
        assertEquals(0, actual_.path("necesidades").decimalValue().compareTo(new BigDecimal("58.00")));
        assertEquals(0, actual_.path("deseos").decimalValue().compareTo(new BigDecimal("24.00")));
        assertEquals(0, actual_.path("ahorro").decimalValue().compareTo(new BigDecimal("18.00")));
        assertEquals(0, sugerencia.path("necesidades").decimalValue().compareTo(new BigDecimal(60)));
        assertEquals(0, sugerencia.path("deseos").decimalValue().compareTo(new BigDecimal(25)));
        assertEquals(0, sugerencia.path("ahorro").decimalValue().compareTo(new BigDecimal(15)));
        assertFalse(cuerpo.path("gastoSuperaIngreso").asBoolean());
        assertTrue(cuerpo.path("motivo").isNull());
    }

    @Test
    @DisplayName("D23: el mes en curso no se incluye en la serie")
    void d23_mesEnCursoExcluido() throws Exception {
        YearMonth actual = FechasMovimiento.mesActual();
        guardarMes(actual, "1000.00", "-600.00");
        guardarMes(actual.minusMonths(1), "1000.00", "-580.00");

        mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAnalizados.length()").value(1))
                .andExpect(jsonPath("$.mesesAnalizados[0]").value(actual.minusMonths(1).toString()))
                .andExpect(jsonPath("$.actual.necesidades").value(58.00));
    }

    @Test
    @DisplayName("D24: un gasto del último día 23:30 Santiago cuenta en el mes cerrado")
    void d24_fronteraDeDia() throws Exception {
        YearMonth actual = FechasMovimiento.mesActual();
        YearMonth mesFrontera = actual.minusMonths(1);
        YearMonth anterior = actual.minusMonths(2);

        // Mes frontera: ingreso 1.000 y solo el gasto de frontera (23:30 del último día).
        guardarMes(mesFrontera, "1000.00");
        Date frontera = Date.from(LocalDate.of(mesFrontera.getYear(), mesFrontera.getMonthValue(), 31)
                .atTime(23, 30).atZone(FechasMovimiento.zona()).toInstant());
        movimientoRepository.save(movimiento(usuario, frontera, TipoMovimiento.GASTO_GENERAL,
                "-500.00", vivienda));
        // El mes anterior lleva el gasto que completa la mediana: 70/50.
        guardarMes(anterior, "1000.00", "-700.00", "-500.00");

        MvcResult respuesta = mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAnalizados.length()").value(2))
                .andExpect(jsonPath("$.mesesAnalizados[1]").value(mesFrontera.toString()))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getResponse().getContentAsString());
        // Mediana de {50, 70} = 60 y de {0, 50} = 25: solo si el gasto de
        // frontera quedó en su mes. Si se colara al mes en curso, daría 70.
        assertEquals(0, cuerpo.path("actual").path("necesidades").decimalValue()
                .compareTo(new BigDecimal("60.00")));
        assertEquals(0, cuerpo.path("actual").path("deseos").decimalValue()
                .compareTo(new BigDecimal("25.00")));
        assertEquals(0, cuerpo.path("actual").path("ahorro").decimalValue()
                .compareTo(new BigDecimal("15.00")));
    }

    @Test
    @DisplayName("D25: sin configuración igualALaGuia es falso; coincidiendo con ella, verdadero")
    void d25_igualATLaGuia() throws Exception {
        YearMonth actual = FechasMovimiento.mesActual();
        guardarMes(actual.minusMonths(2), "1000.00", "-500.00", "-300.00");
        guardarMes(actual.minusMonths(1), "1000.00", "-500.00", "-300.00");

        mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencia.necesidades").value(50))
                .andExpect(jsonPath("$.sugerencia.deseos").value(30))
                .andExpect(jsonPath("$.sugerencia.ahorro").value(20))
                .andExpect(jsonPath("$.igualALaGuia").value(false));

        mockMvc.perform(post("/api/v1/presupuestos/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"porcentajeNecesidades\": 50.00,"
                                + " \"porcentajeDeseos\": 30.00,"
                                + " \"porcentajeAhorro\": 20.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/presupuestos/sugerencia-regla")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igualALaGuia").value(true));
    }

    // ────────────────────────────── helpers ──────────────────────────────

    private static JsonNode fila(JsonNode listado, String codigo) {
        for (JsonNode nodo : listado) {
            if (codigo.equals(nodo.path("codigo").asText())) {
                return nodo;
            }
        }
        throw new AssertionError("No hay fila con código " + codigo);
    }

    private static JsonNode reparto(JsonNode plano, String tipo) {
        for (JsonNode nodo : plano.path("repartoPorTipo")) {
            if (tipo.equals(nodo.path("tipo").asText())) {
                return nodo;
            }
        }
        throw new AssertionError("No hay reparto del tipo " + tipo);
    }

    private ResultActions putTipo(Long id, String tipo) throws Exception {
        return mockMvc.perform(put("/api/v1/categorias/" + id + "/tipo-preferido")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"" + tipo + "\"}"));
    }

    private ResultActions putTipoAjeno(Long id, String tipo) throws Exception {
        return mockMvc.perform(put("/api/v1/categorias/" + id + "/tipo-preferido")
                .header("Authorization", "Bearer " + tokenAjeno)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"" + tipo + "\"}"));
    }

    /** Guarda un mes cerrado: un ingreso y, si vienen, gastos: 1.º vivienda, 2.º suscripciones. */
    private void guardarMes(YearMonth mes, String ingreso, String... gastos) {
        movimientoRepository.save(movimiento(usuario, fecha(mes, 5),
                TipoMovimiento.INGRESO_GENERAL, ingreso, null));
        Categoria[] categorias = {vivienda, suscripciones};
        for (int i = 0; i < gastos.length; i++) {
            movimientoRepository.save(movimiento(usuario, fecha(mes, 10 + i),
                    TipoMovimiento.GASTO_GENERAL, gastos[i], categorias[i % categorias.length]));
        }
    }

    private static Movimiento movimiento(Usuario dueno, Date fecha, TipoMovimiento tipo, String monto,
                                         Categoria categoria) {
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(dueno);
        movimiento.setFecha(fecha);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setMonto(new BigDecimal(monto));
        movimiento.setCategoria(categoria);
        movimiento.setDescripcion("T10d");
        return movimiento;
    }

    private static Date fecha(YearMonth mes, int dia) {
        return Date.from(LocalDate.of(mes.getYear(), mes.getMonthValue(), dia)
                .atTime(12, 0).atZone(FechasMovimiento.zona()).toInstant());
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
}
