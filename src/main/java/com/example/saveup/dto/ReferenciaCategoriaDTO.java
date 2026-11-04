package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Referencia histórica de gasto de una categoría (T10d, sección 8). Solo se
 * incluye cuando el cliente pide {@code incluirReferencias=true}.
 */
@Data
@Schema(description = "Gasto histórico de referencia de una categoría.")
public class ReferenciaCategoriaDTO {

    @Schema(description = "Gasto real del mes inmediatamente anterior; null si no hay historial.")
    private BigDecimal mesAnterior;

    @Schema(description = "Promedio de hasta 3 meses cerrados previos, HALF_UP; null sin historial.")
    private BigDecimal promedio3Meses;
}
