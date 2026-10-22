package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Un pago real de una deuda con su desglose (capital, interés y costos).
 *
 * <p>El desglose no se persiste: {@link com.example.saveup.service.DeudaService}
 * lo recalcula en memoria con {@link com.example.saveup.service.finanzas.ImputacionPagos}
 * como la imputación acumulada tras ese pago menos la del pago anterior (regla 40).
 * El monto se expone en positivo; el movimiento almacenado es negativo.
 */
@Data
@NoArgsConstructor
@Schema(description = "Pago de deuda con su desglose de capital, interés y costos.")
public class PagoDeudaResponseDTO {
    @Schema(description = "Identificador del pago.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Fecha del pago en ISO-8601 UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Date fecha;

    @Schema(description = "Monto del pago en positivo, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;

    @Schema(description = "Parte imputada a capital, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal capital;

    @Schema(description = "Parte imputada a intereses, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal interes;

    @Schema(description = "Parte imputada a costos, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal costos;

    @Schema(description = "Descripción del pago.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String descripcion;

    public PagoDeudaResponseDTO(Long id, Date fecha, BigDecimal monto, BigDecimal capital, BigDecimal interes,
                                BigDecimal costos, String descripcion) {
        this.id = id;
        this.fecha = fecha;
        this.monto = monto;
        this.capital = capital;
        this.interes = interes;
        this.costos = costos;
        this.descripcion = descripcion;
    }
}
