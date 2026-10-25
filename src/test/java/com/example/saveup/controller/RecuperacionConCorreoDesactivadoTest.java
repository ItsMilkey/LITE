package com.example.saveup.controller;

import com.example.saveup.repository.TokenVerificacionRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.service.CorreoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La bandera maestra {@code app.mail.enabled=false} (estado del proyecto: el
 * envío está PENDIENTE de activación, requiere proveedor externo): la
 * recuperación de contraseña no genera ni envía nada, deja un log WARN y
 * responde exactamente igual que con el correo habilitado (anti-enumeración
 * intacta). El resto del flujo (con bandera activa) vive en AuthControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecuperacionConCorreoDesactivadoTest {

    private static final String EMAIL = "test@example.com";
    private static final String MENSAJE_GENERICO =
            "Si el correo está registrado, enviamos las instrucciones para restablecer la contraseña.";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private TokenVerificacionRepository tokenVerificacionRepository;

    @MockBean
    private CorreoService correoService;

    @Test
    @DisplayName("POST /auth/recuperar-contrasena desactivado → 200 con el mismo mensaje genérico y sin tocar BD ni correo")
    void recuperarConCorreoDesactivado_respondeIgualYNoHaceNada() throws Exception {
        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MENSAJE_GENERICO));

        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MENSAJE_GENERICO));

        verifyNoInteractions(usuarioRepository, tokenVerificacionRepository, correoService);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("Con la bandera desactivada queda un log WARN de correo deshabilitado")
    void recuperarConCorreoDesactivado_dejaLogWarn(CapturedOutput salida) throws Exception {
        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk());

        assertTrue(salida.getAll().contains("app.mail.enabled=false"),
                "Debe quedar el WARN de que el correo está deshabilitado");
    }
}
