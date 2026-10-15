package com.example.saveup.model.enums;

import java.math.BigDecimal;

/**
 * Estado de ejecución de un límite presupuestario según el porcentaje usado (T5).
 *
 * <p>Umbrales explícitos y sin números mágicos:
 * <ul>
 *   <li>&lt; 80 % → {@link #OK}</li>
 *   <li>≥ 80 % y &lt; 100 % → {@link #ALERTA}</li>
 *   <li>≥ 100 % → {@link #EXCEDIDO}</li>
 * </ul>
 * Las comparaciones usan {@link BigDecimal#compareTo} (no {@code equals}), para
 * que 80 y 80.00 se comporten igual.
 */
public enum EstadoEjecucion {

    OK,
    ALERTA,
    EXCEDIDO;

    /** A partir de este porcentaje el usuario entra en alerta. */
    public static final BigDecimal UMBRAL_ALERTA = new BigDecimal("80");

    /** A partir de este porcentaje el límite está excedido. */
    public static final BigDecimal UMBRAL_EXCEDIDO = new BigDecimal("100");

    /**
     * Deriva el estado del porcentaje usado. Un porcentaje nulo se trata como 0
     * (sin gasto) para no romper el reporte.
     */
    public static EstadoEjecucion desdePorcentaje(BigDecimal porcentajeUsado) {
        if (porcentajeUsado == null) {
            return OK;
        }
        if (porcentajeUsado.compareTo(UMBRAL_EXCEDIDO) >= 0) {
            return EXCEDIDO;
        }
        if (porcentajeUsado.compareTo(UMBRAL_ALERTA) >= 0) {
            return ALERTA;
        }
        return OK;
    }
}
