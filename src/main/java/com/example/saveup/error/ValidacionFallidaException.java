package com.example.saveup.error;

import java.util.List;

/**
 * Validación de cuerpo que no puede expresarse con Bean Validation (por
 * ejemplo, el nombre de una partida después de recortarlo o un
 * {@code partidaId} repetido dentro de un lote). Se traduce a
 * {@code 400 VALIDACION_FALLIDA} con la lista {@code errores[]}, igual que
 * {@code MethodArgumentNotValidException}, sin agregar códigos nuevos.
 */
public class ValidacionFallidaException extends RuntimeException {

    private final List<ErrorCampo> errores;

    public ValidacionFallidaException(String campo, String mensaje) {
        super(mensaje);
        this.errores = List.of(new ErrorCampo(campo, mensaje));
    }

    public ValidacionFallidaException(List<ErrorCampo> errores) {
        super(errores.isEmpty() ? "Revisa los datos ingresados." : errores.get(0).mensaje());
        this.errores = List.copyOf(errores);
    }

    public List<ErrorCampo> getErrores() {
        return errores;
    }
}
