package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.model.PlanMensual;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.PlanMensualRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10c — integración real (H2, MockMvc y repositorios) de los casos C21 y
 * C23–C58 y C61. Cada test corre en una transacción con rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanIntegracionTest {

    private static final String RUT = "11111111-1";
    private static final String RUT_AJENO = "22222222-2";
    private static final YearMonth MES = YearMonth.of(2026, 11);
    private static final YearMonth OTRO_MES = YearMonth.of(2026, 10);
    private static final YearMonth MES_ACTUAL = FechasMovimiento.mesActual();

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
    private PlanMensualRepository planMensualRepository;
    @Autowired
    private PartidaPlanRepository partidaPlanRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private DeudaRepository deudaRepository;
    @Autowired
    private EliminacionDeCuentaService eliminacionDeCuentaService;

    private String token;
    private String tokenAjeno;
    private Usuario usuario;
    private Usuario ajeno;
    private Categoria alimentacion;
    private Categoria transporte;
    private Categoria vivienda;
    private Categoria suscripciones;
    private Categoria deudas;
    private Categoria sueldo;
    private Categoria ahorro;
    private Categoria ocio;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        tokenAjeno = jwtService.generarTokenConExpiracion(RUT_AJENO, "ajeno@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "Ana", "Pérez"));
        ajeno = usuarioRepository.save(nuevoUsuario(RUT_AJENO, "Beto", "Soto"));

        alimentacion = categoria("ALIMENTACION_BASE", "Alimentación Base", TipoPresupuesto.NECESIDAD);
        transporte = categoria("TRANSPORTE_MOVILIDAD", "Transporte y Movilidad", TipoPresupuesto.NECESIDAD);
        vivienda = categoria("VIVIENDA_SUMINISTROS", "Vivienda y Suministros", TipoPresupuesto.NECESIDAD);
        suscripciones = categoria("SUSCRIPCIONES_DIGITAL", "Suscripciones Digitales", TipoPresupuesto.DESEO);
        deudas = categoria("DEUDAS", "Pago de Deudas", TipoPresupuesto.NECESIDAD);
        sueldo = categoria("SUELDO", "Sueldo", TipoPresupuesto.OTROS);
        ahorro = categoria("AHORRO", "Ahorro", TipoPresupuesto.AHORRO);
        ocio = categoria("OCIO_RECREACION", "Ocio y Recreación", TipoPresupuesto.DESEO);
    }

    // ─────────────────────────── lectura y plan ───────────────────────────

    @Test
    @DisplayName("C21: un mes sin plan con gastos devuelve existe=false y todo el gasto sin planificar")
    void c21_mesSinPlanConGastos() throws Exception {
        movimientoRepository.save(movimiento(usuario, fecha(2026, 3, 10), TipoMovimiento.GASTO_GENERAL,
                "-12000.00", alimentacion, null, "Compra suelta"));

        mockMvc.perform(get("/api/v1/planes/2026-03").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value("2026-03"))
                .andExpect(jsonPath("$.existe").value(false))
                .andExpect(jsonPath("$.partidas.length()").value(0))
                .andExpect(jsonPath("$.resumen.planificadoTotal").value(0.00))
                .andExpect(jsonPath("$.resumen.remanenteLibre").doesNotExist())
                .andExpect(jsonPath("$.resumen.sinPlanificar.monto").value(12000.00))
                .andExpect(jsonPath("$.resumen.sinPlanificar.motivo").doesNotExist())
                .andExpect(jsonPath("$.resumen.sinPlanificar.porMotivo[0].motivo").value("SIN_PARTIDA"));
    }

    @Test
    @DisplayName("C23: POST de una partida válida en un mes sin plan responde 201 y crea el plan")
    void c23_crearPartidaCreaPlan() throws Exception {
        postPartida(MES, "Supermercado", alimentacion.getId(), "250000.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.existe").value(true))
                .andExpect(jsonPath("$.partidas.length()").value(1))
                .andExpect(jsonPath("$.partidas[0].nombre").value("Supermercado"))
                .andExpect(jsonPath("$.partidas[0].orden").value(1));

        assertTrue(planMensualRepository
                .findByUsuarioRutAndMes(RUT, MES.atDay(1)).isPresent());
    }

    // ─────────────────────────── validación de partidas ───────────────────────────

    @Test
    @DisplayName("C24: nombre vacío o solo espacios responde 400 VALIDACION_FALLIDA")
    void c24_nombreVacio() throws Exception {
        for (String nombre : List.of("", "   ")) {
            postPartida(MES, nombre, alimentacion.getId(), "1000.00")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        }
    }

    @Test
    @DisplayName("C25: un nombre de 81 caracteres responde 400")
    void c25_nombreLargo() throws Exception {
        postPartida(MES, "x".repeat(81), alimentacion.getId(), "1000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    @DisplayName("C26: \"Super\" y luego \" súper \" en el mismo mes responde 409")
    void c26_nombreDuplicadoNormalizado() throws Exception {
        postPartida(MES, "Super", alimentacion.getId(), "1000.00").andExpect(status().isCreated());

        postPartida(MES, " súper ", alimentacion.getId(), "2000.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString("nombre")));
    }

    @Test
    @DisplayName("C27: el mismo nombre en otro mes responde 201")
    void c27_mismoNombreOtroMes() throws Exception {
        postPartida(MES, "Super", alimentacion.getId(), "1000.00").andExpect(status().isCreated());
        postPartida(OTRO_MES, "Super", alimentacion.getId(), "1000.00").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("C28: las categorías SUELDO y AHORRO no se pueden usar en partidas")
    void c28_categoriasNoPermitidas() throws Exception {
        postPartida(MES, "Sueldo", sueldo.getId(), "1000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        postPartida(MES, "Ahorro", ahorro.getId(), "1000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("C29: una categoría inexistente responde 404 NO_ENCONTRADO")
    void c29_categoriaInexistente() throws Exception {
        postPartida(MES, "Fantasma", 999_999L, "1000.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("C30: monto negativo, con 3 decimales o con 14 enteros responde 400")
    void c30_montosInvalidos() throws Exception {
        postPartida(MES, "Negativo", alimentacion.getId(), "-1.00")
                .andExpect(status().isBadRequest());
        postPartida(MES, "Decimales", alimentacion.getId(), "1.234")
                .andExpect(status().isBadRequest());
        postPartida(MES, "Enteros", alimentacion.getId(), "12345678901234.00")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("C31: la partida número 101 responde 409")
    void c31_maximoPartidas() throws Exception {
        PlanMensual plan = planDe(MES);
        for (int i = 1; i <= 100; i++) {
            partidaPlanRepository.save(partidaDe(plan, "Partida " + i, alimentacion, "0.00", i));
        }

        postPartida(MES, "Partida 101", alimentacion.getId(), "0.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));
    }

    @Test
    @DisplayName("C32: meses con formato o rango inválido responden 400 PARAMETRO_INVALIDO")
    void c32_mesesInvalidos() throws Exception {
        for (String mes : List.of("2026-1", "2026-13", "abc", "1999-12", "2101-01")) {
            mockMvc.perform(get("/api/v1/planes/" + mes).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        }
    }

    @Test
    @DisplayName("C33: ingreso de referencia -1 → 400; 0 → 200; nulo → 200 (limpia); 1.000.000 → 200")
    void c33_ingresoReferencia() throws Exception {
        putIngreso(MES, "-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));

        putIngreso(MES, "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresoReferencia").value(0.00));
        putIngreso(MES, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresoReferencia").doesNotExist());
        putIngreso(MES, "1000000.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresoReferencia").value(1000000.00));
    }

    @Test
    @DisplayName("C34: PUT de partida sin campos responde 400")
    void c34_putSinCampos() throws Exception {
        long id = crearPartida(MES, "Super", alimentacion, "1000.00");

        mockMvc.perform(put("/api/v1/planes/{mes}/partidas/{id}", MES, id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    @DisplayName("C35: cambiar la categoría con movimientos vinculados da 409; sin ellos da 200")
    void c35_cambioCategoria() throws Exception {
        long conMovimientos = crearPartida(MES, "Super", alimentacion, "1000.00");
        PartidaPlan partida = partidaPlanRepository.findById(conMovimientos).orElseThrow();
        movimientoRepository.save(movimiento(usuario, fecha(2026, 11, 5), TipoMovimiento.GASTO_GENERAL,
                "-500.00", alimentacion, partida, "Compra"));

        putPartida(MES, conMovimientos, "{\"categoriaId\":" + transporte.getId() + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));

        long sinMovimientos = crearPartida(MES, "Feria", alimentacion, "1000.00");
        putPartida(MES, sinMovimientos, "{\"categoriaId\":" + transporte.getId() + "}")
                .andExpect(status().isOk());
        assertEquals(transporte.getId(),
                partidaPlanRepository.findById(sinMovimientos).orElseThrow().getCategoria().getId());
    }

    @Test
    @DisplayName("C36: borrar una partida con movimientos los deja sin partida y se reimputan por categoría")
    void c36_borrarPartida() throws Exception {
        long superId = crearPartida(MES, "Super", alimentacion, "1000.00");
        long feriaId = crearPartida(MES, "Feria", alimentacion, "1000.00");
        PartidaPlan superPartida = partidaPlanRepository.findById(superId).orElseThrow();
        Movimiento movimiento = movimientoRepository.save(movimiento(usuario, fecha(2026, 11, 5),
                TipoMovimiento.GASTO_GENERAL, "-600.00", alimentacion, superPartida, "Compra"));

        mockMvc.perform(delete("/api/v1/planes/{mes}/partidas/{id}", MES, superId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertNull(movimientoRepository.findById(movimiento.getId()).orElseThrow().getPartida());
        mockMvc.perform(get("/api/v1/planes/{mes}", MES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas.length()").value(1))
                .andExpect(jsonPath("$.partidas[0].id").value(feriaId))
                .andExpect(jsonPath("$.partidas[0].real").value(600.00));
    }

    // ─────────────────────────── propiedad y meses ───────────────────────────

    @Test
    @DisplayName("C37: una partida de otro usuario responde 403 en PUT, PATCH y DELETE")
    void c37_partidaAjena() throws Exception {
        long ajena = crearPartida(ajeno, MES, "Ajena", alimentacion, "1000.00");
        planDe(MES); // el PATCH necesita que el usuario tenga plan para llegar al 403

        putPartida(MES, ajena, "{\"nombre\":\"Robada\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        patchMontos(MES, "[{\"partidaId\":" + ajena + ",\"monto\":2000.00}]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(delete("/api/v1/planes/{mes}/partidas/{id}", MES, ajena)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    @DisplayName("C38: una partida de otro mes en la ruta de este mes responde 404")
    void c38_partidaDeOtroMes() throws Exception {
        long idOtroMes = crearPartida(usuario, OTRO_MES, "Octubre", alimentacion, "1000.00");
        planDe(MES); // el PATCH necesita que el usuario tenga plan para llegar al 404

        putPartida(MES, idOtroMes, "{\"nombre\":\"Noviembre\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        patchMontos(MES, "[{\"partidaId\":" + idOtroMes + ",\"monto\":2000.00}]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(delete("/api/v1/planes/{mes}/partidas/{id}", MES, idOtroMes)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────── PATCH de montos ───────────────────────────

    @Test
    @DisplayName("C39: PATCH de montos válido responde 200 con el plan recalculado")
    void c39_patchValido() throws Exception {
        long superId = crearPartida(MES, "Super", alimentacion, "250000.00");
        crearPartida(MES, "Arriendo", vivienda, "400000.00");

        patchMontos(MES, "[{\"partidaId\":" + superId + ",\"monto\":300000.00}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.planificadoTotal").value(700000.00));
        assertEquals(0, new BigDecimal("300000.00").compareTo(
                partidaPlanRepository.findById(superId).orElseThrow().getMontoPlanificado()));
    }

    @Test
    @DisplayName("C40: un monto inválido entre varios deja todo sin cambios")
    void c40_patchInvalidoAtomico() throws Exception {
        long superId = crearPartida(MES, "Super", alimentacion, "250000.00");
        long arriendoId = crearPartida(MES, "Arriendo", vivienda, "400000.00");

        patchMontos(MES, "[{\"partidaId\":" + superId + ",\"monto\":300000.00},"
                + "{\"partidaId\":" + arriendoId + ",\"monto\":-1.00}]")
                .andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("250000.00").compareTo(
                partidaPlanRepository.findById(superId).orElseThrow().getMontoPlanificado()));
        assertEquals(0, new BigDecimal("400000.00").compareTo(
                partidaPlanRepository.findById(arriendoId).orElseThrow().getMontoPlanificado()));
    }

    @Test
    @DisplayName("C41: PATCH con partidaId repetido, sin cambios o con 101 cambios responde 400")
    void c41_lotesInvalidos() throws Exception {
        long superId = crearPartida(MES, "Super", alimentacion, "250000.00");

        patchMontos(MES, "[{\"partidaId\":" + superId + ",\"monto\":1.00},"
                + "{\"partidaId\":" + superId + ",\"monto\":2.00}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
        patchMontos(MES, "[]").andExpect(status().isBadRequest());

        StringBuilder cambios = new StringBuilder();
        for (int i = 1; i <= 101; i++) {
            if (i > 1) {
                cambios.append(',');
            }
            cambios.append("{\"partidaId\":").append(superId).append(",\"monto\":").append(i).append(".00}");
        }
        patchMontos(MES, cambios.toString()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("C42: repetir el PATCH con los mismos montos no vuelve a cambiar actualizadoEn")
    void c42_patchIdempotente() throws Exception {
        long superId = crearPartida(MES, "Super", alimentacion, "250000.00");

        String primero = patchMontos(MES, "[{\"partidaId\":" + superId + ",\"monto\":300000.00}]")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String segundo = patchMontos(MES, "[{\"partidaId\":" + superId + ",\"monto\":300000.00}]")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(objectMapper.readTree(primero).path("partidas").get(0).path("actualizadoEn"),
                objectMapper.readTree(segundo).path("partidas").get(0).path("actualizadoEn"));
    }

    @Test
    @DisplayName("C43: un PATCH con una partida inexistente entre varias responde 404 y no aplica nada")
    void c43_patchInexistente() throws Exception {
        long superId = crearPartida(MES, "Super", alimentacion, "250000.00");

        patchMontos(MES, "[{\"partidaId\":" + superId + ",\"monto\":300000.00},"
                + "{\"partidaId\":999999,\"monto\":1.00}]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        assertEquals(0, new BigDecimal("250000.00").compareTo(
                partidaPlanRepository.findById(superId).orElseThrow().getMontoPlanificado()));
    }

    // ─────────────────────────── copiar ───────────────────────────

    @Test
    @DisplayName("C44: copiar un mes con 5 partidas a un mes vacío responde 201 y copia el ingreso si falta")
    void c44_copiar() throws Exception {
        putIngreso(MES, "1000000.00").andExpect(status().isOk());
        crearPartida(MES, "Arriendo", vivienda, "400000.00");
        crearPartida(MES, "Super", alimentacion, "250000.00");
        crearPartida(MES, "Transporte", transporte, "80000.00");
        crearPartida(MES, "Netflix", suscripciones, "10000.00");
        crearPartida(MES, "Spotify", suscripciones, "6000.00");

        mockMvc.perform(post("/api/v1/planes/{mes}/copiar-desde/{origen}", OTRO_MES, MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mes").value(OTRO_MES.toString()))
                .andExpect(jsonPath("$.partidas.length()").value(5))
                .andExpect(jsonPath("$.ingresoReferencia").value(1000000.00))
                .andExpect(jsonPath("$.partidas[0].nombre").value("Arriendo"));
    }

    @Test
    @DisplayName("C45: copiar a un mes que ya tiene partidas responde 409")
    void c45_copiarDestinoConPartidas() throws Exception {
        crearPartida(MES, "Super", alimentacion, "1000.00");
        crearPartida(OTRO_MES, "Arriendo", vivienda, "2000.00");

        mockMvc.perform(post("/api/v1/planes/{mes}/copiar-desde/{origen}", OTRO_MES, MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));
    }

    @Test
    @DisplayName("C46: copiar desde un mes sin partidas da 404 y desde sí mismo da 400")
    void c46_copiarInvalido() throws Exception {
        putIngreso(MES, "1000000.00").andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/planes/{mes}/copiar-desde/{origen}", OTRO_MES, MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/planes/{mes}/copiar-desde/{origen}", MES, MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ─────────────────────────── movimientos con partida ───────────────────────────

    @Test
    @DisplayName("C47: POST de un gasto con partidaId del mes actual deriva la categoría y sube el real")
    void c47_gastoConPartida() throws Exception {
        long partidaId = crearPartida(usuario, MES_ACTUAL, "Super", alimentacion, "250000.00");

        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10000,\"descripcion\":\"Compra\","
                                + "\"tipoMovimiento\":\"GASTO_GENERAL\",\"partidaId\":" + partidaId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidaId").value(partidaId))
                .andExpect(jsonPath("$.categoria.codigo").value("ALIMENTACION_BASE"));

        mockMvc.perform(get("/api/v1/planes/{mes}", MES_ACTUAL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas[0].real").value(10000.00));
    }

    @Test
    @DisplayName("C48: POST de un gasto con partidaId y categoriaId distinta responde 400")
    void c48_categoriaNoCoincide() throws Exception {
        long partidaId = crearPartida(usuario, MES_ACTUAL, "Super", alimentacion, "250000.00");

        postGastoConPartida(partidaId, transporte.getId(), "1000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("C49: POST de un gasto con partida de otro mes responde 400")
    void c49_partidaDeOtroMes() throws Exception {
        long partidaId = crearPartida(usuario, MES_ACTUAL.minusMonths(1), "Vieja", alimentacion, "1000.00");

        postGastoConPartida(partidaId, null, "1000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("C50: POST de un ingreso con partidaId responde 400")
    void c50_ingresoConPartida() throws Exception {
        long partidaId = crearPartida(usuario, MES_ACTUAL, "Super", alimentacion, "250000.00");

        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":1000,\"descripcion\":\"Ingreso\","
                                + "\"tipoMovimiento\":\"INGRESO_GENERAL\",\"partidaId\":" + partidaId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("C51: PUT asigna partida, desvincularPartida la quita y ambos juntos responden 400")
    void c51_editarPartidaDeMovimiento() throws Exception {
        long partidaId = crearPartida(usuario, MES_ACTUAL, "Super", alimentacion, "250000.00");
        long movimientoId = crearGasto("1000.00");

        mockMvc.perform(put("/api/v1/movimientos/{id}", movimientoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidaId\":" + partidaId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").value(partidaId));

        mockMvc.perform(put("/api/v1/movimientos/{id}", movimientoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desvincularPartida\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").doesNotExist());

        mockMvc.perform(put("/api/v1/movimientos/{id}", movimientoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidaId\":" + partidaId + ",\"desvincularPartida\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("C52: PUT con categoría distinta en un gasto con partida responde 400 con el mensaje de la sección 8")
    void c52_categoriaDistintaConPartida() throws Exception {
        long partidaId = crearPartida(usuario, MES_ACTUAL, "Super", alimentacion, "250000.00");
        long movimientoId = crearGasto("1000.00");

        mockMvc.perform(put("/api/v1/movimientos/{id}", movimientoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidaId\":" + partidaId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/movimientos/{id}", movimientoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\":" + transporte.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString(
                        "Cambia la partida o desvincula el gasto antes de cambiar la categoría.")));
    }

    @Test
    @DisplayName("C53: GET paginados con partidaId devuelve solo los movimientos de esa partida")
    void c53_filtroPorPartida() throws Exception {
        PlanMensual plan = planDe(MES);
        PartidaPlan superPartida = partidaPlanRepository.save(
                partidaDe(plan, "Super", alimentacion, "1000.00", 1));
        PartidaPlan feria = partidaPlanRepository.save(
                partidaDe(plan, "Feria", alimentacion, "1000.00", 2));
        movimientoRepository.save(movimiento(usuario, fecha(2026, 11, 5), TipoMovimiento.GASTO_GENERAL,
                "-100.00", alimentacion, superPartida, "Super"));
        movimientoRepository.save(movimiento(usuario, fecha(2026, 11, 6), TipoMovimiento.GASTO_GENERAL,
                "-200.00", alimentacion, feria, "Feria"));

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("partidaId", superPartida.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Super"))
                .andExpect(jsonPath("$.content[0].partidaId").value(superPartida.getId()));
    }

    @Test
    @DisplayName("C54: PAGO_DEUDA por /movimientos con partida DEUDAS da 201 y con otra categoría da 400")
    void c54_pagoDeudaConPartida() throws Exception {
        Deuda deuda = deudaRepository.save(deuda(usuario));
        long partidaDeudas = crearPartida(usuario, MES_ACTUAL, "Cuota crédito", deudas, "50000.00");
        long partidaAlimentacion = crearPartida(usuario, MES_ACTUAL, "Super", alimentacion, "50000.00");

        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10000,\"descripcion\":\"Cuota\","
                                + "\"tipoMovimiento\":\"PAGO_DEUDA\",\"deudaId\":" + deuda.getId()
                                + ",\"partidaId\":" + partidaDeudas + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidaId").value(partidaDeudas));

        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10000,\"descripcion\":\"Cuota\","
                                + "\"tipoMovimiento\":\"PAGO_DEUDA\",\"deudaId\":" + deuda.getId()
                                + ",\"partidaId\":" + partidaAlimentacion + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("C55: un gasto del 31-oct 23:30 en Santiago cuenta en octubre y no en noviembre")
    void c55_limiteDeMesEnSantiago() throws Exception {
        PlanMensual octubre = planDe(OTRO_MES);
        PartidaPlan partida = partidaPlanRepository.save(
                partidaDe(octubre, "Arriendo", vivienda, "400000.00", 1));
        // 2026-11-01T02:30Z = 2026-10-31T23:30 en America/Santiago (UTC-3).
        movimientoRepository.save(movimiento(usuario, Date.from(Instant.parse("2026-11-01T02:30:00Z")),
                TipoMovimiento.GASTO_GENERAL, "-5000.00", vivienda, partida, "Tarde"));

        mockMvc.perform(get("/api/v1/planes/2026-10").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas[0].real").value(5000.00));

        mockMvc.perform(get("/api/v1/planes/2026-11").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(false))
                .andExpect(jsonPath("$.resumen.sinPlanificar.monto").value(0.00));
    }

    // ─────────────────────────── seguridad y borrado ───────────────────────────

    @Test
    @DisplayName("C57: sin token responde 401 y el plan de otro usuario no aparece")
    void c57_aislamientoEntreUsuarios() throws Exception {
        crearPartida(ajeno, MES, "Ajena", alimentacion, "1000.00");

        mockMvc.perform(get("/api/v1/planes/{mes}", MES))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/planes/{mes}", MES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(false));

        mockMvc.perform(get("/api/v1/planes/{mes}", MES).header("Authorization", "Bearer " + tokenAjeno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true))
                .andExpect(jsonPath("$.partidas[0].nombre").value("Ajena"));
    }

    @Test
    @DisplayName("C58: el borrado de cuenta elimina planes y partidas sin errores de FK")
    void c58_borradoDeCuenta() throws Exception {
        PlanMensual plan = planDe(MES);
        PartidaPlan partida = partidaPlanRepository.save(
                partidaDe(plan, "Super", alimentacion, "1000.00", 1));
        movimientoRepository.save(movimiento(usuario, fecha(2026, 11, 5), TipoMovimiento.GASTO_GENERAL,
                "-100.00", alimentacion, partida, "Compra"));

        eliminacionDeCuentaService.eliminarTodo(usuario);

        assertTrue(planMensualRepository.findByUsuarioRutAndMes(RUT, MES.atDay(1)).isEmpty());
        assertTrue(partidaPlanRepository.findById(partida.getId()).isEmpty());
    }

    // ─────────────────────────── sugeridor con historial ───────────────────────────

    @Test
    @DisplayName("C61: el historial de partidas sube la categoría a 0,95 con motivo HISTORIAL solo para su dueño")
    void c61_sugeridorConHistorial() throws Exception {
        PlanMensual plan = planDe(OTRO_MES);
        partidaPlanRepository.save(partidaDe(plan, "Super", alimentacion, "1000.00", 1));

        mockMvc.perform(get("/api/v1/categorias/sugerencia")
                        .param("texto", "super")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias[0].codigo").value("ALIMENTACION_BASE"))
                .andExpect(jsonPath("$.sugerencias[0].confianza").value(0.95))
                .andExpect(jsonPath("$.sugerencias[0].motivo").value("HISTORIAL"));

        mockMvc.perform(get("/api/v1/categorias/sugerencia")
                        .param("texto", "super")
                        .header("Authorization", "Bearer " + tokenAjeno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias[0].motivo").value("PALABRA_CLAVE"))
                .andExpect(jsonPath("$.sugerencias[0].confianza").value(0.90));
    }

    // ─────────────────────────── helpers ───────────────────────────

    private org.springframework.test.web.servlet.ResultActions postPartida(YearMonth mes, String nombre,
                                                                          Long categoriaId, String monto)
            throws Exception {
        return mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":" + json(nombre) + ",\"categoriaId\":" + categoriaId
                        + ",\"montoPlanificado\":" + monto + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions putIngreso(YearMonth mes, String montoJson)
            throws Exception {
        return mockMvc.perform(put("/api/v1/planes/{mes}/ingreso-referencia", mes)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\":" + montoJson + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions putPartida(YearMonth mes, long id, String body)
            throws Exception {
        return mockMvc.perform(put("/api/v1/planes/{mes}/partidas/{id}", mes, id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions patchMontos(YearMonth mes, String cambios)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/planes/{mes}/montos", mes)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cambios\":" + cambios + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions postGastoConPartida(Long partidaId,
                                                                                 Long categoriaId, String monto)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"monto\":").append(monto)
                .append(",\"descripcion\":\"Compra\",\"tipoMovimiento\":\"GASTO_GENERAL\"");
        if (partidaId != null) {
            body.append(",\"partidaId\":").append(partidaId);
        }
        if (categoriaId != null) {
            body.append(",\"categoriaId\":").append(categoriaId);
        }
        body.append('}');
        return mockMvc.perform(post("/api/v1/movimientos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private long crearPartida(YearMonth mes, String nombre, Categoria categoria, String monto) throws Exception {
        return crearPartida(usuario, mes, nombre, categoria, monto);
    }

    private long crearPartida(Usuario dueno, YearMonth mes, String nombre, Categoria categoria, String monto)
            throws Exception {
        String duenoToken = dueno.getRut().equals(RUT) ? token : tokenAjeno;
        MvcResult resultado = mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                        .header("Authorization", "Bearer " + duenoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":" + json(nombre) + ",\"categoriaId\":" + categoria.getId()
                                + ",\"montoPlanificado\":" + monto + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode plan = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long id = plan.path("partidas").get(plan.path("partidas").size() - 1).path("id").asLong();
        assertNotNull(partidaPlanRepository.findById(id).orElse(null));
        return id;
    }

    private long crearGasto(String monto) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":" + monto + ",\"descripcion\":\"Gasto\","
                                + "\"tipoMovimiento\":\"GASTO_GENERAL\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asLong();
    }

    private PlanMensual planDe(YearMonth mes) {
        return planMensualRepository.findByUsuarioRutAndMes(usuario.getRut(), mes.atDay(1))
                .orElseGet(() -> {
                    PlanMensual plan = new PlanMensual();
                    plan.setUsuario(usuario);
                    plan.setMes(mes.atDay(1));
                    return planMensualRepository.save(plan);
                });
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

    private Deuda deuda(Usuario dueno) {
        Deuda deuda = new Deuda();
        deuda.setUsuario(dueno);
        deuda.setNombre("Crédito");
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
        deuda.setCostoTotalCredito(BigDecimal.ZERO);
        deuda.setCargaAnualEquivalente(BigDecimal.ZERO);
        deuda.setFechaCreacion(new Date());
        return deuda;
    }

    private static PartidaPlan partidaDe(PlanMensual plan, String nombre, Categoria categoria,
                                         String monto, int orden) {
        String normalizado = com.example.saveup.service.plan.NombrePartida.normalizar(nombre);
        PartidaPlan partida = new PartidaPlan();
        partida.setPlan(plan);
        partida.setCategoria(categoria);
        partida.setNombre(nombre);
        partida.setNombreNormalizado(normalizado);
        partida.setMontoPlanificado(new BigDecimal(monto));
        partida.setOrden(orden);
        return partida;
    }

    private static Movimiento movimiento(Usuario dueno, Date fecha, TipoMovimiento tipo, String monto,
                                         Categoria categoria, PartidaPlan partida, String descripcion) {
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(dueno);
        movimiento.setFecha(fecha);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setMonto(new BigDecimal(monto));
        movimiento.setCategoria(categoria);
        movimiento.setPartida(partida);
        movimiento.setDescripcion(descripcion);
        return movimiento;
    }

    private static Date fecha(int anio, int mes, int dia) {
        return Date.from(LocalDate.of(anio, mes, dia).atTime(12, 0)
                .atZone(ZoneId.of("America/Santiago")).toInstant());
    }

    private String json(String valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    /** httpStatus helpers para asserts de valores numéricos exactos. */
    @SuppressWarnings("unused")
    private static void assertMoney(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real));
    }
}
