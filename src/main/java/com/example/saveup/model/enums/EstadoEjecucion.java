package com.example.saveup.model.enums;

import java.math.BigDecimal;

/**
 * Estado de ejecución de un monto real frente a un monto planificado (T5/T10a).
 *
 * <p>Umbrales explícitos y sin números mágicos:
 * <ul>
 *   <li>&lt; 80 % → {@link #BAJO_EL_PLAN}</li>
 *   <li>80 % a 100 % inclusive → {@link #CERCA_DEL_PLAN}</li>
 *   <li>&gt; 100 % → {@link #SOBRE_EL_PLAN}</li>
 * </ul>
 * Los estados son informativos: ninguno representa un error. El porcentaje se
 * compara por multiplicación cruzada ({@code real × 100} contra
 * {@code umbral × planificado}) para no redondear ni dividir.
 */
public enum EstadoEjecucion {

    BAJO_EL_PLAN,
    CERCA_DEL_PLAN,
    SOBRE_EL_PLAN;

    /** A partir de este porcentaje el usuario está cerca de su plan. */
    public static final BigDecimal UMBRAL_CERCA = new BigDecimal("80");

    /** Por encima de este porcentaje el plan quedó sobrepasado. */
    public static final BigDecimal UMBRAL_SOBRE = new BigDecimal("100");

    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * Deriva el estado comparando {@code real × 100} contra {@code umbral × planificado}.
     *
     * <p>Un plan nulo o no positivo se trata sin división: si hubo gasto real el
     * estado es {@link #SOBRE_EL_PLAN}; si no, {@link #BAJO_EL_PLAN}. Un real
     * nulo se trata como 0. T5 nunca persiste un plan ≤ 0; T10c usa esta misma
     * regla para las partidas sin monto.
     */
    public static EstadoEjecucion desde(BigDecimal real, BigDecimal planificado) {
        BigDecimal gasto = real == null ? BigDecimal.ZERO : real;
        BigDecimal plan = planificado == null ? BigDecimal.ZERO : planificado;

        if (plan.signum() <= 0) {
            return gasto.signum() > 0 ? SOBRE_EL_PLAN : BAJO_EL_PLAN;
        }

        BigDecimal gastoEnPorcentaje = gasto.multiply(CIEN);
        if (gastoEnPorcentaje.compareTo(UMBRAL_CERCA.multiply(plan)) < 0) {
            return BAJO_EL_PLAN;
        }
        if (gastoEnPorcentaje.compareTo(UMBRAL_SOBRE.multiply(plan)) > 0) {
            return SOBRE_EL_PLAN;
        }
        return CERCA_DEL_PLAN;
    }
}
