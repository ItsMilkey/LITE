package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "Página de resultados.")
public class PageResponseDTO<T> {
    @Schema(description = "Elementos de la página.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> content;

    @Schema(description = "Número de página, empezando en 0.", requiredMode = Schema.RequiredMode.REQUIRED)
    private int currentPage;

    @Schema(description = "Cantidad total de elementos.", requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalItems;

    @Schema(description = "Cantidad total de páginas.", requiredMode = Schema.RequiredMode.REQUIRED)
    private int totalPages;
}