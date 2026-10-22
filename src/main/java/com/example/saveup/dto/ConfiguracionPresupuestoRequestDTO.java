package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Configuración 50/30/20 del usuario. Los tres porcentajes deben sumar 100.00.")
public class ConfiguracionPresupuestoRequestDTO {

    @NotNull(message = "El porcentaje de necesidades es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de necesidades debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de necesidades no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de necesidades debe tener como máximo 3 enteros y 2 decimales")
    @Schema(description = "Porcentaje para necesidades (0–100), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeNecesidades;

    @NotNull(message = "El porcentaje de deseos es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de deseos debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de deseos no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de deseos debe tener como máximo 3 enteros y 2 decimales")
    @Schema(description = "Porcentaje para deseos (0–100), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeDeseos;

    @NotNull(message = "El porcentaje de ahorro es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje de ahorro debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje de ahorro no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de ahorro debe tener como máximo 3 enteros y 2 decimales")
    @Schema(description = "Porcentaje para ahorro (0–100), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeAhorro;

    @Schema(description = "Indica si la configuración está activa.", defaultValue = "true")
    private Boolean activo = true;

    /**
     * Bandera para habilitar/deshabilitar la ejecución física de abonos automáticos en metas.
     * Si es false, el presupuesto se mantiene como guía de control y visualización de gastos,
     * sin crear movimientos contables de ABONO_META ni debitar del saldo líquido principal.
     */
    @Schema(description = "Si es true, el ahorro se ejecuta automáticamente como abonos a metas.",
            defaultValue = "false")
    private Boolean automatizarAhorroEnMetas = false;

    @Valid
    @Schema(description = "Distribución opcional del ahorro entre metas.")
    private List<AsignacionPresupuestoDTO> asignaciones;
}
