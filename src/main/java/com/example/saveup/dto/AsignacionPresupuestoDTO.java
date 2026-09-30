package com.example.saveup.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionPresupuestoDTO {

    @NotNull(message = "El ID de la meta es obligatorio")
    private Long metaId;

    /**
     * Fraccion porcentual del monto de ahorro a destinar a esta meta (0.0 - 100.0).
     */
    @NotNull(message = "El porcentaje de asignacion es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje no puede exceder 100")
    private BigDecimal porcentaje;
}
