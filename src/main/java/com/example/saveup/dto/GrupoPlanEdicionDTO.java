package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Edición parcial de un grupo: al menos nombre u orden. */
@Data
@Schema(description = "Edición parcial de un grupo; al menos un campo es obligatorio.")
public class GrupoPlanEdicionDTO {

    @Size(max = 60, message = "El nombre del grupo no puede superar los 60 caracteres")
    @Schema(description = "Nuevo nombre del grupo.")
    private String nombre;

    @Min(value = 0, message = "El orden no puede ser negativo")
    @Schema(description = "Nuevo orden de presentación.")
    private Integer orden;
}
