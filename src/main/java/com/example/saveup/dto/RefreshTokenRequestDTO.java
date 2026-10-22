package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de POST /api/v1/auth/refresh y POST /api/v1/auth/logout (T9.1).
 */
@Schema(description = "Refresh token entregado por el login o por un refresh anterior.")
public record RefreshTokenRequestDTO(
        @Schema(description = "Token de sesión rotativo; en el refresh se consume y se entrega uno nuevo.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken) {
}
