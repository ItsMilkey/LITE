package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ítem del PUT /api/v1/presupuestos/categorias/me. El formato se valida con Bean
 * Validation (@NotNull / @Positive); la invariante de negocio (categoría
 * existente, sin repetidos) se revalida en el servicio (400).
 */
@Data
@NoArgsConstructor
public class PresupuestoCategoriaItemDTO {

    @NotNull(message = "La categoría es obligatoria")
    @Schema(description = "ID de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @NotNull(message = "El límite mensual es obligatorio")
    @Positive(message = "El límite mensual debe ser mayor a 0")
    @Digits(integer = 13, fraction = 2, message = "El límite mensual debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Límite mensual positivo, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal limiteMensual;

    public PresupuestoCategoriaItemDTO(Long categoriaId, BigDecimal limiteMensual) {
        this.categoriaId = categoriaId;
        this.limiteMensual = limiteMensual;
    }
}
