package com.example.saveup.service.push;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condiciones de arranque del push (T9.6) ligadas a la bandera maestra
 * {@code app.push.enabled=false} (valor por defecto: la función está
 * PENDIENTE de activación porque el envío real requiere Firebase, proveedor
 * externo). Complementan los {@code @ConditionalOnProperty} de cada
 * {@link PushSender} para que la selección sea excluyente: exactamente un
 * bean para cada combinación de {@code app.push.enabled} /
 * {@code app.push.log-only}.
 */
public final class CondicionesPush {

    private CondicionesPush() {
    }

    /** {@code app.push.log-only} vacía u {@code true}: modo log, sin hablar con FCM. */
    public static class LogOnly implements Condition {

        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata meta) {
            return !"false".equals(contexto.getEnvironment().getProperty("app.push.log-only"));
        }
    }

    /** {@code app.push.enabled=true}: la función fue activada en la configuración. */
    public static class PushHabilitado implements Condition {

        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata meta) {
            return "true".equals(contexto.getEnvironment().getProperty("app.push.enabled"));
        }
    }
}
