package com.example.saveup.service.finanzas;

import com.example.saveup.model.enums.ModalidadCalculo;

import java.math.BigDecimal;

/**
 * Condiciones de entrada para el motor financiero.
 *
 * @param modalidad             modalidad de cálculo
 * @param montoCapital          capital solicitado (> 0)
 * @param cantidadCuotas        número de cuotas (1–360)
 * @param tasaMensualFraccion   tasa mensual como fracción (0.015 = 1.5 %), nullable.
 *                              Difiere de {@link com.example.saveup.dto.SimulacionCreditoRequestDTO#tasaMensual}
 *                              que es un porcentaje (0–100).
 * @param valorCuotaPublicada   cuota publicada por la entidad, nullable
 * @param gastosIniciales       gastos cobrados al desembolso (≥ 0, < capital)
 * @param costoAdicionalPorCuota costo adicional por cuota (≥ 0)
 */
public record CondicionesCredito(
        ModalidadCalculo modalidad,
        BigDecimal montoCapital,
        int cantidadCuotas,
        BigDecimal tasaMensualFraccion,
        BigDecimal valorCuotaPublicada,
        BigDecimal gastosIniciales,
        BigDecimal costoAdicionalPorCuota
) {
    /** Constructor con defaults: gastosIniciales = 0, costoAdicionalPorCuota = 0. */
    public CondicionesCredito(
            ModalidadCalculo modalidad,
            BigDecimal montoCapital,
            int cantidadCuotas,
            BigDecimal tasaMensualFraccion,
            BigDecimal valorCuotaPublicada
    ) {
        this(modalidad, montoCapital, cantidadCuotas, tasaMensualFraccion, valorCuotaPublicada,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
