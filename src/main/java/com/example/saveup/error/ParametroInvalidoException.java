package com.example.saveup.error;

/**
 * Parámetro de la petición inválido por longitud, formato o rango (por ejemplo,
 * un {@code @RequestParam} que supera el máximo permitido). Se traduce a
 * {@code 400 PARAMETRO_INVALIDO}; no se usa para reglas de negocio ni para
 * validaciones de cuerpo ({@code VALIDACION_FALLIDA}).
 */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
