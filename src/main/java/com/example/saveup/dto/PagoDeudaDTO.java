package com.example.saveup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PagoDeudaDTO {

    @NotNull(message = "El monto del pago es obligatorio")
    @Positive(message = "El monto del pago debe ser un número positivo")
    private Double monto;

    @NotBlank(message = "La descripción del pago es obligatoria")
    private String descripcion;
}

