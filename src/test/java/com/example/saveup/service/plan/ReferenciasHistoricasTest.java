package com.example.saveup.service.plan;

import com.example.saveup.service.plan.ReferenciasHistoricas.Referencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * T10d (sesión 2) — cálculo puro de las referencias históricas (caso D49). La
 * ventana son como máximo los 3 meses cerrados previos al mes y nunca empieza
 * antes del primer movimiento del usuario.
 */
class ReferenciasHistoricasTest {

    private static final YearMonth MES = YearMonth.of(2026, 11);
    private static final YearMonth PRIMER_MES = YearMonth.of(2026, 8);

    @Test
    @DisplayName("D49: 100.000, 120.000 y 80.000 en los 3 meses previos dan promedio 100.000,00 y mesAnterior 80.000,00")
    void d49_promedioYMesAnterior() {
        Map<YearMonth, BigDecimal> serie = new HashMap<>();
        serie.put(YearMonth.of(2026, 8), dinero("100000.00"));
        serie.put(YearMonth.of(2026, 9), dinero("120000.00"));
        serie.put(YearMonth.of(2026, 10), dinero("80000.00"));

        Referencia referencia = ReferenciasHistoricas.calcular(serie, MES, PRIMER_MES);

        assertMoney("100000.00", referencia.promedio3Meses());
        assertMoney("80000.00", referencia.mesAnterior());
    }

    @Test
    @DisplayName("D49: los meses de la ventana sin gasto cuentan como 0")
    void d49_mesesSinGastoValenCero() {
        Map<YearMonth, BigDecimal> serie = Map.of(YearMonth.of(2026, 10), dinero("80000.00"));

        Referencia referencia = ReferenciasHistoricas.calcular(serie, MES, PRIMER_MES);

        assertMoney("26666.67", referencia.promedio3Meses());
        assertMoney("80000.00", referencia.mesAnterior());
    }

    @Test
    @DisplayName("D49: la ventana se recorta al mes del primer movimiento")
    void d49_ventanaDesdeElPrimerMovimiento() {
        Map<YearMonth, BigDecimal> serie = new HashMap<>();
        serie.put(YearMonth.of(2026, 7), dinero("999999.00"));
        serie.put(YearMonth.of(2026, 9), dinero("100000.00"));
        serie.put(YearMonth.of(2026, 10), dinero("120000.00"));

        Referencia referencia = ReferenciasHistoricas.calcular(
                serie, MES, YearMonth.of(2026, 9));

        assertMoney("110000.00", referencia.promedio3Meses());
        assertMoney("120000.00", referencia.mesAnterior());
    }

    @Test
    @DisplayName("D49: el promedio redondea HALF_UP a 2 decimales")
    void d49_redondeoHalfUp() {
        Map<YearMonth, BigDecimal> serie = new HashMap<>();
        serie.put(YearMonth.of(2026, 9), dinero("0.67"));
        serie.put(YearMonth.of(2026, 10), dinero("0.34"));

        Referencia referencia = ReferenciasHistoricas.calcular(
                serie, MES, YearMonth.of(2026, 9));

        assertMoney("0.51", referencia.promedio3Meses());
        assertMoney("0.34", referencia.mesAnterior());
    }

    @Test
    @DisplayName("D48: si el primer movimiento es del mismo mes no hay referencia")
    void d48_primerMovimientoEnElMes() {
        Referencia referencia = ReferenciasHistoricas.calcular(Map.of(), MES, MES);

        assertNull(referencia.promedio3Meses());
        assertNull(referencia.mesAnterior());
    }

    @Test
    @DisplayName("D49: sin primer movimiento tampoco hay referencia")
    void d49_sinPrimeMovimiento() {
        Referencia referencia = ReferenciasHistoricas.calcular(Map.of(), MES, null);

        assertNull(referencia.promedio3Meses());
        assertNull(referencia.mesAnterior());
    }

    private static BigDecimal dinero(String valor) {
        return new BigDecimal(valor).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void assertMoney(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                "Se esperaba " + esperado + " pero fue " + real);
    }
}
