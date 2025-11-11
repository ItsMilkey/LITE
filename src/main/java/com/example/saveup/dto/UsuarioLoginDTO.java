package com.example.saveup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;@Data
public class UsuarioLoginDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del correo no es válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;
}