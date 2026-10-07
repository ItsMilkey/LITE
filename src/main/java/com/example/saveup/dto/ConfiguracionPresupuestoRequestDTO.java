package com.example.saveup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionPresupuestoRequestDTO {

    @NotNull(message = "El porcentaje de necesidades es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de necesidades debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de necesidades no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de necesidades debe tener como máximo 3 enteros y 2 decimales")
    private BigDecimal porcentajeNecesidades;

    @NotNull(message = "El porcentaje de deseos es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de deseos debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de deseos no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de deseos debe tener como máximo 3 enteros y 2 decimales")
    private BigDecimal porcentajeDeseos;

    @NotNull(message = "El porcentaje de ahorro es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de ahorro debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de ahorro no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de ahorro debe tener como máximo 3 enteros y 2 decimales")
    private BigDecimal porcentajeAhorro;

    private Boolean activo = true;

    /**
     * Bandera para habilitar/deshabilitar la ejecución física de abonos automáticos en metas.
     * Si es false, el presupuesto se mantiene como guía de control y visualización de gastos,
     * sin crear movimientos contables de ABONO_META ni debitar del saldo líquido principal.
     */
    private Boolean automatizarAhorroEnMetas = false;

    @Valid
    private List<AsignacionPresupuestoDTO> asignaciones;
}
