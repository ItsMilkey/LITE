package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Categoría de movimiento.")
public class CategoriaDTO {
    @Schema(description = "Identificador de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Nombre visible de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Identificador del icono usado por el cliente.")
    private String iconId;

    @Schema(description = "Color de la categoría en formato #RRGGBB.")
    private String colorHex;

    @Schema(description = "Tipo de presupuesto al que pertenece la categoría.")
    private com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto;
}
