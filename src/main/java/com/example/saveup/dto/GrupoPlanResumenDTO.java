package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumen de un grupo dentro del plan del mes (T10d, sección 6). El grupo
 * "Sin grupo" llega con {@code id} nulo y va al final.
 */
@Data
@Schema(description = "Resumen de un grupo dentro del plan del mes.")
public class GrupoPlanResumenDTO {

    @Schema(description = "ID del grupo; null para el grupo \"Sin grupo\".")
    private Long id;

    @Schema(description = "Nombre del grupo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Orden del grupo; null para \"Sin grupo\".")
    private Integer orden;

    @Schema(description = "Suma de las partidas del grupo.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal planificado;

    @Schema(description = "planificado ÷ planificadoTotal × 100, HALF_UP; null si el total es 0.")
    private BigDecimal porcentajeDelPlanificado;

    @Schema(description = "Gasto real imputado a las partidas del grupo.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal real;

    @Schema(description = "IDs de las partidas del grupo, en orden de presentación.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> partidaIds;
}
