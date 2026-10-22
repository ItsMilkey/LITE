package com.example.saveup.service;

/**
 * El refresh token recibido no es válido: no existe, está revocado, está
 * expirado o pertenece a un usuario eliminado. Se traduce a REFRESH_INVALIDO
 * (401) para que la app móvil, a diferencia de TOKEN_EXPIRADO, vuelva al login
 * en lugar de reintentar el refresh (T9.1).
 */
public class RefreshTokenInvalidoException extends RuntimeException {

    public RefreshTokenInvalidoException() {
        super("El refresh token no es válido.");
    }

    public RefreshTokenInvalidoException(String mensaje) {
        super(mensaje);
    }

    public RefreshTokenInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
