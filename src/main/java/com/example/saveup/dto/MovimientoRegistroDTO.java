package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoMovimiento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Datos para registrar un movimiento.")
public class MovimientoRegistroDTO {

    @NotNull(message = "El monto no puede ser nulo")
    @Digits(integer = 13, fraction = 2, message = "El monto debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Monto con escala 2. El signo final lo impone el tipo de movimiento.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;

    @NotBlank(message = "La descripción es obligatoria")
    @Schema(description = "Descripción del movimiento.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String descripcion;

    @NotNull(message = "El tipo de movimiento es obligatorio")
    @Schema(description = "Tipo de movimiento. Define el signo del monto.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoMovimiento tipoMovimiento;

    // Opcional: se usa cuando el movimiento es un pago de deuda
    @Schema(description = "ID de la deuda cuando el tipo es PAGO_DEUDA.")
    private Long deudaId;

    // Opcional: se usará cuando se implemente el vínculo explícito con metas (ver T3/T4)
    @Schema(description = "ID de la meta vinculada, si corresponde.")
    private Long metaId;

    @Schema(description = "ID de la categoría seleccionada.")
    private Long categoriaId; // ID de la categoría seleccionada

    @Schema(description = "Si es true, aplica Smart-Split al ingreso.", defaultValue = "false")
    private Boolean aplicarPresupuesto = false; // Nuevo campo para Smart-Split
}