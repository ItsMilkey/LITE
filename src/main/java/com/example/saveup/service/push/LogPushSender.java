package com.example.saveup.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementación por defecto de {@link PushSender} (T9.6) cuando la feature
 * está ACTIVA y {@code app.push.log-only=true}: no habla con FCM y registra
 * el envío en el log. Es lo que corre en dev, en tests que activan la bandera
 * y en un despliegue con credenciales pendientes.
 *
 * <p>Con {@code app.push.enabled=false} (por defecto del proyecto) la
 * implementación activa es {@link NoOpPushSender}: la función está PENDIENTE
 * de activación y no debe haber trazas de "envío logueado" que no ocurrirán.
 *
 * <p>El token se loguea enmascarado: el log no debe servir para notificar a los
 * dispositivos de nadie.
 */
@Service
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
@Conditional(CondicionesPush.LogOnly.class)
public class LogPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(LogPushSender.class);

    @Override
    public ResultadoEnvio enviar(String token, String titulo, String cuerpo, Map<String, String> datos) {
        LOG.info("[push log-only] dispositivo={} | titulo={} | cuerpo={} | datos={}",
                TokensPush.enmascarar(token), titulo, cuerpo, datos);
        return ResultadoEnvio.ok();
    }
}
