package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Mensaje simple de confirmación. */
@Schema(description = "Confirmación legible de una operación.")
public record MensajeResponseDTO(
        @Schema(description = "Mensaje de confirmación en español.", requiredMode = Schema.RequiredMode.REQUIRED)
        String message) {
}
