package com.example.saveup.service.movimiento;

import com.example.saveup.model.enums.TipoMovimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la regla de signo canónico (T1d).
 */
class SignoMovimientoTest {

    private static final BigDecimal CIEN = new BigDecimal("100.00");
    private static final BigDecimal MENOS_CIEN = new BigDecimal("-100.00");

    // ─────────────────── Tipos positivos ───────────────────

    @Test
    @DisplayName("INGRESO_GENERAL: entrada positiva y negativa → positivo")
    void ingresoGeneral_quedaPositivo() {
        assertEquals(0, CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.INGRESO_GENERAL, CIEN)));
        assertEquals(0, CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.INGRESO_GENERAL, MENOS_CIEN)));
    }

    @Test
    @DisplayName("RETIRO_META: entrada positiva y negativa → positivo")
    void retiroMeta_quedaPositivo() {
        assertEquals(0, CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.RETIRO_META, CIEN)));
        assertEquals(0, CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.RETIRO_META, MENOS_CIEN)));
    }

    // ─────────────────── Tipos negativos ───────────────────

    @Test
    @DisplayName("GASTO_GENERAL: entrada positiva y negativa → negativo")
    void gastoGeneral_quedaNegativo() {
        assertEquals(0, MENOS_CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, CIEN)));
        assertEquals(0, MENOS_CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, MENOS_CIEN)));
    }

    @Test
    @DisplayName("PAGO_DEUDA: entrada positiva y negativa → negativo")
    void pagoDeuda_quedaNegativo() {
        assertEquals(0, MENOS_CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.PAGO_DEUDA, CIEN)));
        assertEquals(0, MENOS_CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.PAGO_DEUDA, MENOS_CIEN)));
    }

    @Test
    @DisplayName("ABONO_META: entrada positiva y negativa → negativo")
    void abonoMeta_quedaNegativo() {
        assertEquals(0, MENOS_CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.ABONO_META, CIEN)));
        assertEquals(0, MENOS_CIEN.compareTo(SignoMovimiento.aplicar(TipoMovimiento.ABONO_META, MENOS_CIEN)));
    }

    // ─────────────────── Rechazos ───────────────────

    @Test
    @DisplayName("Monto 0 → error (no puede haber movimiento sin efecto en el saldo)")
    void montoCero_error() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, BigDecimal.ZERO));
        assertTrue(ex.getMessage().contains("0"));
    }

    @Test
    @DisplayName("Monto -0.00 → error")
    void montoCeroNegativo_error() {
        assertThrows(IllegalArgumentException.class,
                () -> SignoMovimiento.aplicar(TipoMovimiento.INGRESO_GENERAL, new BigDecimal("-0.00")));
    }

    @Test
    @DisplayName("Monto null → error")
    void montoNull_error() {
        assertThrows(IllegalArgumentException.class,
                () -> SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, null));
    }

    @Test
    @DisplayName("Tipo null → error")
    void tipoNull_error() {
        assertThrows(IllegalArgumentException.class,
                () -> SignoMovimiento.aplicar(null, CIEN));
    }

    // ─────────────────── Escala y redondeo ───────────────────

    @Test
    @DisplayName("El resultado siempre queda en escala 2 con HALF_UP")
    void escalaDos_halffUp() {
        BigDecimal conTresDecimales = new BigDecimal("100.005");

        BigDecimal positivo = SignoMovimiento.aplicar(TipoMovimiento.INGRESO_GENERAL, conTresDecimales);
        BigDecimal negativo = SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, conTresDecimales);

        assertEquals(2, positivo.scale());
        assertEquals(2, negativo.scale());
        assertEquals(0, new BigDecimal("100.01").compareTo(positivo),
                "HALF_UP sobre 100.005 → 100.01");
        assertEquals(0, new BigDecimal("-100.01").compareTo(negativo));
    }

    @Test
    @DisplayName("Consistente: aplicar(tipo, monto) es idempotente en el signo")
    void idempotente() {
        BigDecimal unaVez = SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, CIEN);
        BigDecimal dosVeces = SignoMovimiento.aplicar(TipoMovimiento.GASTO_GENERAL, unaVez);

        assertEquals(0, unaVez.compareTo(dosVeces));
    }

    // ─────────────────── Todos los tipos registrados ───────────────────

    @Test
    @DisplayName("Los 5 valores de TipoMovimiento tienen signo definido (evita tipos olvidados)")
    void todosLosTiposTienenSigno() {
        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            assertDoesNotThrow(() -> SignoMovimiento.esNegativo(tipo),
                    "Falta registrar el signo de " + tipo);
        }
        assertEquals(5, TipoMovimiento.values().length,
                "Si se agregó un valor al enum, hay que registrarlo en SignoMovimiento y en el CHECK de la BD");
    }
}
