package com.example.saveup.dto;

import com.example.saveup.validation.ContrasenaValida;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

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
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 8, maxLength = 20,
                pattern = "^(?=.*[A-Za-z])(?=.*\\d).*$")
        @ContrasenaValida
        String nuevaContrasena) {
}
