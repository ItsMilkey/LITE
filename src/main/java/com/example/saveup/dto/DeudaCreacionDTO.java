package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoDeuda;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Datos para crear o editar una deuda.")
public class DeudaCreacionDTO {

    @NotBlank(message = "El nombre de la deuda es obligatorio")
    @Schema(description = "Nombre de la deuda.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Descripción opcional de la deuda.")
    private String descripcion; // Opcional, sin validación de no nulo

    @Schema(description = "Tipo de deuda. Si se omite, el servicio usa OTRO.")
    private TipoDeuda tipoDeuda;

    @Min(value = 0, message = "Las cuotas pagadas previas deben ser mayores o iguales a cero")
    @Schema(description = "Cuotas ya pagadas antes de registrar la deuda.", defaultValue = "0")
    private Integer cuotasPagadasPrevias = 0;

    @Valid
    @Schema(description = "Condiciones del crédito. Obligatorio en la práctica para calcular la tabla.")
    private CondicionesCreditoInput condiciones;

    @Positive(message = "El monto total debe ser un número positivo")
    @Digits(integer = 13, fraction = 2, message = "El monto total debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Monto total a pagar, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoTotal;

    @Positive(message = "La cantidad de cuotas debe ser un número positivo")
    @Schema(description = "Cantidad total de cuotas.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer cantidadCuotas;
}