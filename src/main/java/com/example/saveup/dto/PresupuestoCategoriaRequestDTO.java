package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo del PUT /api/v1/presupuestos/categorias/me: reemplaza el conjunto de
 * límites del usuario. Se envuelve la lista en un DTO para que {@code @Valid}
 * realmente casque a cada elemento (un {@code @Valid} sobre una {@code List}
 * como body no propaga la validación a los ítems).
 */
@Data
@NoArgsConstructor
public class PresupuestoCategoriaRequestDTO {

    @Valid
    @NotNull(message = "La lista 'categorias' es obligatoria")
    @ArraySchema(
            arraySchema = @Schema(description = "Conjunto completo de límites; reemplaza los existentes.",
                    requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = PresupuestoCategoriaItemDTO.class))
    private List<PresupuestoCategoriaItemDTO> categorias = new ArrayList<>();
}
