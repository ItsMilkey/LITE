package com.example.saveup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejecución general 50/30/20 de un mes dentro del historial (T5). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPresupuestoDTO {
    private Integer anio;
    private Integer mes;
    private EjecucionPresupuestoDTO ejecucion;
}
