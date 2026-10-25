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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T5 — Contrato HTTP de los endpoints de presupuesto por categoría e historial.
 * Integración real con repositorios y H2 (misma aproximación que T4): cada test
 * corre en una transacción con rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PresupuestoCategoriaControllerTest {

    private static final String RUT = "11111111-1";

    @Autowired
    private MockMvc mockMvc;

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
    private Categoria comida;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT));
        comida = categoriaRepository.save(
                new Categoria(null, "Comida T5", "ic_fastfood", "#FFC107", TipoPresupuesto.NECESIDAD));
    }

    @Test
    @DisplayName("PUT guarda los límites y GET los devuelve")
    void putYGetLimites() throws Exception {
        mockMvc.perform(put("/api/v1/presupuestos/categorias/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\":[{\"categoriaId\":" + comida.getId() + ",\"limiteMensual\":150000.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoriaId").value(comida.getId()))
                .andExpect(jsonPath("$[0].limiteMensual").value(150000.00));

        mockMvc.perform(get("/api/v1/presupuestos/categorias/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("Comida T5"));
    }

    @Test
    @DisplayName("PUT con límite ≤ 0 → 400 Bad Request (Bean Validation)")
    void putLimiteNoPositivo_400() throws Exception {
        mockMvc.perform(put("/api/v1/presupuestos/categorias/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\":[{\"categoriaId\":" + comida.getId() + ",\"limiteMensual\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con límite negativo → 400 Bad Request")
    void putLimiteNegativo_400() throws Exception {
        mockMvc.perform(put("/api/v1/presupuestos/categorias/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\":[{\"categoriaId\":" + comida.getId() + ",\"limiteMensual\":-5}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con categoriaId nulo → 400 Bad Request (Bean Validation)")
    void putCategoriaIdNulo_400() throws Exception {
        mockMvc.perform(put("/api/v1/presupuestos/categorias/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\":[{\"limiteMensual\":1000}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con categoría inexistente → 400 Bad Request")
    void putCategoriaInexistente_400() throws Exception {
        mockMvc.perform(put("/api/v1/presupuestos/categorias/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\":[{\"categoriaId\":999999,\"limiteMensual\":1000}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ejecución por categoría: 80% del límite → estado ALERTA")
    void ejecucionCategoria_alerta() throws Exception {
        mockMvc.perform(put("/api/v1/presupuestos/categorias/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\":[{\"categoriaId\":" + comida.getId() + ",\"limiteMensual\":100.00}]}"))
                .andExpect(status().isOk());

        movimiento(TipoMovimiento.GASTO_GENERAL, "-50.00", comida, fecha(2026, 5, 10));
        movimiento(TipoMovimiento.PAGO_DEUDA, "-30.00", comida, fecha(2026, 5, 20));
        movimiento(TipoMovimiento.GASTO_GENERAL, "-10.00", comida, fecha(2026, 6, 1));

        mockMvc.perform(get("/api/v1/presupuestos/categorias/ejecucion/me")
                        .param("month", "5").param("year", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gastado").value(80.00))
                .andExpect(jsonPath("$[0].porcentajeUsado").value(80.00))
                .andExpect(jsonPath("$[0].estado").value("ALERTA"));
    }

    @Test
    @DisplayName("Historial con rango mayor a 24 meses → 400 Bad Request")
    void historial_masDe24Meses_400() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos/historial/me")
                        .param("desde", "2024-01").param("hasta", "2026-02")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("Historial de un mes sin ingresos → 200 con ceros")
    void historial_mesSinIngresos_ceros() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos/historial/me")
                        .param("desde", "2026-03").param("hasta", "2026-03")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].anio").value(2026))
                .andExpect(jsonPath("$[0].mes").value(3))
                .andExpect(jsonPath("$[0].ejecucion.totalIngresos").value(0));
    }

    @Test
    @DisplayName("POST /me con suma de porcentajes ≠ 100 → 400 Bad Request")
    void postConfiguracion_sumaNo100_400() throws Exception {
        mockMvc.perform(post("/api/v1/presupuestos/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"porcentajeNecesidades\":50.00,\"porcentajeDeseos\":30.00,\"porcentajeAhorro\":21.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    @DisplayName("POST /me con suma de porcentajes = 100 → 200 y devuelve el DTO")
    void postConfiguracion_suma100_ok() throws Exception {
        mockMvc.perform(post("/api/v1/presupuestos/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"porcentajeNecesidades\":50.00,\"porcentajeDeseos\":30.00,\"porcentajeAhorro\":20.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentajeAhorro").value(20.00))
                .andExpect(jsonPath("$.usuarioRut").value(RUT));
    }

    @Test
    @DisplayName("POST /me con un porcentaje negativo → 400 Bad Request")
    void postConfiguracion_negativo_400() throws Exception {
        mockMvc.perform(post("/api/v1/presupuestos/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"porcentajeNecesidades\":120.00,\"porcentajeDeseos\":10.00,\"porcentajeAhorro\":-30.00}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private Usuario nuevoUsuario(String rut) {
        Usuario u = new Usuario();
        u.setRut(rut);
        u.setNombre("Usuario");
        u.setApellido("Test");
        u.setEmail(rut + "@test.cl");
        u.setContrasena("hash");
        return u;
    }

    private void movimiento(TipoMovimiento tipo, String monto, Categoria categoria, Date fecha) {
        Movimiento m = new Movimiento();
        m.setUsuario(usuario);
        m.setFecha(fecha);
        m.setTipoMovimiento(tipo);
        m.setMonto(new BigDecimal(monto));
        m.setDescripcion("test");
        m.setCategoria(categoria);
        movimientoRepository.save(m);
    }

    private Date fecha(int anio, int mes, int dia) {
        return Date.from(LocalDate.of(anio, mes, dia).atTime(12, 0)
                .atZone(ZoneId.of("America/Santiago")).toInstant());
    }
}
