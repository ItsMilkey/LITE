package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Edición parcial de un grupo: al menos nombre u orden. */
@Data
@Schema(description = "Edición parcial de un grupo; al menos un campo es obligatorio.")
public class GrupoPlanEdicionDTO {

    @Schema(description = "Nuevo nombre del grupo.")
    private String nombre;

    @Schema(description = "Nuevo orden de presentación.")
    private Integer orden;
}
