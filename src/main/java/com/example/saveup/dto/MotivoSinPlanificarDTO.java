package com.example.saveup.dto;

import com.example.saveup.model.enums.MotivoSinPlanificar;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** Desglose de lo no planificado por motivo (T10c). */
@Data
@Schema(description = "Monto y cantidad de movimientos sin planificar por motivo.")
public class MotivoSinPlanificarDTO {

    @Schema(description = "Motivo por el que el gasto no se imputó a una partida.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MotivoSinPlanificar motivo;

    @Schema(description = "Monto sin planificar con ese motivo, en valor absoluto.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;

    @Schema(description = "Cantidad de movimientos con ese motivo.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int movimientos;
}
