package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Monto y descripción de un abono o retiro de meta.")
public class AbonoRetiroDTO {
    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser positivo")
    @Digits(integer = 13, fraction = 2, message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Monto positivo, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;

    @NotBlank(message = "La descripción es obligatoria")
    @Schema(description = "Descripción de la operación.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String descripcion;
}