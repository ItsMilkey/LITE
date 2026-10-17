package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada de condiciones de crédito desde la API.
 * Difiere de {@link com.example.saveup.service.finanzas.CondicionesCredito} (record interno)
 * en que aquí las tasas vienen en porcentaje (0–100) y los campos son opcionales
 * (el resolutor se encarga de validar y normalizar).
 */
@Data
@Builder
@NoArgsConstructor
public class CondicionesCreditoInput {

    @NotNull(message = "La modalidad es obligatoria")
    private ModalidadCalculo modalidad;

    @NotNull(message = "El monto de capital es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto de capital debe ser mayor a 0")
    private BigDecimal montoCapital;

    private Integer cantidadCuotas;
    private LocalDate fechaPrimeraCuota;
    private LocalDate fechaUltimaCuota;
    private BigDecimal tasaMensual;
    private BigDecimal tasaAnualEfectiva;
    private BigDecimal valorCuota;
    private BigDecimal gastosIniciales;
    private BigDecimal costoAdicionalPorCuota;

    public CondicionesCreditoInput(ModalidadCalculo modalidad, BigDecimal montoCapital, Integer cantidadCuotas,
                                   LocalDate fechaPrimeraCuota, LocalDate fechaUltimaCuota, BigDecimal tasaMensual,
                                   BigDecimal tasaAnualEfectiva, BigDecimal valorCuota, BigDecimal gastosIniciales,
                                   BigDecimal costoAdicionalPorCuota) {
        this.modalidad = modalidad;
        this.montoCapital = montoCapital;
        this.cantidadCuotas = cantidadCuotas;
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        this.fechaUltimaCuota = fechaUltimaCuota;
        this.tasaMensual = tasaMensual;
        this.tasaAnualEfectiva = tasaAnualEfectiva;
        this.valorCuota = valorCuota;
        this.gastosIniciales = gastosIniciales;
        this.costoAdicionalPorCuota = costoAdicionalPorCuota;
    }

    /**
     * Tasa mensual como fracción (0.015 = 1,5 %): lo que consume el motor. La
     * conversión porcentaje → fracción vive en el DTO (regla 40), no en
     * {@link com.example.saveup.service.finanzas.ResolutorCondiciones}.
     */
    public BigDecimal tasaMensualFraccion() {
        return PorcentajeTasa.aFraccion(tasaMensual);
    }

    /** Tasa anual efectiva como fracción (0.1956 = 19,56 %). */
    public BigDecimal tasaAnualEfectivaFraccion() {
        return PorcentajeTasa.aFraccion(tasaAnualEfectiva);
    }
}
