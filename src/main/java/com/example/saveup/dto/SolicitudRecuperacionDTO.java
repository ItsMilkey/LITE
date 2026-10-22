package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de POST /api/v1/auth/recuperar-contrasena (T9.3). La respuesta del
 * endpoint es siempre genérica para no revelar si el correo existe; este DTO
 * solo valida el formato de entrada.
 */
@Schema(description = "Solicitud de recuperación de contraseña.")
public record SolicitudRecuperacionDTO(
        @Schema(description = "Correo con el que se registró la cuenta.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El formato del correo no es válido")
        String email) {
}
