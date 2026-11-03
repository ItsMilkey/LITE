package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoEjecucion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/** Partida del plan con su ejecución real en el mes (T10c). */
@Data
@Schema(description = "Partida del plan y su gasto real del mes.")
public class PartidaPlanDTO {

    @Schema(description = "Identificador de la partida.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Nombre libre de la partida.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "ID de la categoría del catálogo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @Schema(description = "Código estable de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoriaCodigo;

    @Schema(description = "Monto planificado con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoPlanificado;

    @Schema(description = "Gasto real imputado en valor absoluto.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal real;

    @Schema(description = "montoPlanificado − real; negativo si se pasó.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal diferencia;

    @Schema(description = "real × 100 ÷ montoPlanificado, truncado; null si el plan es 0.")
    private BigDecimal porcentajeUsado;

    @Schema(description = "Estado informativo del plan.", requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoEjecucion estado;

    @Schema(description = "Posición de la partida dentro del plan.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer orden;

    @Schema(description = "Última modificación de la partida, en ISO-8601 UTC.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Date actualizadoEn;
}
