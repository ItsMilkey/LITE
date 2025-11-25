package com.example.saveup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DeudaCreacionDTO {

    @NotBlank(message = "El nombre de la deuda es obligatorio")
    private String nombre;

    private String descripcion; // Opcional, sin validación de no nulo

    @NotNull(message = "El monto total es obligatorio")
    @Positive(message = "El monto total debe ser un número positivo")
    private Double montoTotal;

    @NotNull(message = "La cantidad de cuotas es obligatoria")
    @Positive(message = "La cantidad de cuotas debe ser un número positivo")
    private Integer cantidadCuotas;

    @NotBlank(message = "El RUT del usuario es obligatorio")
    private String usuarioRut;
}