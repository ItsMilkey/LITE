package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejecución general 50/30/20 de un mes dentro del historial (T5). */
@Data
@NoArgsConstructor
@Schema(description = "Entrada mensual del historial de presupuesto.")
public class HistorialPresupuestoDTO {
    @Schema(description = "Año del mes.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer anio;

    @Schema(description = "Mes numérico (1–12).", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer mes;

    @Schema(description = "Ejecución presupuestaria del mes.", requiredMode = Schema.RequiredMode.REQUIRED)
    private EjecucionPresupuestoDTO ejecucion;

    public HistorialPresupuestoDTO(Integer anio, Integer mes, EjecucionPresupuestoDTO ejecucion) {
        this.anio = anio;
        this.mes = mes;
        this.ejecucion = ejecucion;
    }
}
