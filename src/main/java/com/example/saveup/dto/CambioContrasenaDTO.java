package com.example.saveup.dto;

import com.example.saveup.validation.ContrasenaValida;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de PUT /api/v1/usuarios/me/contrasena (T9.3). La sesión actual queda
 * como esté: lo que se revoca son todas las sesiones activas (refresh tokens),
 * así que la app pedirá iniciar sesión con la nueva contraseña.
 */
@Schema(description = "Cambio de contraseña con sesión activa.")
public record CambioContrasenaDTO(
        @Schema(description = "Contraseña actual; el cambio se rechaza si no coincide.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "La contraseña actual es obligatoria")
        String contrasenaActual,
        @Schema(description = "Nueva contraseña: 8 a 20 caracteres, con al menos una letra y un número.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 8, maxLength = 20,
                pattern = "^(?=.*[A-Za-z])(?=.*\\d).*$")
        @ContrasenaValida
        String nuevaContrasena) {
}
