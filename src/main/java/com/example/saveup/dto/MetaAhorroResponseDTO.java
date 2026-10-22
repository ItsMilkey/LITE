package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Meta de ahorro del usuario.")
public class MetaAhorroResponseDTO {
    @Schema(description = "Identificador de la meta.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Nombre de la meta.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Monto objetivo, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoObjetivo;

    @Schema(description = "Fecha límite en formato yyyy-MM-dd.")
    private LocalDate fechaLimite;

    // Campo calculado en el servicio (ahora persistido)
    @Schema(description = "Monto ahorrado actualmente, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoActual;
}
