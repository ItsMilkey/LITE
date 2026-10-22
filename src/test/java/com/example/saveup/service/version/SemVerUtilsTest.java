package com.example.saveup.service.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contratos del comparador SemVer (T9.4): formato estricto y comparación
 * numérica por segmentos (no lexicográfica).
 */
class SemVerUtilsTest {

    // ───────────────── exigirFormato ─────────────────

    @ParameterizedTest
    @ValueSource(strings = {"1.4.2", "2.0.0", "10.100.999", "0.0.1"})
    @DisplayName("exigirFormato acepta MAJOR.MINOR.PATCH numérico")
    void exigirFormato_aceptaValidas(String version) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> SemVerUtils.exigirFormato(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1.2", "1.2.3.4", "v1.2.3", "1.2.x", "abc", "1..3", "1,2,3"})
    @DisplayName("exigirFormato rechaza versiones mal formadas")
    void exigirFormato_rechazaInvalidas(String version) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SemVerUtils.exigirFormato(version));

        assertEquals("La versión debe tener el formato MAJOR.MINOR.PATCH (por ejemplo 1.4.2).",
                ex.getMessage());
    }

    @Test
    @DisplayName("exigirFormato con nulo → IllegalArgumentException (DATOS_INVALIDOS)")
    void exigirFormato_nulo() {
        assertThrows(IllegalArgumentException.class, () -> SemVerUtils.exigirFormato(null));
    }

    // ───────────────── comparar ─────────────────

    @Test
    @DisplayName("comparar ordena numéricamente por MAJOR, MINOR y PATCH")
    void comparar_ordenNumerico() {
        assertTrue(SemVerUtils.comparar("1.9.9", "2.0.0") < 0, "MAJOR decide.");
        assertTrue(SemVerUtils.comparar("2.2.0", "2.4.0") < 0, "MINOR decide a igual MAJOR.");
        assertTrue(SemVerUtils.comparar("2.4.0", "2.4.1") < 0, "PATCH decide a igual MAJOR.MINOR.");
        assertTrue(SemVerUtils.comparar("2.4.0", "2.4.0") == 0);
        // Numérico, no lexicográfico: "10" > "9" aunque el texto diga lo contrario.
        assertTrue(SemVerUtils.comparar("1.10.0", "1.9.1") > 0);
    }

    @Test
    @DisplayName("comparar iguala numéricamente versiones con ceros a la izquierda")
    void comparar_cerosIzquierda() {
        assertEquals(0, SemVerUtils.comparar("2.4.0", "2.04.00"));
    }
}
