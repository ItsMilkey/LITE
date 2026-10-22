package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Límite mensual configurado por el usuario para una categoría (respuesta T5). */
@Data
@NoArgsConstructor
@Schema(description = "Límite mensual configurado para una categoría.")
public class PresupuestoCategoriaDTO {
    @Schema(description = "ID de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @Schema(description = "Nombre de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoria;

    @Schema(description = "Límite mensual, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal limiteMensual;

    public PresupuestoCategoriaDTO(Long categoriaId, String categoria, BigDecimal limiteMensual) {
        this.categoriaId = categoriaId;
        this.categoria = categoria;
        this.limiteMensual = limiteMensual;
    }
}
