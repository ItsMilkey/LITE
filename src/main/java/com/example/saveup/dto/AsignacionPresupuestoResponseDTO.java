package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Asignación del ahorro a una meta.")
public class AsignacionPresupuestoResponseDTO {

    @Schema(description = "Identificador de la meta.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long metaId;

    @Schema(description = "Nombre de la meta.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metaNombre;

    @Schema(description = "Porcentaje del ahorro asignado (0–100), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentaje;

    public AsignacionPresupuestoResponseDTO() {
    }

    public AsignacionPresupuestoResponseDTO(Long metaId, String metaNombre, BigDecimal porcentaje) {
        this.metaId = metaId;
        this.metaNombre = metaNombre;
        this.porcentaje = porcentaje;
    }
}
