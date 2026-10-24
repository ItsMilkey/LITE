package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Baja de un dispositivo push (T9.6): se identifica por su token de FCM. */
@Schema(description = "Token del dispositivo que se deja de notificar.")
public record DispositivoPushBajaDTO(

        @Schema(description = "Token de Firebase Cloud Messaging del dispositivo.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank(message = "El token es obligatorio")
        @Size(max = 255, message = "El token no puede superar los 255 caracteres")
        String token) {
}
