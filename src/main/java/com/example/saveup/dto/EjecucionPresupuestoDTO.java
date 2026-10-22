package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "Ejecución presupuestaria 50/30/20 de un mes.")
public class EjecucionPresupuestoDTO {
    @Schema(description = "Presupuesto calculado para necesidades, con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal presupuestoNecesidades; // (Ingresos * % Necesidades)

    @Schema(description = "Gasto real en necesidades, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gastoNecesidades; // Suma gastos tipo NECESIDAD

    @Schema(description = "Presupuesto calculado para deseos, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal presupuestoDeseos; // (Ingresos * % Deseos)

    @Schema(description = "Gasto real en deseos, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gastoDeseos; // Suma gastos tipo DESEO

    @Schema(description = "Presupuesto calculado para ahorro, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal presupuestoAhorro; // (Ingresos * % Ahorro)

    @Schema(description = "Ahorro ejecutado como abonos a metas, con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal ahorroRealizado; // Suma de egresos hacia metas (ABONO_META)

    // Metadata
    @Schema(description = "Ingresos del mes, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalIngresos;

    @Schema(description = "Porcentaje de necesidades configurado (0–100).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeNecesidadesConfigurado;

    @Schema(description = "Porcentaje de deseos configurado (0–100).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeDeseosConfigurado;

    @Schema(description = "Porcentaje de ahorro configurado (0–100).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeAhorroConfigurado;

    @Schema(description = "Indica si el ahorro se automatiza en metas.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean automatizarAhorroEnMetas;
}
