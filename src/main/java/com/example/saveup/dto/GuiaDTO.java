package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** Guía 50/30/20 del usuario (T5). Null y diferencias nulas si no la tiene. */
@Data
@Schema(description = "Guía de porcentajes del usuario; null si no tiene configuración.")
public class GuiaDTO {

    @Schema(description = "Porcentaje de necesidades de la guía.")
    private BigDecimal necesidades;

    @Schema(description = "Porcentaje de deseos de la guía.")
    private BigDecimal deseos;

    @Schema(description = "Porcentaje de ahorro de la guía.")
    private BigDecimal ahorro;

    public GuiaDTO() {
    }

    public GuiaDTO(BigDecimal necesidades, BigDecimal deseos, BigDecimal ahorro) {
        this.necesidades = necesidades;
        this.deseos = deseos;
        this.ahorro = ahorro;
    }
}
