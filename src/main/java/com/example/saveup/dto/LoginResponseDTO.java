package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de POST /api/v1/usuarios/login. */
@Schema(description = "Token JWT para autenticar las siguientes solicitudes.")
public record LoginResponseDTO(
        @Schema(description = "JWT que debe enviarse en Authorization: Bearer <token>.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token) {
}
