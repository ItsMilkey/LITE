package com.example.saveup.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PagoDeudaDTO {

    @NotNull(message = "El monto del pago es obligatorio")
    @Positive(message = "El monto del pago debe ser un número positivo")
    @Digits(integer = 17, fraction = 2, message = "El monto del pago debe tener como máximo 17 enteros y 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "La descripción del pago es obligatoria")
    private String descripcion;
}
