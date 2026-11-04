package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoPresupuesto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Reparto del plan por tipo, frente a la guía (T10d, sección 4). Todo es
 * informativo: ningún valor es un error.
 */
@Data
@Schema(description = "Plan y gasto real de un tipo (NECESIDAD, DESEO u OTROS) y su diferencia de puntos con la guía.")
public class RepartoTipoDTO {

    @Schema(description = "Tipo del reparto.", requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoPresupuesto tipo;

    @Schema(description = "Suma de las partidas del tipo efectivo, con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal planificado;

    @Schema(description = "planificado ÷ ingresoReferencia × 100 (HALF_UP); null sin ingreso.")
    private BigDecimal porcentajeDelIngreso;

    @Schema(description = "Gasto real del tipo en el mes, con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal real;

    @Schema(description = "real ÷ ingresoReal × 100 (HALF_UP); null sin ingreso real.")
    private BigDecimal porcentajeRealDelIngresoReal;

    @Schema(description = "porcentajeDelIngreso − porcentaje de la guía, con 2 decimales; " +
            "null para OTROS o si falta el ingreso o la guía.")
    private BigDecimal diferenciaPuntos;
}
