package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Creación de una partida (T10c). El nombre se valida en el servicio tras recortarlo. */
@Data
@Schema(description = "Datos para crear una partida del plan.")
public class PartidaCreacionDTO {

    @Schema(description = "Nombre libre de la partida (1 a 80 caracteres).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @NotNull(message = "La categoría es obligatoria")
    @Schema(description = "ID de la categoría del catálogo (distinta de SUELDO y AHORRO).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @NotNull(message = "El monto planificado es obligatorio")
    @Digits(integer = 13, fraction = 2,
            message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @DecimalMin(value = "0", message = "El monto planificado no puede ser negativo")
    @Schema(description = "Monto planificado con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoPlanificado;
}
