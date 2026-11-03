package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

/** Ingreso de referencia del mes (T10c). {@code monto: null} borra el valor. */
@Data
@Schema(description = "Ingreso de referencia del plan; null para limpiarlo.")
public class IngresoReferenciaDTO {

    @Digits(integer = 13, fraction = 2,
            message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @DecimalMin(value = "0", message = "El ingreso de referencia no puede ser negativo")
    @Schema(description = "Monto del ingreso de referencia, o null para quitarlo.")
    private BigDecimal monto;
}
