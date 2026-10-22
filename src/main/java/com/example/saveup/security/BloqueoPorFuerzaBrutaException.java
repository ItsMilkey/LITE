package com.example.saveup.security;

/**
 * El par email+IP superó el máximo de intentos fallidos de login (T9.5) y
 * está bloqueado 15 minutos. Se traduce a INTENTOS_LOGIN_EXCEDIDOS (429) con
 * el mismo mensaje siempre, sin revelar cuántos intentos quedan.
 */
public class BloqueoPorFuerzaBrutaException extends RuntimeException {

    public BloqueoPorFuerzaBrutaException() {
        super("Demasiados intentos de inicio de sesión fallidos.");
    }
}
