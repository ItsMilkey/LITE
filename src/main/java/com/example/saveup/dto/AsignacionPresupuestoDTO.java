package com.example.saveup.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionPresupuestoDTO {

    @NotNull(message = "El ID de la meta es obligatorio")
    private Long metaId;

    @NotNull(message = "El porcentaje de asignación es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de asignación debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de asignación no puede exceder 100")
    private Double porcentaje;
}
