package com.example.saveup.service;

/**
 * El token de verificación recibido no es válido: no existe, ya se usó, fue
 * invalidado por una solicitud posterior o expiró (vale 1 hora). Se traduce a
 * TOKEN_VERIFICACION_INVALIDO (400) sin distinguir el motivo exacto, para no
 * dar pistas sobre cuáles tokens siguen vivos (T9.3).
 */
public class TokenVerificacionInvalidoException extends RuntimeException {

    public TokenVerificacionInvalidoException() {
        super("El token no es válido o ya no está vigente.");
    }

    public TokenVerificacionInvalidoException(String mensaje) {
        super(mensaje);
    }
}
