package com.example.saveup.controller;

import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T8a — Contrato de datos para clientes móviles: las fechas y los montos viajan
 * sin ambigüedad y los límites de mes se calculan en America/Santiago, no en la
 * zona del servidor. Integración real con H2 (rollback por test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class T8aContratoDatosTest {

    private static final String RUT = "11111111-1";
    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        token = jwtService.generateToken(RUT, "test@example.com");
        usuario = usuarioRepository.save(nuevoUsuario(RUT));
    }

    @Test
    @DisplayName("31-oct 23:30 en Santiago cuenta en octubre; 1-nov 00:30 cuenta en noviembre")
    void limitesDeMesEnZonaDelNegocio() throws Exception {
        movimiento(instante(2026, 10, 31, 23, 30), TipoMovimiento.INGRESO_GENERAL, "1000.00");
        movimiento(instante(2026, 11, 1, 0, 30), TipoMovimiento.INGRESO_GENERAL, "500.00");

        mockMvc.perform(get("/api/v1/presupuestos/ejecucion/me")
                        .param("month", "10").param("year", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIngresos").value(1000.00));

        mockMvc.perform(get("/api/v1/presupuestos/ejecucion/me")
                        .param("month", "11").param("year", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIngresos").value(500.00));

        mockMvc.perform(get("/api/v1/presupuestos/historial/me")
                        .param("desde", "2026-10").param("hasta", "2026-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].anio").value(2026))
                .andExpect(jsonPath("$[0].mes").value(10))
                .andExpect(jsonPath("$[0].ejecucion.totalIngresos").value(1000.00));
    }

    @Test
    @DisplayName("Los filtros desde/hasta se interpretan en hora de Santiago e incluyen el día completo")
    void filtrosInclusivosEnZonaDelNegocio() throws Exception {
        movimiento(instante(2026, 10, 31, 23, 30), TipoMovimiento.GASTO_GENERAL, "-1000.00");

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("desde", "2026-10-31").param("hasta", "2026-10-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("desde", "2026-11-01").param("hasta", "2026-11-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("desde", "2026-10-31").param("hasta", "2026-11-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("Un instante se serializa en UTC terminando en Z (no +00:00)")
    void instanteSerializadoEnUtcConZ() throws Exception {
        movimiento(instante(2026, 10, 31, 23, 30), TipoMovimiento.GASTO_GENERAL, "-1000.00");

        mockMvc.perform(get("/api/v1/movimientos/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fecha").value("2026-11-01T02:30:00.000Z"))
                .andExpect(jsonPath("$[0].fecha", endsWith("Z")))
                .andExpect(content().string(not(containsString("+00:00"))));
    }

    @Test
    @DisplayName("Una fecha de calendario se serializa como yyyy-MM-dd y no lleva zona")
    void fechaDeCalendarioComoYyyyMmDd() throws Exception {
        mockMvc.perform(post("/api/v1/metas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Viaje","montoObjetivo":1000.00,"fechaLimite":"2027-11-07"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaLimite").value("2027-11-07"));

        mockMvc.perform(get("/api/v1/metas/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fechaLimite").value("2027-11-07"));
    }

    @Test
    @DisplayName("Monto de 13 enteros + 2 decimales se acepta; 14 enteros → 400")
    void limiteDeEnterosEnMontos() throws Exception {
        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":1234567890123.45,"descripcion":"Máximo permitido","tipoMovimiento":"GASTO_GENERAL"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":12345678901234.56,"descripcion":"Un entero de más","tipoMovimiento":"GASTO_GENERAL"}
                                """))
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

    private void movimiento(Date fecha, TipoMovimiento tipo, String monto) {
        Movimiento m = new Movimiento();
        m.setUsuario(usuario);
        m.setFecha(fecha);
        m.setTipoMovimiento(tipo);
        m.setMonto(new BigDecimal(monto));
        m.setDescripcion("test T8a");
        movimientoRepository.save(m);
    }

    /** Instante construido SIEMPRE en America/Santiago, independiente de la JVM. */
    private Date instante(int anio, int mes, int dia, int hora, int minuto) {
        return Date.from(ZonedDateTime.of(anio, mes, dia, hora, minuto, 0, 0, SANTIAGO).toInstant());
    }
}
