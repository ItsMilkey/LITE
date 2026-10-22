package com.example.saveup.service.version;

import java.util.regex.Pattern;

/**
 * Comparador SemVer (T9.4) para la política de versiones de la app. Solo
 * soporta el formato numérico que la app reporta: MAJOR.MINOR.PATCH con
 * segmentos de hasta 3 dígitos; se comparan los números, no los textos
 * ("1.10.0" > "1.9.1"). Sin prerreleases ni build metadata: los clientes
 * móviles no las usan.
 */
public final class SemVerUtils {

    /** Formato aceptado: tres segmentos numéricos separados por punto. */
    private static final Pattern FORMATO = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    private SemVerUtils() {
    }

    /**
     * Verifica el formato SemVer; si no cumple lanza
     * {@link IllegalArgumentException} (el handler la traduce a
     * DATOS_INVALIDOS 400) con un mensaje accionable para soporte.
     */
    public static void exigirFormato(String version) {
        if (version == null || version.isBlank() || !FORMATO.matcher(version.trim()).matches()) {
            throw new IllegalArgumentException(
                    "La versión debe tener el formato MAJOR.MINOR.PATCH (por ejemplo 1.4.2).");
        }
    }

    /**
     * Compara dos versiones SemVer válidas: negativo si {@code a < b},
     * cero si son iguales (numéricamente, aceptando ceros a la izquierda) y
     * positivo si {@code a > b}.
     */
    public static int comparar(String a, String b) {
        String[] segmentosA = a.trim().split("\\.");
        String[] segmentosB = b.trim().split("\\.");

        for (int i = 0; i < 3; i++) {
            int numeroA = Integer.parseInt(segmentosA[i]);
            int numeroB = Integer.parseInt(segmentosB[i]);
            if (numeroA != numeroB) {
                return Integer.compare(numeroA, numeroB);
            }
        }
        return 0;
    }

    /** Normaliza espacios alrededor; los umbrales configurados no llevan espacios. */
    public static String normalizada(String version) {
        return version.trim();
    }
}
