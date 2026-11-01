package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Sugerencias de categoría para un texto libre.")
public class SugerenciaCategoriaResponseDTO {

    @Schema(description = "Texto original recibido, sin recortar; la app lo usa para descartar respuestas atrasadas.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String texto;

    @Schema(description = "Hasta 3 sugerencias ordenadas por confianza descendente y, en empate, por código.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SugerenciaCategoriaDTO> sugerencias;

    @Schema(description = "Verdadero si hay al menos 2 sugerencias y la diferencia entre la primera y la segunda "
            + "es menor que 0.10; la app debe pedir confirmación visible.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean ambigua;
}
