package com.example.saveup;

import com.example.saveup.service.push.NoOpPushSender;
import com.example.saveup.service.push.PushSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Punto de partida del proyecto (T9.3/T9.6): el contexto carga con las banderas
 * maestras de correo y push DESACTIVADAS — sin credenciales de SMTP ni de FCM,
 * sin beans que fallen — porque el envío real está PENDIENTE de activación
 * (requiere proveedor externo). Con la bandera de push desactivada, la
 * implementación inyectada es el no-op.
 */
@SpringBootTest
class SaveupLiteApplicationTests {

    @Autowired
    private PushSender pushSender;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("Con app.mail.enabled/app.push.enabled en false, no hay credenciales requeridas ni beans fallantes")
    void contextoSinProveedoresExternos_arrancaConNoOp() {
        assertInstanceOf(NoOpPushSender.class, pushSender);
    }
}
