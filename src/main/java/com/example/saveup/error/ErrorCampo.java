package com.example.saveup.error;

/**
 * Error de validación asociado a un campo. {@code campo} usa el nombre JSON de
 * la propiedad (por ejemplo {@code condiciones.montoCapital}), nunca el nombre
 * del atributo Java.
 */
public record ErrorCampo(String campo, String mensaje) {
}
