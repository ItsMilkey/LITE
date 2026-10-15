package com.example.saveup.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Límite mensual configurado por el usuario para una categoría (respuesta T5). */
@Data
@NoArgsConstructor
public class PresupuestoCategoriaDTO {
    private Long categoriaId;
    private String categoria;
    private BigDecimal limiteMensual;

    public PresupuestoCategoriaDTO(Long categoriaId, String categoria, BigDecimal limiteMensual) {
        this.categoriaId = categoriaId;
        this.categoria = categoria;
        this.limiteMensual = limiteMensual;
    }
}
