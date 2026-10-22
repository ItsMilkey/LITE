package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Confirmación de identidad para DELETE /api/v1/usuarios/me (T9.2).
 * Exige la contraseña actual: una sesión abandonada en un dispositivo
 * prestado no debe poder destruir la cuenta.
 */
@Schema(description = "Cuerpo de DELETE /api/v1/usuarios/me: confirmación del usuario antes del borrado.")
public record UsuarioEliminacionDTO(
        @Schema(description = "Contraseña actual del usuario; el borrado se rechaza si no coincide.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "La contraseña es obligatoria para eliminar la cuenta")
        String contrasena) {
}
