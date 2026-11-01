package com.example.saveup.service.categoria;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementación por defecto de {@link FuenteHistorialCategoria}: no consulta
 * nada. El historial real llega con T10c.
 */
@Component
public class FuenteHistorialCategoriaVacia implements FuenteHistorialCategoria {

    @Override
    public Optional<String> codigoPara(String rut, String textoNormalizado) {
        return Optional.empty();
    }
}
