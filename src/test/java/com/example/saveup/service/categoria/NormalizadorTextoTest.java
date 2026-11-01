package com.example.saveup.service.categoria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T10b — Normalización y tokens del sugeridor (puro, sin Spring). */
class NormalizadorTextoTest {

    @Test
    @DisplayName("Pasa a minúsculas, quita tildes, colapsa espacios y recorta")
    void normalizaMayusculasTildesYEspacios() {
        assertEquals("super mercado", NormalizadorTexto.normalizar("  Super   Mercado "));
        assertEquals("lider", NormalizadorTexto.normalizar("Líder"));
        assertEquals("cafe", NormalizadorTexto.normalizar("café"));
        assertEquals("permiso de circulacion", NormalizadorTexto.normalizar("Permiso de Circulación"));
        assertEquals("peluqueria", NormalizadorTexto.normalizar("Peluquería"));
        assertEquals("sofa", NormalizadorTexto.normalizar("sofá"));
    }

    @Test
    @DisplayName("Reemplaza puntuación y símbolos por espacios")
    void reemplazaPuntuacion() {
        assertEquals("cuanto cuesta", NormalizadorTexto.normalizar("¿Cuánto cuesta?"));
        assertEquals("uber eats", NormalizadorTexto.normalizar("uber_eats"));
        assertEquals("casa 123", NormalizadorTexto.normalizar("casa-123"));
    }

    @Test
    @DisplayName("Descarta las palabras vacías de la lista de tokens")
    void descartaPalabrasVacias() {
        assertEquals(List.of("cuenta", "luz", "casa"),
                NormalizadorTexto.tokens("cuenta de la luz para mi casa"));
        assertEquals(List.of("cena", "aniversario"),
                NormalizadorTexto.tokens("cena y aniversario"));
        assertEquals("cuenta luz casa",
                NormalizadorTexto.normalizarTokens("cuenta de la luz para mi casa"));
    }

    @Test
    @DisplayName("Texto nulo o vacío no produce tokens")
    void nuloOVacio() {
        assertEquals("", NormalizadorTexto.normalizar(null));
        assertEquals("", NormalizadorTexto.normalizar("   "));
        assertTrue(NormalizadorTexto.tokens(null).isEmpty());
        assertTrue(NormalizadorTexto.tokens("de la el").isEmpty());
        assertEquals("", NormalizadorTexto.normalizarTokens(null));
    }
}
