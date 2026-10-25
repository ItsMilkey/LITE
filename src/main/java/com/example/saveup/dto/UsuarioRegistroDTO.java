package com.example.saveup.dto;

import com.example.saveup.validation.ContrasenaValida;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioRegistroDTO {

    @NotBlank(message = "El RUT es obligatorio")
    @Pattern(regexp = "^\\d{7,8}-[\\dkK]{1}$", message = "El RUT debe tener un formato válido (ej: 12345678-9)")
    @Schema(description = "RUT chileno sin puntos, con guion y dígito verificador (ej: 12345678-9).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String rut;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Schema(description = "Nombre del usuario.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    @Schema(description = "Apellido del usuario.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String apellido;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El formato del correo no es válido")
    @Schema(description = "Correo electrónico único.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @ContrasenaValida
    @Schema(description = "Contraseña de 8 a 64 caracteres, con al menos una letra y un número, "
            + "de hasta 72 bytes en UTF-8.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 8, maxLength = 64,
            pattern = "^(?=.*[A-Za-z])(?=.*\\d).*$")
    private String contrasena;
}
