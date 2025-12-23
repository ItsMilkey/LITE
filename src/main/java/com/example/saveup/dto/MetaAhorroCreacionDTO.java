package com.example.saveup.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.Date;

@Data
public class MetaAhorroCreacionDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Positive(message = "El monto objetivo debe ser positivo")
    private Double montoObjetivo;

    @Future(message = "La fecha límite debe ser en el futuro")
    private Date fechaLimite;

    @NotBlank(message = "El RUT del usuario es obligatorio")
    private String usuarioRut;
}