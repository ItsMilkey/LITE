package com.example.saveup.service.finanzas;

import java.math.BigDecimal;

/**
 * Fila de la tabla de amortización.
 *
 * @param numero         número de cuota (1-based)
 * @param cuota          monto de cuota pura (capital + interés)
 * @param capital        parte de la cuota destinada a capital
 * @param interes        parte de la cuota destinada a interés
 * @param costoAdicional costo adicional de esa cuota
 * @param saldo          saldo remanente después del pago
 */
public record CuotaAmortizacion(
        int numero,
        BigDecimal cuota,
        BigDecimal capital,
        BigDecimal interes,
        BigDecimal costoAdicional,
        BigDecimal saldo
) {}
