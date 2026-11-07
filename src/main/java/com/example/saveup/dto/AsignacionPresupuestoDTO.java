package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
     * Porcentaje del ahorro a destinar a esta meta (0.0 - 100.0). La suma del
     * conjunto debe ser exactamente 100.00; el servicio lo valida y responde
     * 400 DATOS_INVALIDOS con detail.
     */
    @NotNull(message = "El porcentaje de asignación es obligatorio")
    @Schema(description = "Porcentaje del ahorro destinado a la meta (0–100), no una fracción. "
            + "El conjunto debe sumar 100.00.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentaje;
}
