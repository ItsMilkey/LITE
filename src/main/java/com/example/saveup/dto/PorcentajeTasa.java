package com.example.saveup.dto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Conversión entre la fracción que usa el motor financiero (0.015 = 1,5 %) y el
 * porcentaje que exponen/reciben los DTO (1.5). Regla 40 de
 * {@code .agents/rules/40-calculo-financiero.md}: esta aritmética vive en la
 * capa DTO, nunca en los servicios ni en el motor.
 *
 * <p>Una tasa puede viajar como fracción (motor, entidades, proyecciones JPQL)
 * o como porcentaje (request/response). Centralizarla aquí evita repetir
 * {@code * 100 / 100} por el código y las confusiones que eso trae.
 */
public final class PorcentajeTasa {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private PorcentajeTasa() {
    }

    /**
     * Fracción → porcentaje. Devuelve {@code null} si la fracción es {@code null},
     * para que el llamador decida el valor por defecto.
     */
    public static BigDecimal aPorcentaje(BigDecimal fraccion, int escala) {
        if (fraccion == null) {
            return null;
        }
        return fraccion.multiply(CIEN).setScale(escala, REDONDEO);
    }

    /**
     * Porcentaje → fracción (precisión {@link MathContext#DECIMAL128}).
     * Devuelve {@code null} si el porcentaje es {@code null}.
     */
    public static BigDecimal aFraccion(BigDecimal porcentaje) {
        if (porcentaje == null) {
            return null;
        }
        return porcentaje.divide(CIEN, MC);
    }
}
