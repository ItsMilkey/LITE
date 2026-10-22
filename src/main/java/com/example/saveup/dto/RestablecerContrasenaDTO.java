package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/restablecer-contrasena (T9.3). Las reglas de la
 * nueva contraseña son las mismas del registro (8–20 caracteres, con letras
 * y números).
 */
@Schema(description = "Restablecimiento de contraseña con token de un solo uso.")
public record RestablecerContrasenaDTO(
        @Schema(description = "Token recibido por correo; vale 1 hora y un solo uso.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El token es obligatorio")
        String token,
        @Schema(description = "Nueva contraseña: 8 a 20 caracteres, con al menos una letra y un número.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 20, message = "La contraseña debe tener entre 8 y 20 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "La contraseña debe contener al menos una letra y un número")
        String nuevaContrasena) {
}
