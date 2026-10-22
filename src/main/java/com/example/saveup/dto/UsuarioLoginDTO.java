package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UsuarioLoginDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del correo no es válido")
    @Schema(description = "Correo electrónico registrado.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Schema(description = "Contraseña del usuario.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contrasena;
}