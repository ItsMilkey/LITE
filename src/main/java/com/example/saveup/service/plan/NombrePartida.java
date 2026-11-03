package com.example.saveup.service.plan;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalización del nombre de una partida (T10c). Es una regla distinta de la
 * del sugeridor: aquí se conservan las palabras vacías y la puntuación; solo
 * se pasa a minúsculas, se quitan las tildes y se colapsan los espacios.
 *
 * <p>La misma función se usa al guardar ({@code partida_plan.nombre_normalizado})
 * y al consultar el historial del sugeridor, de modo que dos nombres que
 * coinciden aquí son duplicados dentro del plan.
 */
public final class NombrePartida {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("[\\s\\u00A0]+");

    private NombrePartida() {
    }

    /** Forma canónica del nombre; nunca devuelve null. */
    public static String normalizar(String nombre) {
        if (nombre == null) {
            return "";
        }
        String sinTildes = MARCAS_DIACRITICAS.matcher(
                Normalizer.normalize(nombre, Normalizer.Form.NFD)).replaceAll("");
        return ESPACIOS.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
