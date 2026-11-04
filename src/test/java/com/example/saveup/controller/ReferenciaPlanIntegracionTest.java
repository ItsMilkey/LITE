package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10d (sesión 2) — integración real (H2, MockMvc y repositorios) de las
 * referencias históricas del plan: D47 (parámetro incluirReferencias) y D48
 * (usuario cuyo primer movimiento es del mismo mes).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReferenciaPlanIntegracionTest {

    private static final String RUT = "11111111-1";
    private static final YearMonth MES = YearMonth.of(2026, 11);

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

    private String token;
    private Usuario usuario;
    private Categoria alimentacion;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "Ana", "Pérez"));
        alimentacion = categoriaRepository.findByCodigo("ALIMENTACION_BASE")
                .orElseGet(() -> categoriaRepository.save(new Categoria(null, "ALIMENTACION_BASE",
                        "Alimentación Base", null, null, TipoPresupuesto.NECESIDAD)));
    }

    @Test
    @DisplayName("D47: incluirReferencias=true agrega la referencia; sin el parámetro no aparece")
    void d47_parametroIncluirReferencias() throws Exception {
        crearPartida(MES, "Supermercado", "250000.00");
        gasto(LocalDate.of(2026, 8, 10), "-100000.00");
        gasto(LocalDate.of(2026, 9, 10), "-120000.00");
        gasto(LocalDate.of(2026, 10, 10), "-80000.00");

        MvcResult conReferencias = mockMvc.perform(get("/api/v1/planes/{mes}", MES)
                        .param("incluirReferencias", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porCategoria.length()").value(1))
                .andExpect(jsonPath("$.porCategoria[0].codigo").value("ALIMENTACION_BASE"))
                .andExpect(jsonPath("$.porCategoria[0].referencia.promedio3Meses").value(100000.00))
                .andExpect(jsonPath("$.porCategoria[0].referencia.mesAnterior").value(80000.00))
                .andReturn();
        assertTrue(objectMapper.readTree(conReferencias.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8))
                .path("porCategoria").get(0).has("referencia"));

        MvcResult sinReferencias = mockMvc.perform(get("/api/v1/planes/{mes}", MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porCategoria.length()").value(1))
                .andReturn();
        JsonNode categoria = objectMapper.readTree(sinReferencias.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8))
                .path("porCategoria").get(0);
        assertTrue(!categoria.has("referencia"), "Sin el parámetro no debe incluirse la referencia");
    }

    @Test
    @DisplayName("D48: un usuario cuyo primer movimiento es este mes no recibe referencia")
    void d48_primerMovimientoEsteMes() throws Exception {
        YearMonth mesActual = FechasMovimiento.mesActual();
        gasto(mesActual.atDay(1), "-10000.00");

        MvcResult respuesta = mockMvc.perform(get("/api/v1/planes/{mes}", mesActual)
                        .param("incluirReferencias", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porCategoria.length()").value(1))
                .andExpect(jsonPath("$.porCategoria[0].real").value(10000.00))
                .andReturn();

        JsonNode categoria = objectMapper.readTree(respuesta.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8))
                .path("porCategoria").get(0);
        assertTrue(!categoria.has("referencia") || categoria.path("referencia").isNull(),
                "Sin meses cerrados previos la referencia debe ser nula");
    }

    private void crearPartida(YearMonth mes, String nombre, String monto) throws Exception {
        mockMvc.perform(post("/api/v1/planes/{mes}/partidas", mes)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"categoriaId\":"
                                + alimentacion.getId() + ",\"montoPlanificado\":" + monto + "}"))
                .andExpect(status().isCreated());
    }

    private void gasto(LocalDate fecha, String monto) {
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuario);
        movimiento.setFecha(Date.from(fecha.atTime(12, 0)
                .atZone(ZoneId.of("America/Santiago")).toInstant()));
        movimiento.setTipoMovimiento(TipoMovimiento.GASTO_GENERAL);
        movimiento.setMonto(new BigDecimal(monto));
        movimiento.setCategoria(alimentacion);
        movimiento.setDescripcion("Gasto de referencia");
        movimientoRepository.save(movimiento);
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
