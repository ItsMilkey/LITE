package com.example.saveup.error;

/**
 * Regla de negocio visible al usuario. El código forma parte del contrato con
 * el cliente y determina el status HTTP y el título del problem+json.
 */
public class ErrorNegocio extends RuntimeException {

    private final CodigoError codigo;

    public ErrorNegocio(CodigoError codigo) {
        super(codigo.getDetail());
        this.codigo = codigo;
    }

    public ErrorNegocio(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoError getCodigo() {
        return codigo;
    }
}
