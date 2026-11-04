package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Sugerencia de regla basada en el comportamiento real (T10d, sección 5).
 * Todo es informativo y de solo lectura: para aceptar, el cliente usa
 * POST /api/v1/presupuestos/me.
 */
@Data
@Schema(description = "Sugerencia de regla 50/30/20 calculada desde los meses cerrados.")
public class SugerenciaReglaResponseDTO {

    @Schema(description = "Meses cerrados analizados, formato yyyy-MM, en orden ascendente.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> mesesAnalizados;

    @Schema(description = "Mediana de los porcentajes del comportamiento real (2 decimales).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private PorcentajesReglaDTO actual;

    @Schema(description = "Sugerencia redondeada a múltiplos de 5; null con DATOS_INSUFICIENTES.")
    private PorcentajesReglaDTO sugerencia;

    @Schema(description = "true si el gasto real de necesidades y deseos supera el ingreso; la sugerencia se escaló para sumar 100.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean gastoSuperaIngreso;

    @Schema(description = "true si la sugerencia coincide con la configuración del usuario; false si no la tiene.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean igualALaGuia;

    @Schema(description = "Motivo por el que no hay sugerencia; DATOS_INSUFICIENTES con menos de 2 meses válidos.")
    private String motivo;
}
