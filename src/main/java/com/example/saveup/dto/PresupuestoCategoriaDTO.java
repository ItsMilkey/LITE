package com.example.saveup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Límite mensual configurado por el usuario para una categoría (respuesta T5). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoCategoriaDTO {
    private Long categoriaId;
    private String categoria;
    private BigDecimal limiteMensual;
}
