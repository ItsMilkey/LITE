package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

@Schema(description = "Perfil del usuario autenticado (nunca incluye la contraseña).")
public record UsuarioResponseDTO(
        @Schema(description = "RUT del usuario.", requiredMode = Schema.RequiredMode.REQUIRED)
        String rut,
        @Schema(description = "Nombre del usuario.", requiredMode = Schema.RequiredMode.REQUIRED)
        String nombre,
        @Schema(description = "Apellido del usuario.", requiredMode = Schema.RequiredMode.REQUIRED)
        String apellido,
        @Schema(description = "Correo electrónico.", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Fecha de registro en ISO-8601 UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
        Date fechaRegistro) {
}
