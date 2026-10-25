package com.example.saveup.dto;

import com.example.saveup.validation.ContrasenaValida;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de POST /api/v1/auth/restablecer-contrasena (T9.3). Las reglas de la
 * nueva contraseña son las mismas del registro (8–64 caracteres, con letras
 * y números, hasta 72 bytes en UTF-8).
 */
@Schema(description = "Restablecimiento de contraseña con token de un solo uso.")
public record RestablecerContrasenaDTO(
        @Schema(description = "Token recibido por correo; vale 1 hora y un solo uso.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El token es obligatorio")
        String token,
        @Schema(description = "Nueva contraseña: 8 a 64 caracteres, con al menos una letra y un número, "
                + "de hasta 72 bytes en UTF-8.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 8, maxLength = 64,
                pattern = "^(?=.*[A-Za-z])(?=.*\\d).*$")
        @ContrasenaValida
        String nuevaContrasena) {
}
