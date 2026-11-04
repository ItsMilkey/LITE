package com.example.saveup.service.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cálculo puro de las referencias históricas de gasto (T10d, sección 8).
 *
 * <p>Toma la serie agregada de una categoría y el mes del primer movimiento
 * del usuario. La ventana son los hasta 3 meses cerrados previos al mes, sin
 * retroceder antes del primer movimiento. Los meses de la ventana sin gasto
 * cuentan como 0; si no hay ningún mes cerrado disponible, la referencia es
 * nula.
 */
public final class ReferenciasHistoricas {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final int MESES_PROMEDIO = 3;

    private ReferenciasHistoricas() {
    }

    public record Referencia(BigDecimal mesAnterior, BigDecimal promedio3Meses) {
    }

    /**
     * @param gastoPorMes  gasto real de la categoría por mes (los meses sin fila valen 0)
     * @param mes          mes del plan
     * @param primerMes    mes del primer movimiento del usuario; null si no tiene
     */
    public static Referencia calcular(Map<YearMonth, BigDecimal> gastoPorMes,
                                      YearMonth mes, YearMonth primerMes) {
        if (mes == null || primerMes == null || !primerMes.isBefore(mes)) {
            return new Referencia(null, null);
        }

        List<YearMonth> ventana = new ArrayList<>(MESES_PROMEDIO);
        for (int i = MESES_PROMEDIO; i >= 1; i--) {
            YearMonth candidato = mes.minusMonths(i);
            if (!candidato.isBefore(primerMes)) {
                ventana.add(candidato);
            }
        }
        if (ventana.isEmpty()) {
            return new Referencia(null, null);
        }

        BigDecimal mesAnterior = dinero(gasto(gastoPorMes, mes.minusMonths(1)));
        BigDecimal suma = BigDecimal.ZERO;
        for (YearMonth month : ventana) {
            suma = suma.add(dinero(gasto(gastoPorMes, month)));
        }
        BigDecimal promedio = suma.divide(BigDecimal.valueOf(ventana.size()),
                ESCALA_DINERO, REDONDEO);
        return new Referencia(mesAnterior, promedio);
    }

    private static BigDecimal gasto(Map<YearMonth, BigDecimal> gastoPorMes, YearMonth mes) {
        if (gastoPorMes == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal valor = gastoPorMes.get(mes);
        return valor == null ? BigDecimal.ZERO : valor.abs();
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(ESCALA_DINERO, REDONDEO);
    }
}
