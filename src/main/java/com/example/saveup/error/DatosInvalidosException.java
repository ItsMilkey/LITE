package com.example.saveup.error;

/**
 * Regla T8b, versión reforzada: un {@link IllegalArgumentException} cuya
 * <b>autoría es del propio repo</b> — el mensaje es español escrito para el
 * cliente — viaja tal cual en el {@code detail} de un 400 DATOS_INVALIDOS.
 *
 * <p>Todo rechazo de entrada con mensaje de usuario debe lanzar esta clase.
 * Los IAE de librería (NumberFormatException, parseos, conversiones de Spring)
 * NO deben retiparse: el handler les entrega el {@code detail} genérico y deja
 * la causa en el log, así ningún texto en inglés ni nombre de clase llega al
 * cliente aunque nunca lo revisemos a mano.
 */
public class DatosInvalidosException extends IllegalArgumentException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }

    public DatosInvalidosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
