package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoMovimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MovimientoRegistroDTO {

    @NotNull(message = "El monto no puede ser nulo")
    private Double monto;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @NotNull(message = "El tipo de movimiento es obligatorio")
    private TipoMovimiento tipoMovimiento;

    @NotBlank(message = "El RUT del usuario es obligatorio")
    private String usuarioRut;

    // Opcional: Se usarán cuando implementemos deudas y metas
    private Long deudaId;
    private Long metaId;

    private Long categoriaId; // ID de la categoría seleccionada

    private Boolean aplicarPresupuesto = false; // Nuevo campo para Smart-Split
}