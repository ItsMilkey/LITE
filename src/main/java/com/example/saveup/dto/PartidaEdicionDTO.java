package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Edición parcial de una partida (T10c): nombre y/o categoría. Al menos uno
 * debe llegar; el servicio lo valida. El monto no se cambia aquí: usa PATCH
 * /planes/{mes}/montos.
 */
@Data
@Schema(description = "Edición parcial de una partida; al menos un campo es obligatorio.")
public class PartidaEdicionDTO {

    @Schema(description = "Nuevo nombre libre de la partida.")
    private String nombre;

    @Schema(description = "Nueva categoría del catálogo.")
    private Long categoriaId;
}
