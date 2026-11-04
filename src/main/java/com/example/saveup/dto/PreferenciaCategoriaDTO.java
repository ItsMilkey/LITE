package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoPresupuesto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Categoría con su clasificación por tipo para el usuario (T10d, sección 3). */
@Data
@Schema(description = "Categoría del catálogo con su tipo por defecto, preferido y efectivo.")
public class PreferenciaCategoriaDTO {

    @Schema(description = "Identificador de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Nombre de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Código estable de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigo;

    @Schema(description = "Identificador del ícono de la categoría.")
    private String iconId;

    @Schema(description = "Color hexadecimal de la categoría.")
    private String colorHex;

    @Schema(description = "Tipo por defecto del catálogo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoPresupuesto tipoPorDefecto;

    @Schema(description = "Tipo preferido por el usuario; null si no definió uno.")
    private TipoPresupuesto tipoPreferido;

    @Schema(description = "Tipo efectivo: tipoPreferido si existe; si no, tipoPorDefecto.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoPresupuesto tipoEfectivo;
}
