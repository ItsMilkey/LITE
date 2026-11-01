package com.example.saveup.service.categoria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T10b — Distancia de Damerau-Levenshtein (OSA) con tope. */
class DistanciaEdicionTest {

    @Test
    @DisplayName("Cadenas iguales o vacías")
    void cadenasIgualesOVacias() {
        assertEquals(0, DistanciaEdicion.distancia("supermercado", "supermercado", 2));
        assertEquals(0, DistanciaEdicion.distancia("", "", 2));
        assertEquals(3, DistanciaEdicion.distancia("", "abc", 3));
        assertEquals(3, DistanciaEdicion.distancia("abc", "", 3));
        assertEquals(0, DistanciaEdicion.distancia(null, null, 1));
        assertEquals(1, DistanciaEdicion.distancia(null, "a", 1));
    }

    @Test
    @DisplayName("Inserción, borrado y sustitución cuestan 1")
    void operacionesSimples() {
        assertEquals(1, DistanciaEdicion.distancia("supermercado", "supermecado", 1));
        assertEquals(1, DistanciaEdicion.distancia("netflix", "netflx", 1));
        assertEquals(1, DistanciaEdicion.distancia("gato", "gatos", 1));
        assertEquals(1, DistanciaEdicion.distancia("gatos", "gato", 1));
        assertEquals(1, DistanciaEdicion.distancia("gato", "pato", 1));
    }

    @Test
    @DisplayName("Una transposición adyacente cuesta 1")
    void transposicionAdyacente() {
        assertEquals(1, DistanciaEdicion.distancia("casa", "caas", 1));
        assertEquals(1, DistanciaEdicion.distancia("ab", "ba", 1));
    }

    @Test
    @DisplayName("El tope corta el cálculo y devuelve tope + 1")
    void topeCortaElCalculo() {
        assertEquals(3, DistanciaEdicion.distancia("supermercado", "xyzqwerty", 2));
        assertEquals(2, DistanciaEdicion.distancia("supermercado", "xyzqwerty", 1));
        assertEquals(1, DistanciaEdicion.distancia("gato", "pato", 0));
        assertEquals(0, DistanciaEdicion.distancia("gato", "gato", 0));
    }

    @Test
    @DisplayName("Si la diferencia de largo supera el tope, no calcula la matriz")
    void diferenciaDeLargoMayorQueTope() {
        assertEquals(3, DistanciaEdicion.distancia("a", "abcdef", 2));
        assertEquals(2, DistanciaEdicion.distancia("abcdef", "a", 1));
    }
}
