package com.example.saveup.service.categoria;

import java.util.Optional;

/**
 * Fuente de historial del sugeridor (T10b). T10b trae una implementación vacía;
 * T10c enchufa el historial real del usuario sin tocar la lógica de puntaje.
 */
public interface FuenteHistorialCategoria {

    /**
     * @param rut              usuario autenticado
     * @param textoNormalizado texto del usuario normalizado y sin palabras vacías
     * @return código de la categoría usada históricamente para ese texto, si existe
     */
    Optional<String> codigoPara(String rut, String textoNormalizado);
}
