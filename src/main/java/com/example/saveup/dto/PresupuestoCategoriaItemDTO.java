package com.example.saveup.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ítem del PUT /api/presupuestos/categorias/me. El formato se valida con Bean
 * Validation (@NotNull / @Positive); la invariante de negocio (categoría
 * existente, sin repetidos) se revalida en el servicio (400).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoCategoriaItemDTO {

    @NotNull(message = "La categoría es obligatoria")
    private Long categoriaId;

    @NotNull(message = "El límite mensual es obligatorio")
    @Positive(message = "El límite mensual debe ser mayor a 0")
    private BigDecimal limiteMensual;
}
