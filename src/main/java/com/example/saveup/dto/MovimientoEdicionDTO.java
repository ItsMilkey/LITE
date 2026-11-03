package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Datos editables de un movimiento (T4). La fecha NO es editable
 * (updatable = false en la entidad); el tipo tampoco se cambia aquí.
 *
 * <p>Es una edición parcial: todos los campos son opcionales. El servicio
 * aplica solo los que llegan no nulos; los que vienen en null conservan el
 * valor actual del movimiento. {@code categoriaId} es el modo de cambiar la
 * categoría.
 */
@Data
@Schema(description = "Edición parcial de un movimiento; los campos ausentes conservan su valor.")
public class MovimientoEdicionDTO {

    @Digits(integer = 13, fraction = 2, message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Nuevo monto con escala 2. El signo lo impone el tipo actual.")
    private BigDecimal monto;

    @Size(max = 100, message = "La descripción no puede superar los 100 caracteres")
    @Schema(description = "Nueva descripción del movimiento.")
    private String descripcion;

    @Schema(description = "Nueva categoría del movimiento.")
    private Long categoriaId;

    @Schema(description = "Partida del plan a la que se imputa el gasto (T10c).")
    private Long partidaId;

    /**
     * El PUT es parcial y los nulos significan "no enviado": para quitar la
     * partida hay que mandar {@code true} explícitamente (T10c).
     */
    @Schema(description = "true para quitar la partida actual del movimiento.")
    private Boolean desvincularPartida;
}
