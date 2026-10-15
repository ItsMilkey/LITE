package com.example.saveup.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Datos editables de un movimiento (T4). La fecha NO es editable
 * (updatable = false en la entidad); el tipo tampoco se cambia aquí.
 *
 * <p>Si {@code categoriaId} es null se conserva la categoría actual del
 * movimiento; si viene un id, se reemplaza.
 */
@Data
public class MovimientoEdicionDTO {

    @NotNull(message = "El monto no puede ser nulo")
    @Digits(integer = 17, fraction = 2, message = "El monto debe tener como máximo 17 enteros y 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 100, message = "La descripción no puede superar los 100 caracteres")
    private String descripcion;

    private Long categoriaId;
}
