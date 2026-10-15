package com.example.saveup.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejecución general 50/30/20 de un mes dentro del historial (T5). */
@Data
@NoArgsConstructor
public class HistorialPresupuestoDTO {
    private Integer anio;
    private Integer mes;
    private EjecucionPresupuestoDTO ejecucion;

    public HistorialPresupuestoDTO(Integer anio, Integer mes, EjecucionPresupuestoDTO ejecucion) {
        this.anio = anio;
        this.mes = mes;
        this.ejecucion = ejecucion;
    }
}
