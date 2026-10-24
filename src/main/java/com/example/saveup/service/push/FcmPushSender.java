package com.example.saveup.service.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Adaptador real de FCM (T9.6). Solo se activa con
 * {@code app.push.log-only=false}; en cualquier otro caso manda
 * {@link LogPushSender}.
 *
 * <p>Las credenciales llegan en {@code FIREBASE_CREDENTIALS}: el JSON del
 * service account de Firebase, en base64 (o crudo si empieza con <code>{</code>).
 * Es el formato que ya existe en el repo para secretos de despliegue
 * ({@code JWT_SECRET}, {@code SPRING_DATASOURCE_URL}).
 *
 * <p>Un fallo de entrega nunca lanza: el job debe seguir con el resto de
 * dispositivos. Los códigos que significan "este token ya no existe" se
 * traducen a {@link PushSender.ResultadoEnvio#tokenMuerto()} para que el
 * servicio desactive el dispositivo.
 */
@Service
@ConditionalOnProperty(name = "app.push.log-only", havingValue = "false")
public class FcmPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(FcmPushSender.class);

    /** Códigos de FCM que invalidan el token de forma permanente. */
    private static final Set<MessagingErrorCode> TOKEN_MUERTO = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    private final String credenciales;

    private FirebaseMessaging mensajeria;

    public FcmPushSender(@Value("${FIREBASE_CREDENTIALS:}") String credenciales) {
        this.credenciales = credenciales;
    }

    @PostConstruct
    void inicializar() {
        if (credenciales == null || credenciales.isBlank()) {
            throw new IllegalStateException(
                    "app.push.log-only=false pero FIREBASE_CREDENTIALS está vacío: "
                            + "configura el JSON del service account (base64) o deja log-only=true");
        }

        try (InputStream entrada = new ByteArrayInputStream(decodificar(credenciales))) {
            FirebaseOptions opciones = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(entrada))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(opciones)
                    : FirebaseApp.getInstance();
            this.mensajeria = FirebaseMessaging.getInstance(app);
            LOG.info("FCM inicializado para notificaciones push (T9.6)");
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "No se pudieron leer las credenciales de Firebase (FIREBASE_CREDENTIALS)", e);
        }
    }

    @Override
    public ResultadoEnvio enviar(String token, String titulo, String cuerpo, Map<String, String> datos) {
        try {
            mensajeria.send(Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(titulo)
                            .setBody(cuerpo)
                            .build())
                    .putAllData(datos)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build())
                    .build());
            return ResultadoEnvio.ok();
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode codigo = e.getMessagingErrorCode();
            if (codigo != null && TOKEN_MUERTO.contains(codigo)) {
                LOG.info("FCM rechazó el dispositivo {} ({}); se desactiva",
                        TokensPush.enmascarar(token), codigo);
                return ResultadoEnvio.tokenMuerto();
            }
            LOG.warn("No se pudo enviar la push a {}: {}", TokensPush.enmascarar(token), e.getMessage());
            return ResultadoEnvio.fallo();
        } catch (IllegalArgumentException e) {
            // Token con formato inválido: no se reintenta nunca.
            LOG.info("Token push inválido {}; se desactiva", TokensPush.enmascarar(token));
            return ResultadoEnvio.tokenMuerto();
        }
    }

    /** Acepta base64 o el JSON crudo (útil para pegar el archivo en local). */
    private byte[] decodificar(String valor) {
        String limpio = valor.trim();
        if (limpio.startsWith("{")) {
            return limpio.getBytes(StandardCharsets.UTF_8);
        }
        return Base64.getDecoder().decode(limpio);
    }
}
