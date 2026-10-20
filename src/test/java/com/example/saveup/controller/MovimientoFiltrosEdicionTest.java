package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MetaAhorroRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T4 — Contrato HTTP de filtros (GET /api/movimientos/paginados/me) y de
 * edición/borrado (PUT/DELETE /api/movimientos/{id}).
 *
 * <p>Integración real: repositorios y H2 (perfil test), sin repositorios
 * simulados, para que los filtros de la Specification se ejecuten de verdad.
 * Cada test corre en una transacción que hace rollback, así los datos no se
 * filtran entre casos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovimientoFiltrosEdicionTest {

    private static final String RUT_AUTENTICADO = "11111111-1";
    private static final String RUT_AJENO = "22222222-2";

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

    @Autowired
    private DeudaRepository deudaRepository;

    @Autowired
    private MetaAhorroRepository metaAhorroRepository;

    private String token;
    private Usuario usuario;
    private Usuario ajeno;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        token = jwtService.generateToken(RUT_AUTENTICADO, "test@example.com");
        usuario = usuarioRepository.save(nuevoUsuario(RUT_AUTENTICADO));
        ajeno = usuarioRepository.save(nuevoUsuario(RUT_AJENO));
        categoria = categoriaRepository.save(
                new Categoria(null, "Supermercado", "ic_cart", "#FF0000", TipoPresupuesto.NECESIDAD));
    }

    // ─────────────────────────── Filtros ───────────────────────────

    @Test
    @DisplayName("Sin filtros devuelve todos los movimientos del usuario, más recientes primero")
    void sinFiltros_devuelveTodosOrdenadosPorFechaDesc() throws Exception {
        movimiento(usuario, fecha(2026, 1, 5), TipoMovimiento.INGRESO_GENERAL, "1000.00", "Antiguo", null, null);
        movimiento(usuario, fecha(2026, 1, 10), TipoMovimiento.INGRESO_GENERAL, "2000.00", "Medio", null, null);
        movimiento(usuario, fecha(2026, 1, 20), TipoMovimiento.INGRESO_GENERAL, "3000.00", "Reciente", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.content[0].descripcion").value("Reciente"))
                .andExpect(jsonPath("$.content[2].descripcion").value("Antiguo"));
    }

    @Test
    @DisplayName("Filtro por tipoMovimiento")
    void filtroPorTipoMovimiento() throws Exception {
        movimiento(usuario, fecha(2026, 2, 1), TipoMovimiento.INGRESO_GENERAL, "1000.00", "Ingreso", null, null);
        movimiento(usuario, fecha(2026, 2, 2), TipoMovimiento.GASTO_GENERAL, "-2000.00", "Gasto", null, null);
        movimiento(usuario, fecha(2026, 2, 3), TipoMovimiento.GASTO_GENERAL, "-3000.00", "Gasto 2", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("tipoMovimiento", "GASTO_GENERAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.content[0].tipoMovimiento").value("GASTO_GENERAL"));
    }

    @Test
    @DisplayName("Filtro por categoriaId")
    void filtroPorCategoria() throws Exception {
        movimiento(usuario, fecha(2026, 3, 1), TipoMovimiento.GASTO_GENERAL, "-1000.00", "Con categoria", categoria, null);
        movimiento(usuario, fecha(2026, 3, 2), TipoMovimiento.GASTO_GENERAL, "-2000.00", "Sin categoria", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("categoriaId", categoria.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Con categoria"));
    }

    @Test
    @DisplayName("Filtro por rango de monto (magnitud absoluta)")
    void filtroPorRangoDeMonto() throws Exception {
        movimiento(usuario, fecha(2026, 4, 1), TipoMovimiento.INGRESO_GENERAL, "1000.00", "Chico", null, null);
        movimiento(usuario, fecha(2026, 4, 2), TipoMovimiento.INGRESO_GENERAL, "5000.00", "Medio", null, null);
        movimiento(usuario, fecha(2026, 4, 3), TipoMovimiento.INGRESO_GENERAL, "9000.00", "Grande", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("montoMin", "2000")
                        .param("montoMax", "6000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Medio"));
    }

    @Test
    @DisplayName("El rango de monto usa la magnitud: incluye ingreso +5000 y gasto -5000")
    void filtroPorMonto_usaMagnitud_incluyeIngresoYGasto() throws Exception {
        movimiento(usuario, fecha(2026, 4, 10), TipoMovimiento.INGRESO_GENERAL, "5000.00", "Ingreso 5k", null, null);
        movimiento(usuario, fecha(2026, 4, 11), TipoMovimiento.GASTO_GENERAL, "-5000.00", "Gasto 5k", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("montoMin", "1000")
                        .param("montoMax", "10000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @DisplayName("Filtro por rango de fechas (desde/hasta inclusivos)")
    void filtroPorRangoDeFechas() throws Exception {
        movimiento(usuario, fecha(2026, 1, 5), TipoMovimiento.INGRESO_GENERAL, "1000.00", "D1", null, null);
        movimiento(usuario, fecha(2026, 1, 10), TipoMovimiento.INGRESO_GENERAL, "2000.00", "D2", null, null);
        movimiento(usuario, fecha(2026, 1, 20), TipoMovimiento.INGRESO_GENERAL, "3000.00", "D3", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("desde", "2026-01-05")
                        .param("hasta", "2026-01-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @DisplayName("Filtro por texto q en la descripción, sin distinguir mayúsculas")
    void filtroPorTextoQ_caseInsensitive() throws Exception {
        movimiento(usuario, fecha(2026, 5, 1), TipoMovimiento.GASTO_GENERAL, "-1000.00", "Supermercado Lider", null, null);
        movimiento(usuario, fecha(2026, 5, 2), TipoMovimiento.GASTO_GENERAL, "-2000.00", "Arriendo", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("q", "SUPER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Supermercado Lider"));

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("q", "super")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("El filtro q escapa los comodines % y _ (los busca como texto literal)")
    void filtroPorTextoQ_escapaComodines() throws Exception {
        movimiento(usuario, fecha(2026, 5, 10), TipoMovimiento.GASTO_GENERAL, "-1000.00", "Descuento 100% real", null, null);
        movimiento(usuario, fecha(2026, 5, 11), TipoMovimiento.GASTO_GENERAL, "-2000.00", "Descuento 1000 real", null, null);

        // Sin escape, '100%' casaría también '1000 real'; escapado, solo el literal '100%'.
        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("q", "100%")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Descuento 100% real"));
    }

    @Test
    @DisplayName("Filtros combinados: tipo + q + monto + fecha")
    void filtrosCombinados() throws Exception {
        movimiento(usuario, fecha(2026, 6, 1), TipoMovimiento.GASTO_GENERAL, "-1500.00", "Cafe Starbucks", null, null);
        movimiento(usuario, fecha(2026, 6, 2), TipoMovimiento.GASTO_GENERAL, "-8000.00", "Cafe caro", null, null);
        movimiento(usuario, fecha(2026, 6, 3), TipoMovimiento.INGRESO_GENERAL, "3000.00", "Cafe ingreso", null, null);
        movimiento(usuario, fecha(2026, 6, 20), TipoMovimiento.GASTO_GENERAL, "-1500.00", "Cafe fuera de rango", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("tipoMovimiento", "GASTO_GENERAL")
                        .param("q", "cafe")
                        .param("montoMin", "1000")
                        .param("montoMax", "2000")
                        .param("desde", "2026-06-01")
                        .param("hasta", "2026-06-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Cafe Starbucks"));
    }

    @Test
    @DisplayName("Nunca devuelve movimientos de otro usuario (filtro obligatorio por RUT del token)")
    void noDevuelveMovimientosAjenos() throws Exception {
        movimiento(usuario, fecha(2026, 7, 1), TipoMovimiento.INGRESO_GENERAL, "1000.00", "Mio", null, null);
        movimiento(ajeno, fecha(2026, 7, 2), TipoMovimiento.INGRESO_GENERAL, "9999.00", "Ajeno", null, null);

        mockMvc.perform(get("/api/movimientos/paginados/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].descripcion").value("Mio"));
    }

    // ───────────────────────── Filtros inválidos (400) ─────────────────────────

    @Test
    @DisplayName("desde > hasta → 400 Bad Request")
    void desdeMayorQueHasta_400() throws Exception {
        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("desde", "2026-02-01")
                        .param("hasta", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("montoMin > montoMax → 400 Bad Request")
    void montoMinMayorQueMontoMax_400() throws Exception {
        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("montoMin", "5000")
                        .param("montoMax", "1000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("montoMin o montoMax negativos → 400 Bad Request")
    void montoNegativo_400() throws Exception {
        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("montoMin", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/api/movimientos/paginados/me")
                        .param("montoMax", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─────────────────────────── Edición ───────────────────────────

    @Test
    @DisplayName("Editar un GASTO_GENERAL propio aplica SignoMovimiento y cambia categoría")
    void editarPropio_200() throws Exception {
        Categoria otra = categoriaRepository.save(
                new Categoria(null, "Ocio", "ic_fun", "#00FF00", TipoPresupuesto.DESEO));
        Movimiento gasto = movimiento(usuario, fecha(2026, 8, 1), TipoMovimiento.GASTO_GENERAL,
                "-1000.00", "Vieja", categoria, null);

        mockMvc.perform(put("/api/movimientos/" + gasto.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":2500,\"descripcion\":\"Nueva\",\"categoriaId\":" + otra.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(-2500.00))
                .andExpect(jsonPath("$.descripcion").value("Nueva"))
                .andExpect(jsonPath("$.categoria.id").value(otra.getId()));
    }

    @Test
    @DisplayName("Editar solo la descripción (PUT parcial) → 200 y conserva monto y categoría")
    void editarSoloDescripcion_200() throws Exception {
        Movimiento gasto = movimiento(usuario, fecha(2026, 8, 1), TipoMovimiento.GASTO_GENERAL,
                "-1000.00", "Vieja", categoria, null);

        mockMvc.perform(put("/api/movimientos/" + gasto.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Solo texto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Solo texto"))
                .andExpect(jsonPath("$.monto").value(-1000.00))
                .andExpect(jsonPath("$.categoria.id").value(categoria.getId()));
    }

    @Test
    @DisplayName("Editar un movimiento ajeno → 403 Forbidden (anti-IDOR)")
    void editarAjeno_403() throws Exception {
        Movimiento deOtro = movimiento(ajeno, fecha(2026, 8, 2), TipoMovimiento.GASTO_GENERAL,
                "-1000.00", "Ajeno", null, null);

        mockMvc.perform(put("/api/movimientos/" + deOtro.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":50,\"descripcion\":\"Hack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("Editar un tipo no permitido (ABONO_META) → 409 Conflict")
    void editarTipoNoPermitido_409() throws Exception {
        Movimiento abono = movimiento(usuario, fecha(2026, 8, 3), TipoMovimiento.ABONO_META,
                "-500.00", "Abono", null, null);

        mockMvc.perform(put("/api/movimientos/" + abono.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":500,\"descripcion\":\"Editado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("módulo")));
    }

    @Test
    @DisplayName("Editar un ingreso con abonos de Smart-Split vinculados → 409 Conflict")
    void editarIngresoConSmartSplit_409() throws Exception {
        Movimiento ingreso = movimiento(usuario, fecha(2026, 8, 4), TipoMovimiento.INGRESO_GENERAL,
                "100000.00", "Sueldo", null, null);
        movimiento(usuario, fecha(2026, 8, 4), TipoMovimiento.ABONO_META, "-20000.00", "Abono Auto", null, ingreso);

        mockMvc.perform(put("/api/movimientos/" + ingreso.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":90000,\"descripcion\":\"Corregido\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Smart-Split")));
    }

    @Test
    @DisplayName("Editar un ingreso SIN derivados ni deuda/meta → 200")
    void editarIngresoSimple_200() throws Exception {
        Movimiento ingreso = movimiento(usuario, fecha(2026, 8, 5), TipoMovimiento.INGRESO_GENERAL,
                "1000.00", "Simple", null, null);

        mockMvc.perform(put("/api/movimientos/" + ingreso.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":2000,\"descripcion\":\"Actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(2000.00))
                .andExpect(jsonPath("$.descripcion").value("Actualizado"));
    }

    @Test
    @DisplayName("Editar un movimiento inexistente → 404")
    void editarInexistente_404() throws Exception {
        mockMvc.perform(put("/api/movimientos/999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":50,\"descripcion\":\"Nada\"}"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────── Borrado ───────────────────────────

    @Test
    @DisplayName("Eliminar un movimiento propio → 204 y desaparece")
    void eliminarPropio_204() throws Exception {
        Movimiento gasto = movimiento(usuario, fecha(2026, 9, 1), TipoMovimiento.GASTO_GENERAL,
                "-1000.00", "Borrar", null, null);

        mockMvc.perform(delete("/api/movimientos/" + gasto.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/movimientos/paginados/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("Eliminar un movimiento ajeno → 403 Forbidden (anti-IDOR)")
    void eliminarAjeno_403() throws Exception {
        Movimiento deOtro = movimiento(ajeno, fecha(2026, 9, 2), TipoMovimiento.GASTO_GENERAL,
                "-1000.00", "Ajeno", null, null);

        mockMvc.perform(delete("/api/movimientos/" + deOtro.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Eliminar un tipo no permitido (PAGO_DEUDA) → 409 Conflict")
    void eliminarTipoNoPermitido_409() throws Exception {
        Movimiento pago = movimiento(usuario, fecha(2026, 9, 3), TipoMovimiento.PAGO_DEUDA,
                "-1000.00", "Cuota", null, null);

        mockMvc.perform(delete("/api/movimientos/" + pago.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Eliminar un ingreso con Smart-Split vinculado → 409 Conflict")
    void eliminarIngresoConSmartSplit_409() throws Exception {
        Movimiento ingreso = movimiento(usuario, fecha(2026, 9, 4), TipoMovimiento.INGRESO_GENERAL,
                "100000.00", "Sueldo", null, null);
        movimiento(usuario, fecha(2026, 9, 4), TipoMovimiento.ABONO_META, "-20000.00", "Abono Auto", null, ingreso);

        mockMvc.perform(delete("/api/movimientos/" + ingreso.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Bloquea PUT y DELETE de un GASTO_GENERAL vinculado a una deuda → 409 Conflict")
    void bloquea_edicionYBorrado_conDeuda() throws Exception {
        Deuda deuda = nuevaDeuda();
        Movimiento gasto = movimiento(usuario, fecha(2026, 10, 1), TipoMovimiento.GASTO_GENERAL,
                "-1000.00", "Con deuda", null, null);
        gasto.setDeuda(deuda);
        movimientoRepository.save(gasto);

        mockMvc.perform(put("/api/movimientos/" + gasto.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":50,\"descripcion\":\"Editado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(delete("/api/movimientos/" + gasto.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Bloquea PUT y DELETE de un INGRESO_GENERAL vinculado a una meta → 409 Conflict")
    void bloquea_edicionYBorrado_conMeta() throws Exception {
        MetaAhorro meta = nuevaMeta();
        Movimiento ingreso = movimiento(usuario, fecha(2026, 10, 2), TipoMovimiento.INGRESO_GENERAL,
                "1000.00", "Con meta", null, null);
        ingreso.setMetaAhorro(meta);
        movimientoRepository.save(ingreso);

        mockMvc.perform(put("/api/movimientos/" + ingreso.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":2000,\"descripcion\":\"Editado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(delete("/api/movimientos/" + ingreso.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private Usuario nuevoUsuario(String rut) {
        Usuario u = new Usuario();
        u.setRut(rut);
        u.setNombre("Usuario");
        u.setApellido("Test");
        u.setEmail(rut + "@test.cl");
        u.setContrasena("hash");
        return u;
    }

    private Deuda nuevaDeuda() {
        Deuda d = new Deuda();
        d.setUsuario(usuario);
        d.setNombre("Deuda test");
        d.setMontoTotal(new BigDecimal("100000.00"));
        d.setCantidadCuotas(10);
        d.setEstado(EstadoDeuda.PENDIENTE);
        d.setTipoDeuda(TipoDeuda.CREDITO_CONSUMO);
        d.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        d.setMontoCapital(new BigDecimal("100000.00"));
        d.setTasaMensual(BigDecimal.ZERO);
        d.setValorCuota(new BigDecimal("10000.00"));
        d.setGastosIniciales(BigDecimal.ZERO);
        d.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        d.setCuotasPagadasPrevias(0);
        d.setMontoPagadoPrevio(BigDecimal.ZERO);
        d.setCargaAnualEquivalente(BigDecimal.ZERO);
        d.setCostoTotalCredito(new BigDecimal("100000.00"));
        return deudaRepository.save(d);
    }

    private MetaAhorro nuevaMeta() {
        MetaAhorro m = new MetaAhorro();
        m.setNombre("Meta test");
        m.setMontoActual(BigDecimal.ZERO);
        m.setUsuario(usuario);
        return metaAhorroRepository.save(m);
    }

    private Movimiento movimiento(Usuario dueno, Date fecha, TipoMovimiento tipo, String monto,
                                  String descripcion, Categoria cat, Movimiento origen) {
        Movimiento m = new Movimiento();
        m.setUsuario(dueno);
        m.setFecha(fecha);
        m.setTipoMovimiento(tipo);
        m.setMonto(new BigDecimal(monto));
        m.setDescripcion(descripcion);
        m.setCategoria(cat);
        m.setMovimientoOrigen(origen);
        return movimientoRepository.save(m);
    }

    private Date fecha(int anio, int mes, int dia) {
        return Date.from(LocalDate.of(anio, mes, dia).atTime(12, 0)
                .atZone(ZoneId.of("America/Santiago")).toInstant());
    }
}
