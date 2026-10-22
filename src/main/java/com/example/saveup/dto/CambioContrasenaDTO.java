package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 20, message = "La contraseña debe tener entre 8 y 20 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "La contraseña debe contener al menos una letra y un número")
        String nuevaContrasena) {
}
