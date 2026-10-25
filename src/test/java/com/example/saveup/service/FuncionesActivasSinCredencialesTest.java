package com.example.saveup.service;

import com.example.saveup.service.push.FcmPushSender;
import com.example.saveup.service.push.PushSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Con las banderas ACTIVAS pero sin credenciales: la aplicación arranca igual
 * (ningún bean falla ni expone credenciales ausentes como error de arranque) y
 * cada función falla por corrida con un error claro en el log. Es el estado
 * que quedará si alguien activa <code>APP_MAIL_ENABLED</code>/<code>APP_PUSH_ENABLED</code>
 * antes de configurar el proveedor.
 */
@SpringBootTest(properties = {
        "app.mail.enabled = true",
        "app.mail.log-only = false",
        "app.push.enabled = true",
        "app.push.log-only = false"
})
class FuncionesActivasSinCredencialesTest {

    @Autowired
    private PushSender pushSender;

    @Autowired
    private CorreoService correoService;

    @Test
    @DisplayName("Con banderas activas sin smtp ni Firebase, el contexto de todos modos arranca")
    void contextLoads() {
        assertInstanceOf(FcmPushSender.class, pushSender);
    }

    @Test
    @DisplayName("Push activo sin FIREBASE_CREDENTIALS: el envío falla con error claro (no lanza)")
    void pushActivo_sinCredenciales_envioFalla() {
        PushSender.ResultadoEnvio resultado =
                pushSender.enviar("token-inutilizable", "SaveUp Lite", "cuerpo", Map.of());

        assertFalse(resultado.enviado());
        assertFalse(resultado.tokenInvalido());
    }

    @Test
    @DisplayName("Correo activo sin spring.mail.host: falla ruidoso con mensaje claro")
    void mailActivo_sinSmtp_lanzaErrorClaro() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> correoService.enviar("alguien@example.com", "Asunto", "Cuerpo"));

        assertTrue(e.getMessage().contains("JavaMailSender"),
                "El mensaje debe explicar la credencial faltante: " + e.getMessage());
    }
}
