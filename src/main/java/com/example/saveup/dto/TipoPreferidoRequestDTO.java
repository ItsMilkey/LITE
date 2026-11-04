package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoPresupuesto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Cuerpo de PUT /api/v1/categorias/{id}/tipo-preferido (T10d, sección 3). */
@Data
@Schema(description = "Nuevo tipo preferido para la categoría; solo NECESIDAD o DESEO.")
public class TipoPreferidoRequestDTO {

    @NotNull(message = "El tipo preferido es obligatorio")
    @Schema(description = "Tipo preferido: NECESIDAD o DESEO.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoPresupuesto tipo;
}
