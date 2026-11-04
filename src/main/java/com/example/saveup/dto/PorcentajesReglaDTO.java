package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** Tría {necesidades, deseos, ahorro}; lo usan `actual` y `sugerencia` (T10d, sección 5). */
@Data
@Schema(description = "Porcentajes de la regla: necesidades, deseos y ahorro.")
public class PorcentajesReglaDTO {

    @Schema(description = "Porcentaje de necesidades.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal necesidades;

    @Schema(description = "Porcentaje de deseos.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal deseos;

    @Schema(description = "Porcentaje de ahorro.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal ahorro;

    public PorcentajesReglaDTO() {
    }

    public PorcentajesReglaDTO(BigDecimal necesidades, BigDecimal deseos, BigDecimal ahorro) {
        this.necesidades = necesidades;
        this.deseos = deseos;
        this.ahorro = ahorro;
    }
}
