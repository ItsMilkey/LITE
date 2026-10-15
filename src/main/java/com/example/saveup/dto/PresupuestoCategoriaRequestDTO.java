package com.example.saveup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo del PUT /api/presupuestos/categorias/me: reemplaza el conjunto de
 * límites del usuario. Se envuelve la lista en un DTO para que {@code @Valid}
 * realmente casque a cada elemento (un {@code @Valid} sobre una {@code List}
 * como body no propaga la validación a los ítems).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoCategoriaRequestDTO {

    @Valid
    @NotNull(message = "La lista 'categorias' es obligatoria")
    private List<PresupuestoCategoriaItemDTO> categorias = new ArrayList<>();
}
