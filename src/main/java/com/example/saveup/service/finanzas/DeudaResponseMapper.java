package com.example.saveup.service.finanzas;

import com.example.saveup.dto.DeudaResponseDTO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Mapper que construye un {@link DeudaResponseDTO} a partir de los datos
 * crudos de la proyección JPQL. Centraliza los cálculos financieros
 * que antes vivían en el constructor del DTO (feature envy).
 */
public class DeudaResponseMapper {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private DeudaResponseMapper() {}

    /**
     * Popula los campos calculados de un {@link DeudaResponseDTO} a partir
     * de los datos financieros crudos. Los campos simples (id, nombre, etc.)
     * deben estar ya asignados en el DTO.
     */
    public static void populateCalculatedFields(
            DeudaResponseDTO dto,
            BigDecimal tasaMensualFraccion,
            BigDecimal montoTotal,
            int cantidadCuotas,
            BigDecimal valorCuota,
            LocalDate fechaPrimeraCuota,
            BigDecimal montoPagadoRaw,
            BigDecimal montoPagadoPrevio,
            Long cuotasPagadas,
            int cuotasPagadasPrevias) {

        // Tasa mensual como porcentaje (0–100)
        dto.setTasaMensualPorcentaje(tasaMensualFraccion != null
                ? tasaMensualFraccion.multiply(CIEN).setScale(4, REDONDEO)
                : BigDecimal.ZERO.setScale(4, REDONDEO));

        // Tasa anual efectiva: (1+m)^12 − 1, como porcentaje
        BigDecimal tasaFraccion = tasaMensualFraccion != null ? tasaMensualFraccion : BigDecimal.ZERO;
        dto.setTasaAnualEfectiva(tasaFraccion.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.ONE.add(tasaFraccion).pow(12).subtract(BigDecimal.ONE).multiply(CIEN).setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO));

        // Valor cuota: fallback a división montoTotal/cantidadCuotas
        dto.setValorCuota(valorCuota != null ? valorCuota.setScale(ESCALA_DINERO, REDONDEO)
                : (cantidadCuotas > 0 ? montoTotal.divide(BigDecimal.valueOf(cantidadCuotas), ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO)));

        // Fecha última cuota: generada por CalendarioCuotas
        if (fechaPrimeraCuota != null && cantidadCuotas > 0) {
            CalendarioCuotas calendario = new CalendarioCuotas();
            List<LocalDate> fechas = calendario.fechasVencimiento(fechaPrimeraCuota, cantidadCuotas);
            dto.setFechaUltimaCuota(fechas.get(fechas.size() - 1));
        }

        // Montos pagados y restantes
        BigDecimal pagado = montoPagadoRaw != null
                ? montoPagadoRaw.abs().setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        BigDecimal previo = montoPagadoPrevio != null
                ? montoPagadoPrevio.setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        dto.setMontoPagado(pagado.add(previo));
        dto.setMontoRestante(montoTotal.subtract(dto.getMontoPagado()).max(BigDecimal.ZERO).setScale(ESCALA_DINERO, REDONDEO));
        dto.setCuotasPagadas((cuotasPagadas != null ? cuotasPagadas.intValue() : 0) + cuotasPagadasPrevias);
    }
}
