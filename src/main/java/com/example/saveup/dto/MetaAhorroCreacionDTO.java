package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Datos para crear o editar una meta de ahorro.")
public class MetaAhorroCreacionDTO {
    @NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre de la meta.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Positive(message = "El monto objetivo debe ser positivo")
    @Digits(integer = 13, fraction = 2, message = "El monto objetivo debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Monto objetivo, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoObjetivo;

    @Future(message = "La fecha límite debe ser en el futuro")
    @Schema(description = "Fecha límite en formato yyyy-MM-dd.")
    private LocalDate fechaLimite;
}
