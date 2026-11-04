package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Creación de una partida (T10c). El nombre se recorta en el servicio. */
@Data
@Schema(description = "Datos para crear una partida del plan.")
public class PartidaCreacionDTO {

    @NotBlank(message = "El nombre de la partida es obligatorio")
    @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
    @Schema(description = "Nombre libre de la partida (1 a 80 caracteres).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "ID de la categoría del catálogo (distinta de SUELDO y AHORRO). "
            + "Obligatoria salvo cuando se vincula una deuda, en cuyo caso se fuerza DEUDAS.")
    private Long categoriaId;

    @Digits(integer = 13, fraction = 2,
            message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @DecimalMin(value = "0", message = "El monto planificado no puede ser negativo")
    @Schema(description = "Monto planificado con escala 2. Prohibido si se vincula una deuda.")
    private BigDecimal montoPlanificado;

    @Schema(description = "ID de un grupo del usuario; null deja la partida sin grupo.")
    private Long grupoId;

    @Schema(description = "ID de una deuda a vincular. El monto y la categoría se derivan de la cuota.")
    private Long deudaId;
}
