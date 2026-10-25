package com.example.saveup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos transaccionales (T9.3), hoy solo la recuperación de
 * contraseña. BANDERA MAESTRA {@code app.mail.enabled=false} (por defecto):
 * no genera ni envía nada, deja un log WARN y devuelve; la app puede arrancar
 * sin ninguna credencial ni URL de correo. Es el estado actual del proyecto:
 * el envío real está PENDIENTE de activación (requiere proveedor externo).
 * <p>
 * Con {@code enabled=true} y {@code log-only=true}: loguea el correo completo
 * en la consola, incluido el token de un solo uso; no necesita servidor SMTP
 * ni credenciales (ideal para dev, test y despliegues iniciales).
 * <p>
 * Con {@code log-only=false} usa {@link JavaMailSender} (spring-boot-starter-mail):
 * si no está configurado, falla de forma ruidosa (ERROR_INTERNO) en vez de
 * simular un envío que nunca ocurrió (la app sigue arrancando).
 */
@Service
public class CorreoService {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoService.class);

    private final ObjectProvider<JavaMailSender> proveedorMail;

    @Value("${app.mail.enabled:false}")
    private boolean habilitado;

    @Value("${app.mail.log-only:true}")
    private boolean logOnly;

    public CorreoService(ObjectProvider<JavaMailSender> proveedorMail) {
        this.proveedorMail = proveedorMail;
    }

    /**
     * Envía (o loguea) un correo de texto simple. Sin HTML: el contenido se
     * compone en el servicio llamador. Con la bandera desactivada es un
     * no-op total (log WARN).
     */
    public void enviar(String destino, String asunto, String cuerpo) {
        if (!habilitado) {
            LOG.warn("Correo deshabilitado (app.mail.enabled=false): no se genera ni envía nada "
                            + "para '{}' (asunto={}). La función está PENDIENTE de activación.",
                    destino, asunto);
            return;
        }

        if (logOnly) {
            LOG.info("[correo log-only] Para={} | Asunto={} | Cuerpo={}", destino, asunto, cuerpo);
            return;
        }

        JavaMailSender remitente = proveedorMail.getIfAvailable();
        if (remitente == null) {
            throw new IllegalStateException(
                    "app.mail.log-only=false pero no hay JavaMailSender configurado (spring.mail.host ausente)");
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(destino);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            remitente.send(mensaje);
        } catch (MailException e) {
            LOG.error("No se pudo enviar el correo a {}", destino, e);
            throw new IllegalStateException("No se pudo enviar el correo", e);
        }
    }
}
