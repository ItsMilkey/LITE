package com.example.saveup.controller;

import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.MetaAhorroRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F11 — Asignaciones del ahorro: lectura y validación de suma 100.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PresupuestoAsignacionesControllerTest {

    private static final String RUT = "11111111-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MetaAhorroRepository metaAhorroRepository;

    private String token;
    private Usuario usuario;
    private MetaAhorro viaje;
    private MetaAhorro notebook;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "test@example.com"));
        viaje = meta(usuario, "Viaje");
        notebook = meta(usuario, "Notebook");
    }

    @Test
    @DisplayName("GET sin configuración devuelve 200 []")
    void getSinConfiguracion_vacio() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos/asignaciones/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST válido y GET devuelve meta, nombre y porcentaje")
    void postValido_getDevuelveAsignaciones() throws Exception {
        postConfig(asignaciones(viaje, "60.00", notebook, "40.00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/presupuestos/asignaciones/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].metaId").value(containsInAnyOrder(
                        viaje.getId().intValue(), notebook.getId().intValue())))
                .andExpect(jsonPath("$[*].metaNombre").value(containsInAnyOrder(
                        "Viaje", "Notebook")))
                .andExpect(jsonPath("$[*].porcentaje").value(containsInAnyOrder(
                        60.0, 40.0)));
    }

    @Test
    @DisplayName("Suma distinta de 100 → 400 DATOS_INVALIDOS con detail")
    void sumaNo100_400() throws Exception {
        postConfig(asignaciones(viaje, "60.00", notebook, "30.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", containsString("100.00")));
    }

    @Test
    @DisplayName("Meta repetida → 400 DATOS_INVALIDOS")
    void metaRepetida_400() throws Exception {
        postConfig(asignaciones(viaje, "60.00", viaje, "40.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", containsString("repetida")));
    }

    @Test
    @DisplayName("Meta de otro usuario → 400 DATOS_INVALIDOS")
    void metaAjena_400() throws Exception {
        Usuario otro = usuarioRepository.save(nuevoUsuario("22222222-2", "otro@example.com"));
        MetaAhorro ajena = meta(otro, "Ajena");

        postConfig(asignaciones(ajena, "100.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", containsString("no existe o no pertenece")));
    }

    @Test
    @DisplayName("Porcentaje cero → 400 DATOS_INVALIDOS")
    void porcentajeCero_400() throws Exception {
        postConfig(asignaciones(viaje, "0.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", containsString("mayor a 0")));
    }

    @Test
    @DisplayName("Porcentaje con más de 2 decimales → 400 DATOS_INVALIDOS")
    void porcentajeEscalaInvalida_400() throws Exception {
        postConfig(asignaciones(viaje, "33.333", notebook, "66.667"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail", containsString("2 decimales")));
    }

    @Test
    @DisplayName("Suma exacta con decimales (33.33+33.33+33.34) → 200")
    void sumaExactaConDecimales_ok() throws Exception {
        MetaAhorro tercero = meta(usuario, "Tercero");

        postConfig(asignaciones(
                viaje, "33.33",
                notebook, "33.33",
                tercero, "33.34"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/presupuestos/asignaciones/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Lista vacía limpia las asignaciones")
    void listaVacia_limpia() throws Exception {
        postConfig(asignaciones(viaje, "100.00")).andExpect(status().isOk());
        postConfig("[]").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/presupuestos/asignaciones/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private ResultActions postConfig(String asignacionesJson) throws Exception {
        String body = "{\"porcentajeNecesidades\":50.00,\"porcentajeDeseos\":30.00,"
                + "\"porcentajeAhorro\":20.00,\"asignaciones\":" + asignacionesJson + "}";
        return mockMvc.perform(post("/api/v1/presupuestos/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String asignaciones(Object... pares) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < pares.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            MetaAhorro meta = (MetaAhorro) pares[i];
            String porcentaje = (String) pares[i + 1];
            json.append("{\"metaId\":").append(meta.getId())
                    .append(",\"porcentaje\":").append(porcentaje).append('}');
        }
        return json.append(']').toString();
    }

    private Usuario nuevoUsuario(String rut, String email) {
        Usuario u = new Usuario();
        u.setRut(rut);
        u.setNombre("Usuario");
        u.setApellido("Test");
        u.setEmail(email);
        u.setContrasena("hash");
        return u;
    }

    private MetaAhorro meta(Usuario dueno, String nombre) {
        MetaAhorro meta = new MetaAhorro();
        meta.setUsuario(dueno);
        meta.setNombre(nombre);
        meta.setMontoActual(BigDecimal.ZERO);
        return metaAhorroRepository.save(meta);
    }
}
