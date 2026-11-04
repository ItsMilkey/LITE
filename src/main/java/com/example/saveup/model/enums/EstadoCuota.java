package com.example.saveup.model.enums;

import java.math.BigDecimal;

/**
 * Estado informativo del pago de una cuota de deuda dentro del mes del plan
 * (T10d, sección 7). No sustituye al {@code estado} neutro de ejecución: se
 * calcula en paralelo solo para las partidas vinculadas a una deuda.
 */
public enum EstadoCuota {

    PENDIENTE,
    PARCIAL,
    PAGADA;

    /**
     * Deriva el estado comparando el gasto real imputado con el monto de la
     * cuota. Sin pago es PENDIENTE; pagado igual o mayor es PAGADA; el resto,
     * PARCIAL. Un real nulo se trata como 0.
     */
    public static EstadoCuota desde(BigDecimal real, BigDecimal montoCuota) {
        BigDecimal pagado = real == null ? BigDecimal.ZERO : real;
        BigDecimal objetivo = montoCuota == null ? BigDecimal.ZERO : montoCuota;
        if (pagado.signum() == 0) {
            return PENDIENTE;
        }
        return pagado.compareTo(objetivo) >= 0 ? PAGADA : PARCIAL;
    }
}
