package com.example.saveup.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Freno contra fuerza bruta en el login (T9.5): cuenta los intentos fallidos
 * por la clave email+IP en una caché Caffeine en memoria. Al quinto fallo el
 * par queda bloqueado 15 minutos; un login exitoso limpia el contador. Como
 * todo lo contrario del brute force, la verificación NO consulta la BD.
 * <p>
 * Los contadores viven en memoria del proceso: cubre el caso Render con una
 * sola instancia. Al escalar horizontalmente habría que mover esta caché a
 * Redis (queda anotado en el desglose de T9).
 */
@Service
public class LoginRateLimiterService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginRateLimiterService.class);

    /** Quinto intento fallido cierra el acceso al par email+IP por 15 minutos. */
    public static final int MAX_INTENTOS_FALLIDOS = 5;

    /** Vida de cada contador desde su último cambio (un fallo extiende la ventana). */
    private static final Duration DURACION_VENTANA = Duration.ofMinutes(15);

    /**
     * Clave del contador: el par (correo del intento, IP del cliente) que
     * siempre viaja junto. El correo se normaliza a minúsculas en la clave,
     * así que A@X.com y a@x.com cuentan como el mismo agresor.
     */
    public record AccesoLogin(String email, String ip) {
    }

    private final Cache<String, AtomicInteger> intentos = Caffeine.newBuilder()
            .expireAfterWrite(DURACION_VENTANA)
            .build();

    /**
     * Lanza {@link BloqueoPorFuerzaBrutaException} (429) si el par email+IP ya
     * acumuló el máximo de intentos fallidos. Se consulta ANTES de tocar la BD
     * para que el bloqueo no dependa de ninguna otra capa.
     */
    public void exigirPermitido(AccesoLogin acceso) {
        AtomicInteger contador = intentos.getIfPresent(clave(acceso.email(), acceso.ip()));
        if (contador != null && contador.get() >= MAX_INTENTOS_FALLIDOS) {
            LOG.info("Login bloqueado por fuerza bruta.");
            throw new BloqueoPorFuerzaBrutaException();
        }
    }

    /** Registra un intento fallido (contraseña incorrecta o correo inexistente). */
    public void registrarFallo(AccesoLogin acceso) {
        intentos.asMap().compute(clave(acceso.email(), acceso.ip()), (k, actual) -> {
            if (actual == null) {
                return new AtomicInteger(1);
            }
            actual.incrementAndGet();
            return actual;
        });
    }

    /** Login exitoso: el usuario probó la clave; se parte de cero otra vez. */
    public void resetear(AccesoLogin acceso) {
        intentos.invalidate(clave(acceso.email(), acceso.ip()));
    }

    /**
     * Con {@code app.behind-proxy=true} la app confía en la cabecera
     * {@code X-Forwarded-For} que coloca un reverse proxy de confianza y toma la
     * PRIMERA dirección (la del cliente original). Con el valor por defecto
     * ({@code false}) la cabecera se ignora: si la app es accesible directamente,
     * un atacante podría falsificarla para esquivar el bloqueo.
     */
    @Value("${app.behind-proxy:false}")
    private boolean behindProxy;

    /** Resuelve la IP del cliente según la bandera de proxy. */
    public String resolverIp(HttpServletRequest request) {
        if (behindProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String cliente = forwarded.split(",")[0].trim();
                if (!cliente.isEmpty()) {
                    return cliente;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /** Clave del contador: correo en minúsculas + IP, para agrupar aunque varíe la capitalización. */
    private String clave(String email, String ip) {
        return email.toLowerCase() + ":" + ip;
    }
}
