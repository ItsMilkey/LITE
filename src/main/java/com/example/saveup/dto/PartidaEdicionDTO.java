package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Edición parcial de una partida (T10c/T10d): nombre, categoría, grupo y/o
 * deuda. Al menos uno debe llegar; el servicio lo valida. El monto no se
 * cambia aquí: usa PATCH /planes/{mes}/montos.
 */
@Data
@Schema(description = "Edición parcial de una partida; al menos un campo es obligatorio.")
public class PartidaEdicionDTO {

    @Schema(description = "Nuevo nombre libre de la partida.")
    private String nombre;

    @Schema(description = "Nueva categoría del catálogo.")
    private Long categoriaId;

    @Schema(description = "ID del grupo al que mover la partida.")
    private Long grupoId;

    @Schema(description = "true quita el grupo de la partida. Prohibido junto con grupoId.")
    private Boolean quitarGrupo;

    @Schema(description = "ID de la deuda a vincular. Solo si la partida aún no tiene deuda.")
    private Long deudaId;
}
