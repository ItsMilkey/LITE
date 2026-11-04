package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Grupo de partidas del usuario (T10d, sección 6). */
@Data
@Schema(description = "Grupo de partidas del plan.")
public class GrupoPlanDTO {

    @Schema(description = "Identificador del grupo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Nombre del grupo (1 a 60 caracteres).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Orden de presentación.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer orden;
}
