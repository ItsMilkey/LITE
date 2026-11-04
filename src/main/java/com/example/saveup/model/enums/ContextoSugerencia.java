package com.example.saveup.model.enums;

import com.example.saveup.model.CodigoCategoria;

/**
 * Contexto de una sugerencia de categoría (T10b).
 *
 * <ul>
 *   <li>{@link #GASTO}: todas las categorías menos {@code SUELDO} y {@code AHORRO}.</li>
 *   <li>{@link #INGRESO}: solo {@code SUELDO} y {@code OTROS}.</li>
 * </ul>
 */
public enum ContextoSugerencia {

    GASTO,
    INGRESO;

    /** Indica si una categoría del catálogo puede sugerirse en este contexto. */
    public boolean permite(String codigo) {
        if (codigo == null) {
            return false;
        }
        return switch (this) {
            case GASTO -> !CodigoCategoria.SUELDO.equals(codigo)
                    && !CodigoCategoria.AHORRO.equals(codigo);
            case INGRESO -> CodigoCategoria.SUELDO.equals(codigo)
                    || CodigoCategoria.OTROS.equals(codigo);
        };
    }
}
