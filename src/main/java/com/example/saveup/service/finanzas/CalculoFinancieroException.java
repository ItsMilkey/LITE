package com.example.saveup.service.finanzas;

/**
 * Excepción de negocio para errores de cálculo financiero.
 * Se mapea a HTTP 400 si se expone en un endpoint.
 */
public class CalculoFinancieroException extends RuntimeException {

    public CalculoFinancieroException(String message) {
        super(message);
    }
}
