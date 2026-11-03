package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Un cambio de monto absoluto dentro del lote (T10c). */
@Data
@Schema(description = "Nuevo monto planificado de una partida.")
public class CambioMontoDTO {

    @NotNull(message = "El partidaId es obligatorio")
    @Schema(description = "ID de la partida a actualizar.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long partidaId;

    @NotNull(message = "El monto es obligatorio")
    @Digits(integer = 13, fraction = 2,
            message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @DecimalMin(value = "0", message = "El monto planificado no puede ser negativo")
    @Schema(description = "Monto absoluto (no incremento).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;
}
