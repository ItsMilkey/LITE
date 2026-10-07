package com.example.saveup.service.finanzas;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado del cálculo financiero.
 *
 * @param valorCuota             cuota de la primera cuota
 * @param tasaMensual            tasa mensual como fracción
 * @param tasaAnualEfectiva      tasa anual efectiva como fracción
 * @param montoTotal             suma de todos los flujos (cuota + costoAdicional)
 * @param costoTotalCredito      montoTotal + gastosIniciales
 * @param interesesYCostos       costoTotalCredito − capital
 * @param cargaAnualEquivalente  CAE referencial, como fracción (0.2682 = 26.82 %)
 * @param tabla                  tabla de amortización
 */
public record ResultadoCalculo(
        BigDecimal valorCuota,
        BigDecimal tasaMensual,
        BigDecimal tasaAnualEfectiva,
        BigDecimal montoTotal,
        BigDecimal costoTotalCredito,
        BigDecimal interesesYCostos,
        BigDecimal cargaAnualEquivalente,
        List<CuotaAmortizacion> tabla
) {}
