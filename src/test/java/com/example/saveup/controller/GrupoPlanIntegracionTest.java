package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.PartidaPlanRepository;
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
import java.time.YearMonth;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10d (sesión 2) — integración real (H2, MockMvc y repositorios) de los
 * grupos del plan: CRUD y límites (D27), propiedad (D28), borrado con partidas
 * (D29), conservación al copiar un mes (D33) y quitarGrupo (D34).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GrupoPlanIntegracionTest {

    private static final String RUT = "11111111-1";
    private static final String RUT_AJENO = "22222222-2";
    private static final YearMonth MES = YearMonth.of(2026, 11);
    private static final YearMonth OTRO_MES = YearMonth.of(2026, 10);

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
    private PartidaPlanRepository partidaPlanRepository;

    private String token;
    private String tokenAjeno;
    private Usuario usuario;
    private Usuario ajeno;
    private Categoria alimentacion;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        tokenAjeno = jwtService.generarTokenConExpiracion(RUT_AJENO, "ajeno@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "Ana", "Pérez"));
        ajeno = usuarioRepository.save(nuevoUsuario(RUT_AJENO, "Beto", "Soto"));
        alimentacion = categoriaRepository.findByCodigo("ALIMENTACION_BASE")
                .orElseGet(() -> categoriaRepository.save(new Categoria(null, "ALIMENTACION_BASE",
                        "Alimentación Base", null, null, TipoPresupuesto.NECESIDAD)));
    }

    // ─────────────────────────── D27: CRUD y límites ───────────────────────────

    @Test
    @DisplayName("D27: CRUD de grupos; nombre duplicado y más de 20 dan 409; 61 caracteres da 400")
    void d27_crudLimites() throws Exception {
        mockMvc.perform(get("/api/v1/planes/grupos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        long fijos = crearGrupo("{\"nombre\":\"Fijos\"}", token);

        postGrupo("{\"nombre\":\" fijos \"}", token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));

        postGrupo("{\"nombre\":\"" + "x".repeat(61) + "\"}", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));

        mockMvc.perform(put("/api/v1/planes/grupos/{id}", fijos)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Gastos fijos\",\"orden\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fijos))
                .andExpect(jsonPath("$.nombre").value("Gastos fijos"))
                .andExpect(jsonPath("$.orden").value(2));

        // 20 grupos en total: el siguiente supera el máximo.
        for (int i = 1; i <= 18; i++) {
            crearGrupo("{\"nombre\":\"Grupo " + i + "\"}", token);
        }
        crearGrupo("{\"nombre\":\"Grupo 19\"}", token);

        postGrupo("{\"nombre\":\"Grupo 20\"}", token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"));

        mockMvc.perform(get("/api/v1/planes/grupos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[19].nombre").value("Gastos fijos"))
                .andExpect(jsonPath("$[19].orden").value(2));
    }

    // ─────────────────────────── D28: propiedad ───────────────────────────

    @Test
    @DisplayName("D28: asignar un grupo ajeno da 403 y uno inexistente da 404")
    void d28_grupoAjenoOInexistente() throws Exception {
        long grupoAjeno = crearGrupo("{\"nombre\":\"Ajeno\"}", tokenAjeno);

        postPartida(MES, "Super", alimentacion.getId(), "1000.00", grupoAjeno)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        postPartida(MES, "Super", alimentacion.getId(), "1000.00", 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // ─────────────────────────── D29: borrado ───────────────────────────

    @Test
    @DisplayName("D29: borrar un grupo con partidas las deja sin grupo")
    void d29_borrarGrupoDesvincula() throws Exception {
        long grupo = crearGrupo("{\"nombre\":\"Fijos\"}", token);
        long superId = crearPartida(MES, "Supermercado", alimentacion, "250000.00", grupo);
        long feriaId = crearPartida(MES, "Feria", alimentacion, "30000.00", grupo);

        mockMvc.perform(delete("/api/v1/planes/grupos/{id}", grupo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/planes/{mes}", MES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas.length()").value(2))
                .andExpect(jsonPath("$.partidas[0].grupoId").doesNotExist())
                .andExpect(jsonPath("$.partidas[1].grupoId").doesNotExist())
                .andExpect(jsonPath("$.grupos.length()").value(1))
                .andExpect(jsonPath("$.grupos[0].id").doesNotExist())
                .andExpect(jsonPath("$.grupos[0].nombre").value("Sin grupo"));

        assertNull(partidaPlanRepository.findById(superId).orElseThrow().getGrupo());
        assertNull(partidaPlanRepository.findById(feriaId).orElseThrow().getGrupo());
    }

    // ─────────────────────────── D33: copiar ───────────────────────────

    @Test
    @DisplayName("D33: copiar un mes con partidas agrupadas conserva el grupo")
    void d33_copiarConservaGrupo() throws Exception {
        long grupo = crearGrupo("{\"nombre\":\"Fijos\",\"orden\":1}", token);
        long partidaId = crearPartida(MES, "Arriendo", alimentacion, "400000.00", grupo);

        mockMvc.perform(post("/api/v1/planes/{mes}/copiar-desde/{origen}", OTRO_MES, MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidas.length()").value(1))
                .andExpect(jsonPath("$.partidas[0].grupoId").value(grupo))
                .andExpect(jsonPath("$.grupos.length()").value(1))
                .andExpect(jsonPath("$.grupos[0].id").value(grupo))
                .andExpect(jsonPath("$.grupos[0].planificado").value(400000.00));

        PartidaPlan original = partidaPlanRepository.findById(partidaId).orElseThrow();
        assertEquals(grupo, original.getGrupo().getId());
        assertEquals(1, partidaPlanRepository.buscarDelPlan(
                original.getPlan().getId()).size());
    }

    // ─────────────────────────── D34: quitarGrupo ───────────────────────────

    @Test
    @DisplayName("D34: quitarGrupo junto con grupoId da 400; solo quitarGrupo quita el grupo")
    void d34_quitarGrupo() throws Exception {
        long grupo = crearGrupo("{\"nombre\":\"Fijos\"}", token);
        long partidaId = crearPartida(MES, "Arriendo", alimentacion, "400000.00", grupo);

        putPartida(MES, partidaId, "{\"grupoId\":" + grupo + ",\"quitarGrupo\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        assertEquals(grupo,
                partidaPlanRepository.findById(partidaId).orElseThrow().getGrupo().getId());

        putPartida(MES, partidaId, "{\"quitarGrupo\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas[0].grupoId").doesNotExist());
        assertNull(partidaPlanRepository.findById(partidaId).orElseThrow().getGrupo());
    }

    // ─────────────────────────── helpers ───────────────────────────

    private ResultActions postGrupo(String cuerpo, String tokenDueno) throws Exception {
        return mockMvc.perform(post("/api/v1/planes/grupos")
                .header("Authorization", "Bearer " + tokenDueno)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private long crearGrupo(String cuerpo, String tokenDueno) throws Exception {
        MvcResult resultado = postGrupo(cuerpo, tokenDueno)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asLong();
    }

    private ResultActions postPartida(YearMonth mes, String nombre, Long categoriaId,
                                      String monto, Long grupoId) throws Exception {
        StringBuilder cuerpo = new StringBuilder("{\"nombre\":").append(json(nombre))
                .append(",\"categoriaId\":").append(categoriaId)
                .append(",\"montoPlanificado\":").append(monto);
        if (grupoId != null) {
            cuerpo.append(",\"grupoId\":").append(grupoId);
        }
        cuerpo.append('}');
        return mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.toString()));
    }

    private long crearPartida(YearMonth mes, String nombre, Categoria categoria,
                              String monto, Long grupoId) throws Exception {
        MvcResult resultado = postPartida(mes, nombre, categoria.getId(), monto, grupoId)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode plan = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return plan.path("partidas").get(plan.path("partidas").size() - 1).path("id").asLong();
    }

    private ResultActions putPartida(YearMonth mes, long id, String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/planes/{mes}/partidas/{id}", mes, id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
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

    private String json(String valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }
}
