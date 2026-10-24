package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de una corrida del job de recordatorios (T9.6). Permite verificar
 * desde el cron que el trabajo hizo algo, sin exponer datos de usuarios.
 */
@Schema(description = "Resumen de la corrida de recordatorios.")
public record ResultadoRecordatoriosDTO(

        @Schema(description = "Cuotas pendientes evaluadas.", requiredMode = Schema.RequiredMode.REQUIRED)
        int evaluados,

        @Schema(description = "Avisos enviados y registrados.", requiredMode = Schema.RequiredMode.REQUIRED)
        int enviados,

        @Schema(description = "Avisos omitidos por ya enviados, sin dispositivo o con fallo de entrega.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int omitidos) {
}
