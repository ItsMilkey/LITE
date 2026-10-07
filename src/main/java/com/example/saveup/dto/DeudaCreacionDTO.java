package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoDeuda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DeudaCreacionDTO {

    @NotBlank(message = "El nombre de la deuda es obligatorio")
    private String nombre;

    private String descripcion; // Opcional, sin validación de no nulo

    private TipoDeuda tipoDeuda;

    @Min(value = 0, message = "Las cuotas pagadas previas deben ser mayores o iguales a cero")
    private Integer cuotasPagadasPrevias = 0;

    @Valid
    private CondicionesCreditoDTO condiciones;

    @Positive(message = "El monto total debe ser un número positivo")
    @Digits(integer = 17, fraction = 2, message = "El monto total debe tener como máximo 17 enteros y 2 decimales")
    private BigDecimal montoTotal;

    @Positive(message = "La cantidad de cuotas debe ser un número positivo")
    private Integer cantidadCuotas;
}