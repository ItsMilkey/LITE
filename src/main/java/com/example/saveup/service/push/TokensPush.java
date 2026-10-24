package com.example.saveup.service.push;

/**
 * Utilidades de presentación de tokens push. El token de FCM es una credencial
 * de envío: cualquiera que lo tenga puede notificar al dispositivo, así que no
 * se loguea ni se devuelve completo en las respuestas de la API.
 */
public final class TokensPush {

    private static final int VISIBLES_AL_INICIO = 8;
    private static final int VISIBLES_AL_FINAL = 4;

    private TokensPush() {
    }

    /**
     * Deja visibles los primeros 8 y los últimos 4 caracteres. Con tokens muy
     * cortos enmascara todo menos los últimos 4, sin fallar por índices.
     */
    public static String enmascarar(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.length() <= VISIBLES_AL_INICIO + VISIBLES_AL_FINAL) {
            return "****" + token.substring(Math.max(0, token.length() - VISIBLES_AL_FINAL));
        }
        return token.substring(0, VISIBLES_AL_INICIO) + "…"
                + token.substring(token.length() - VISIBLES_AL_FINAL);
    }
}
