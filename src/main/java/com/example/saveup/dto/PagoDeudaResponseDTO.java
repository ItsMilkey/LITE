package com.example.saveup.dto;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class PagoDeudaResponseDTO {
    private Long id;
    private Date fecha;
    private BigDecimal monto;
    private BigDecimal capital;
    private BigDecimal interes;
    private BigDecimal costos;
    private String descripcion;
}
