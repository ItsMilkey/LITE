package com.example.saveup.model.enums;

/**
 * Tipos de token de verificación de un solo uso (T9.3). Hoy solo existe el
 * restablecimiento de contraseña; evolución a verificación de email, etc.,
 * significa agregar un valor, el CHECK de la migración V11 y su flujo.
 */
public enum TipoVerificacion {
    RESET_PASSWORD
}
