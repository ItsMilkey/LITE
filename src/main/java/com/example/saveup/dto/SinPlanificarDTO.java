package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Gasto real que no se imputó a ninguna partida (T10c, sección 5). */
@Data
@Schema(description = "Gasto del mes que quedó fuera de las partidas, con su motivo.")
public class SinPlanificarDTO {

    @Schema(description = "Monto total sin planificar en valor absoluto.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;

    @Schema(description = "Cantidad de movimientos sin planificar.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int movimientos;

    @Schema(description = "Desglose por motivo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MotivoSinPlanificarDTO> porMotivo;
}
