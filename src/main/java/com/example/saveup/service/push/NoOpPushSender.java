package com.example.saveup.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementación no-op de {@link PushSender} (T9.6), activa con
 *{@code app.push.enabled=false} (el valor por defecto del proyecto: la
 * función está PENDIENTE de activación porque requiere Firebase). No habla
 * con FCM, no loguea envíos y la app arranca sin ninguna credencial. El
 * registro y la baja de tokens de dispositivo siguen funcionando.
 *
 * <p>No es el único no-op: {@code LogPushSender} (log-only) también evita
 * FCM, pero actúa cuando la bandera maestra está activa y aún no se quiere
 * hablar con el proveedor.
 */
@Service
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpPushSender.class);

    @Override
    public ResultadoEnvio enviar(String token, String titulo, String cuerpo, Map<String, String> datos) {
        // Nunca se invoca con la bandera desactivada (el job es no-op), pero
        // si algo lo llama igual, no debe simular un envío que no ocurrió.
        LOG.warn("Push deshabilitado (app.push.enabled=false): envío a {} ignorado",
                TokensPush.enmascarar(token));
        return ResultadoEnvio.fallo();
    }
}
