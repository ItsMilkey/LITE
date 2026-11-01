package com.example.saveup.dto;

import com.example.saveup.model.enums.MotivoSugerencia;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Categoría sugerida para un texto libre, con su confianza y motivo.")
public class SugerenciaCategoriaDTO {

    @Schema(description = "Identificador de la categoría sugerida.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @Schema(description = "Código estable de la categoría (p. ej. RESTAURANTES_SALIDAS).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigo;

    @Schema(description = "Nombre visible de la categoría.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Confianza de la sugerencia, entre 0.00 y 1.00.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal confianza;

    @Schema(description = "Motivo de la coincidencia.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MotivoSugerencia motivo;
}
