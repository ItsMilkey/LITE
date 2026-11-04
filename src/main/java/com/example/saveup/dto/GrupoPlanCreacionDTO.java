package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Creación de un grupo del plan; el nombre se recorta en el servicio. */
@Data
@Schema(description = "Datos para crear un grupo de partidas.")
public class GrupoPlanCreacionDTO {

    @NotBlank(message = "El nombre del grupo es obligatorio")
    @Size(max = 60, message = "El nombre del grupo no puede superar los 60 caracteres")
    @Schema(description = "Nombre del grupo (1 a 60 caracteres).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Min(value = 0, message = "El orden no puede ser negativo")
    @Schema(description = "Orden de presentación; si se omite usa 0.")
    private Integer orden;
}
