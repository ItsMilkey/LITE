package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoEjecucion;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Ejecución por categoría dentro del plan (T10c). El campo {@code real} suma
 * todos los gastos de la categoría en el mes, tengan o no partida.
 */
@Data
@Schema(description = "Ejecución de una categoría dentro del plan del mes.")
public class CategoriaPlanDTO {

    @Schema(description = "ID de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @Schema(description = "Código estable de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigo;

    @Schema(description = "Tipo por defecto de la categoría (NECESIDAD, DESEO, AHORRO u OTROS).")
    private TipoPresupuesto tipo;

    @Schema(description = "Suma de las partidas de la categoría.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal planificado;

    @Schema(description = "Gasto real de la categoría en el mes, con o sin partida.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal real;

    @Schema(description = "planificado − real; negativo si se pasó.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal diferencia;

    @Schema(description = "real × 100 ÷ planificado, truncado; null si el plan es 0.")
    private BigDecimal porcentajeUsado;

    @Schema(description = "Estado informativo de la categoría.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoEjecucion estado;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Gasto histórico de referencia; solo con incluirReferencias=true.")
    private ReferenciaCategoriaDTO referencia;
}
