package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Edición parcial de una partida (T10c/T10d): nombre, categoría y grupo. Al
 * menos uno debe llegar; el servicio lo valida. El monto no se cambia aquí:
 * usa PATCH /planes/{mes}/montos. El vínculo con una deuda solo se crea al
 * crear la partida (T10d §7) y no se puede modificar después.
 */
@Data
@Schema(description = "Edición parcial de una partida; al menos un campo es obligatorio.")
public class PartidaEdicionDTO {

    @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
    @Schema(description = "Nuevo nombre libre de la partida.")
    private String nombre;

    @Schema(description = "Nueva categoría del catálogo.")
    private Long categoriaId;

    @Schema(description = "ID del grupo al que mover la partida.")
    private Long grupoId;

    @Schema(description = "true quita el grupo de la partida. Prohibido junto con grupoId.")
    private Boolean quitarGrupo;
}
