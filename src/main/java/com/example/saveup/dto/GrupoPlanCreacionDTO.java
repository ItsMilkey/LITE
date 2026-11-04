package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Creación de un grupo del plan; el nombre se valida en el servicio. */
@Data
@Schema(description = "Datos para crear un grupo de partidas.")
public class GrupoPlanCreacionDTO {

    @Schema(description = "Nombre del grupo (1 a 60 caracteres).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Orden de presentación; si se omite usa 0.")
    private Integer orden;
}
