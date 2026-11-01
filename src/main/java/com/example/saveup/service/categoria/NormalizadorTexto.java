package com.example.saveup.service.categoria;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalización de texto del sugeridor (T10b), sin dependencias de Spring.
 *
 * <p>Pasa a minúsculas, quita tildes por descomposición Unicode, reemplaza todo
 * carácter que no sea {@code a-z} ni {@code 0-9} por espacio, colapsa espacios
 * y recorta. {@link #tokens(String)} además descarta las palabras vacías.
 */
public final class NormalizadorTexto {

    private static final Set<String> PALABRAS_VACIAS = Set.of(
            "de", "del", "la", "las", "el", "los", "un", "una", "y", "e", "en",
            "a", "al", "para", "por", "con", "mi", "mis", "tu", "tus", "su", "sus");

    private NormalizadorTexto() {
    }

    /** Texto normalizado (minúsculas, sin tildes, solo {@code a-z0-9} y espacios simples). */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Tokens normalizados, sin palabras vacías. */
    public static List<String> tokens(String texto) {
        String normalizado = normalizar(texto);
        if (normalizado.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalizado.split(" "))
                .filter(token -> !PALABRAS_VACIAS.contains(token))
                .toList();
    }

    /** Tokens unidos por un espacio; es la clave normalizada que ve el historial. */
    public static String normalizarTokens(String texto) {
        return String.join(" ", tokens(texto));
    }
}
