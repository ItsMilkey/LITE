package com.example.saveup.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contratos del freno de fuerza bruta (T9.5): bloqueo al quinto fallo,
 * reinicio tras un login exitoso, aislamiento por email e IP y resolución de
 * X-Forwarded-For. Unitario: usa la caché Caffeine real del servicio.
 */
class LoginRateLimiterServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String IP = "201.1.2.3";

    private final LoginRateLimiterService limiter = new LoginRateLimiterService();

    /** Construye la clave email+IP que toda llamada al limiter necesita. */
    private LoginRateLimiterService.AccesoLogin acceso(String email, String ip) {
        return new LoginRateLimiterService.AccesoLogin(email, ip);
    }

    private void registrarFallos(int cuantos, String email, String ip) {
        for (int i = 0; i < cuantos; i++) {
            limiter.registrarFallo(acceso(email, ip));
        }
    }

    @Test
    @DisplayName("Cuatro fallos → todavía permitido; el quinto habilita el bloqueo")
    void cuatroFallos_permitidosQuintoBloquea() {
        registrarFallos(LoginRateLimiterService.MAX_INTENTOS_FALLIDOS - 1, EMAIL, IP);

        assertDoesNotThrow(() -> limiter.exigirPermitido(acceso(EMAIL, IP)),
                "Con menos del máximo la IP sigue con permiso de intentar.");

        limiter.registrarFallo(acceso(EMAIL, IP));

        assertThrows(BloqueoPorFuerzaBrutaException.class,
                () -> limiter.exigirPermitido(acceso(EMAIL, IP)),
                "Tras el quinto fallo el par email+IP queda bloqueado.");
    }

    @Test
    @DisplayName("Un login exitoso resetea el contador: vuelve a conceder el máximo de intentos")
    void exito_reseteaElContador() {
        registrarFallos(LoginRateLimiterService.MAX_INTENTOS_FALLIDOS, EMAIL, IP);
        assertThrows(BloqueoPorFuerzaBrutaException.class,
                () -> limiter.exigirPermitido(acceso(EMAIL, IP)));

        limiter.resetear(acceso(EMAIL, IP));

        assertDoesNotThrow(() -> limiter.exigirPermitido(acceso(EMAIL, IP)));
        // El fresco reinicio vuelve a tolerar los 5 fallos completos
        registrarFallos(LoginRateLimiterService.MAX_INTENTOS_FALLIDOS - 1, EMAIL, IP);
        assertDoesNotThrow(() -> limiter.exigirPermitido(acceso(EMAIL, IP)));
    }

    @Test
    @DisplayName("Aislamiento por IP: bloqueado solo para la IP que acumuló fallos")
    void bloqueo_aisladoPorIp() {
        registrarFallos(LoginRateLimiterService.MAX_INTENTOS_FALLIDOS, EMAIL, "79.30.10.2");

        assertThrows(BloqueoPorFuerzaBrutaException.class,
                () -> limiter.exigirPermitido(acceso(EMAIL, "79.30.10.2")));

        assertDoesNotThrow(() -> limiter.exigirPermitido(acceso(EMAIL, "79.30.10.3")),
                "Otra IP con el mismo correo no hereda el bloqueo.");
    }

    @Test
    @DisplayName("Aislamiento por email: bloqueado solo para el correo caliente")
    void bloqueo_aisladoPorEmail() {
        registrarFallos(LoginRateLimiterService.MAX_INTENTOS_FALLIDOS, EMAIL, IP);

        assertThrows(BloqueoPorFuerzaBrutaException.class,
                () -> limiter.exigirPermitido(acceso(EMAIL, IP)));

        assertDoesNotThrow(() -> limiter.exigirPermitido(acceso("otro@example.com", IP)),
                "Otro correo desde la misma IP no hereda el bloqueo.");
    }

    @Test
    @DisplayName("La clave ignora la capitalización del email (A@X.com ≡ a@x.com)")
    void clave_ignoraMayusculasDelEmail() {
        limiter.registrarFallo(acceso("Ana@ExAmPlE.com", IP));
        limiter.registrarFallo(acceso("ANA@example.com", IP));
        limiter.registrarFallo(acceso("ana@EXAMPLE.com", IP));
        limiter.registrarFallo(acceso(EMAIL, IP));
        limiter.registrarFallo(acceso(EMAIL, IP));

        // 5 fallos en distintos casings del mismo correo ⇒ bloqueado
        assertThrows(BloqueoPorFuerzaBrutaException.class,
                () -> limiter.exigirPermitido(acceso(EMAIL, IP)));
    }

    @Test
    @DisplayName("resolverIp usa la PRIMERA dirección de X-Forwarded-For")
    void resolverIp_primeraDireccionDelForward() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2, 10.0.0.3");
        request.setRemoteAddr("172.17.0.5");

        assertEquals("203.0.113.7", limiter.resolverIp(request));
    }

    @Test
    @DisplayName("resolverIp sin X-Forwarded-For (o en blanco) → remoteAddr")
    void resolverIp_sinForwardUsaRemoteAddr() {
        MockHttpServletRequest sinForward = new MockHttpServletRequest();
        sinForward.setRemoteAddr("190.4.5.6");
        assertEquals("190.4.5.6", limiter.resolverIp(sinForward));

        MockHttpServletRequest forwardVacio = new MockHttpServletRequest();
        forwardVacio.addHeader("X-Forwarded-For", "   ");
        forwardVacio.setRemoteAddr("190.4.5.7");
        assertEquals("190.4.5.7", limiter.resolverIp(forwardVacio));
    }
}
