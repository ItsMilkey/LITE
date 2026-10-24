package com.example.saveup.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementación por defecto de {@link PushSender} (T9.6): no habla con FCM,
 * solo registra el envío en el log. Se activa con
 * {@code app.push.log-only=true} (el valor por defecto), de modo que el job,
 * los tests y un despliegue sin credenciales de Firebase funcionan igual.
 *
 * <p>El token se loguea enmascarado: el log no debe servir para notificar a los
 * dispositivos de nadie.
 */
@Service
@ConditionalOnProperty(name = "app.push.log-only", havingValue = "true", matchIfMissing = true)
public class LogPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(LogPushSender.class);

    @Override
    public ResultadoEnvio enviar(String token, String titulo, String cuerpo, Map<String, String> datos) {
        LOG.info("[push log-only] dispositivo={} | titulo={} | cuerpo={} | datos={}",
                TokensPush.enmascarar(token), titulo, cuerpo, datos);
        return ResultadoEnvio.ok();
    }
}
