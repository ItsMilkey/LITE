package com.example.saveup.controller;

import com.example.saveup.model.DispositivoPush;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.Plataforma;
import com.example.saveup.repository.DispositivoPushRepository;
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

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T9.6 — Contrato HTTP del registro de dispositivos y del trigger de cron.
 * Integración real con H2; cada test corre en una transacción con rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacionControllerTest {

    private static final String RUT = "11111111-1";
    private static final String RUT_OTRO = "22222222-2";
    private static final String TOKEN = "token-de-prueba-1234567890";
    private static final String SECRETO_CRON = "secreto-de-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DispositivoPushRepository dispositivoRepository;

    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        token = jwtService.generateToken(RUT, "test@example.com");
        usuario = usuarioRepository.save(nuevoUsuario(RUT, "test@example.com"));
    }

    @Test
    @DisplayName("POST /dispositivos crea el dispositivo y devuelve el token enmascarado")
    void registrar_creaYEnmascara() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plataforma").value("ANDROID"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.tokenEnmascarado").value("token-de…7890"));

        assertEquals(1, dispositivoRepository.count());
    }

    @Test
    @DisplayName("POST /dispositivos con el mismo token es idempotente (200, sin duplicar)")
    void registrar_mismoToken_esIdempotente() throws Exception {
        String cuerpo = "{\"token\":\"" + TOKEN + "\",\"plataforma\":\"ANDROID\"}";

        mockMvc.perform(post("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        assertEquals(1, dispositivoRepository.count());
    }

    @Test
    @DisplayName("Un token que era de otro usuario se reasigna al usuario autenticado")
    void registrar_tokenDeOtroUsuario_seReasigna() throws Exception {
        Usuario otro = usuarioRepository.save(nuevoUsuario(RUT_OTRO, "otro@example.com"));
        dispositivoRepository.save(dispositivo(otro, TOKEN));

        mockMvc.perform(post("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"plataforma\":\"IOS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plataforma").value("IOS"));

        assertEquals(1, dispositivoRepository.count());
        assertEquals(RUT, dispositivoRepository.findByToken(TOKEN).orElseThrow().getUsuario().getRut());
    }

    @Test
    @DisplayName("GET /dispositivos lista solo los activos del usuario")
    void listar_soloActivosDelUsuario() throws Exception {
        dispositivoRepository.save(dispositivo(usuario, TOKEN));

        DispositivoPush inactivo = dispositivo(usuario, "token-inactivo-999");
        inactivo.setActivo(false);
        dispositivoRepository.save(inactivo);

        mockMvc.perform(get("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tokenEnmascarado").value("token-de…7890"));
    }

    @Test
    @DisplayName("DELETE /dispositivos es idempotente y borra solo el token propio")
    void eliminar_esIdempotente() throws Exception {
        dispositivoRepository.save(dispositivo(usuario, TOKEN));

        mockMvc.perform(delete("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        assertEquals(0, dispositivoRepository.count());
    }

    @Test
    @DisplayName("DELETE con token de otro usuario no borra nada")
    void eliminar_tokenAjeno_noBorra() throws Exception {
        Usuario otro = usuarioRepository.save(nuevoUsuario(RUT_OTRO, "otro@example.com"));
        dispositivoRepository.save(dispositivo(otro, TOKEN));

        mockMvc.perform(delete("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        assertEquals(1, dispositivoRepository.count());
    }

    @Test
    @DisplayName("Plataforma inválida → 400 VALIDACION_FALLIDA")
    void registrar_plataformaInvalida_400() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/dispositivos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"plataforma\":\"WINDOWS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("Sin JWT → 401")
    void registrar_sinJwt_401() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("Cron: con secreto válido ejecuta y responde 202")
    void cron_secretoValido_202() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/recordatorios/ejecutar")
                        .header("X-Cron-Secret", SECRETO_CRON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.evaluados").exists())
                .andExpect(jsonPath("$.enviados").exists());
    }

    @Test
    @DisplayName("Cron: secreto inválido o ausente → 403")
    void cron_secretoInvalido_403() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/recordatorios/ejecutar")
                        .header("X-Cron-Secret", "otro-secreto"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        mockMvc.perform(post("/api/v1/notificaciones/recordatorios/ejecutar"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private Usuario nuevoUsuario(String rut, String email) {
        Usuario nuevo = new Usuario();
        nuevo.setRut(rut);
        nuevo.setNombre("Usuario");
        nuevo.setApellido("Test");
        nuevo.setEmail(email);
        nuevo.setContrasena("hash");
        return nuevo;
    }

    private DispositivoPush dispositivo(Usuario dueno, String tokenDispositivo) {
        DispositivoPush dispositivo = new DispositivoPush();
        dispositivo.setUsuario(dueno);
        dispositivo.setToken(tokenDispositivo);
        dispositivo.setPlataforma(Plataforma.ANDROID);
        dispositivo.setActivo(true);
        dispositivo.setFechaRegistro(new Date());
        dispositivo.setFechaUltimoUso(new Date());
        return dispositivo;
    }
}
