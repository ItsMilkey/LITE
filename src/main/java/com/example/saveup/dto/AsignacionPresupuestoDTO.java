package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class AsignacionPresupuestoDTO {

    @NotNull(message = "El ID de la meta es obligatorio")
    @Schema(description = "ID de la meta que recibe la asignación.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long metaId;

    /**
     * Fraccion porcentual del monto de ahorro a destinar a esta meta (0.0 - 100.0).
     */
    @NotNull(message = "El porcentaje de asignacion es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje debe tener como máximo 3 enteros y 2 decimales")
    @Schema(description = "Porcentaje del ahorro destinado a la meta (0–100), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentaje;
}
