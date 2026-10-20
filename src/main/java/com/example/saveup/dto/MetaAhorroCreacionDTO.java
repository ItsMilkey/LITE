package com.example.saveup.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MetaAhorroCreacionDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Positive(message = "El monto objetivo debe ser positivo")
    @Digits(integer = 13, fraction = 2, message = "El monto objetivo debe tener como máximo 13 enteros y 2 decimales")
    private BigDecimal montoObjetivo;

    @Future(message = "La fecha límite debe ser en el futuro")
    private LocalDate fechaLimite;
}
