package com.example.saveup.dto;

import java.math.BigDecimal;

/** Saldo actual del usuario autenticado. */
public record SaldoResponseDTO(BigDecimal saldo) {
}
