package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Saldo actual del usuario autenticado. */
@Schema(description = "Saldo actual calculado como la suma de los movimientos del usuario.")
public record SaldoResponseDTO(
        @Schema(description = "Saldo en pesos chilenos, con escala 2. Puede ser negativo.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal saldo) {
}
