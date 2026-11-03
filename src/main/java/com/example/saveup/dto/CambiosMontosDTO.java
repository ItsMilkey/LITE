package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Lote atómico de cambios de montos planificados (T10c). */
@Data
@Schema(description = "Lote de 1 a 100 cambios de monto; todo o nada.")
public class CambiosMontosDTO {

    @NotNull(message = "La lista de cambios es obligatoria")
    @Size(min = 1, max = 100, message = "Debes enviar entre 1 y 100 cambios")
    @Valid
    @Schema(description = "Cambios de monto, sin partidaId repetido.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CambioMontoDTO> cambios;
}
