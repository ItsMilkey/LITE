package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Aviso informativo devuelto junto al plan (T10d). Nunca es un error: por
 * ejemplo, al copiar un mes una partida de deuda sin cuota en el destino no se
 * copia y se informa con el motivo {@code SIN_COUOTA_EN_EL_MES}.
 */
@Data
@Schema(description = "Aviso informativo del plan.")
public class AvisoPlanDTO {

    @Schema(description = "Motivo estable del aviso.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String motivo;

    @Schema(description = "ID de la partida de origen, si aplica.")
    private Long partidaId;

    @Schema(description = "Nombre de la partida de origen, si aplica.")
    private String nombre;

    @Schema(description = "ID de la deuda vinculada, si aplica.")
    private Long deudaId;

    @Schema(description = "Texto de respaldo para mostrar al usuario.")
    private String mensaje;
}
