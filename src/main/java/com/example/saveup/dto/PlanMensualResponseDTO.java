package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plan completo de un mes (T10c). Todas las operaciones que modifican el plan
 * devuelven este DTO para que el cliente se reconcilie con un solo objeto.
 * Un mes sin plan responde 200 con {@code existe: false}, nunca 404.
 */
@Data
@Schema(description = "Plan del mes con su resumen, partidas y ejecución por categoría.")
public class PlanMensualResponseDTO {

    @Schema(description = "Mes en formato yyyy-MM.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mes;

    @Schema(description = "false si el usuario todavía no creó un plan para ese mes.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean existe;

    @Schema(description = "Ingreso de referencia del mes; null si no se definió.")
    private BigDecimal ingresoReferencia;

    @Schema(description = "Resumen del plan y del gasto real del mes.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ResumenPlanDTO resumen;

    @Schema(description = "Partidas del plan, en orden de presentación.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PartidaPlanDTO> partidas;

    @Schema(description = "Ejecución de las categorías con partidas o gasto real en el mes.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CategoriaPlanDTO> porCategoria;
}
