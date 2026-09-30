package com.example.saveup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AbonoRetiroDTO {
    @NotNull
    @Positive(message = "El monto debe ser positivo")
    private BigDecimal monto;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;
}