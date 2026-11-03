package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** Resumen del plan del mes (T10c, sección 6). */
@Data
@Schema(description = "Resumen del plan frente al gasto real del mes.")
public class ResumenPlanDTO {

    @Schema(description = "Suma de los montos planificados de las partidas.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal planificadoTotal;

    @Schema(description = "ingresoReferencia − planificadoTotal; null si no hay ingreso.")
    private BigDecimal remanenteLibre;

    @Schema(description = "Porcentaje del ingreso que queda libre; null sin ingreso o si es 0.")
    private BigDecimal margenSeguridadPct;

    @Schema(description = "Indica si el plan supera el ingreso de referencia (informativo).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean excedeIngreso;

    @Schema(description = "Suma de los INGRESO_GENERAL del mes.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal ingresoReal;

    @Schema(description = "Suma en valor absoluto de GASTO_GENERAL y PAGO_DEUDA del mes.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gastoRealTotal;

    @Schema(description = "ingresoReal − gastoRealTotal.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal disponibleReal;

    @Schema(description = "Suma en valor absoluto de los ABONO_META del mes (informativo).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal ahorroRegistrado;

    @Schema(description = "Gasto del mes que no se pudo imputar a una partida.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private SinPlanificarDTO sinPlanificar;
}
