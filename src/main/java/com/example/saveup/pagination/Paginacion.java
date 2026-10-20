package com.example.saveup.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Punto único de validación de la paginación de la API.
 */
public final class Paginacion {

    private static final String MENSAJE_INVALIDO =
            "Los parámetros de paginación no son válidos: page debe ser 0 o mayor "
                    + "y size debe estar entre 1 y 100.";

    private Paginacion() {
    }

    public static Pageable crear(int page, int size) {
        return crear(page, size, Sort.unsorted());
    }

    public static Pageable crear(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(MENSAJE_INVALIDO);
        }
        return PageRequest.of(page, size, sort != null ? sort : Sort.unsorted());
    }
}
