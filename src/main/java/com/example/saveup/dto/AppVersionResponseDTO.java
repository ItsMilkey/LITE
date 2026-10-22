package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de GET /api/v1/app/version (T9.4): le dice a la app si debe
 * bloquear el uso, sugerir actualizar o seguir, junto con la URL de la tienda
 * y la versión de los documentos legales vigentes.
 */
@Schema(description = "Política de actualización y legal vigente para la plataforma consultada.")
public record AppVersionResponseDTO(
        @Schema(description = "true si la versión está por debajo del mínimo soportado: la app debe actualizar antes de seguir.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean actualizacionObligatoria,
        @Schema(description = "true si hay una versión más reciente disponible y se sugiere actualizar sin bloquear.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean actualizacionSugerida,
        @Schema(description = "Mensaje listo para mostrar al usuario, en español.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String mensaje,
        @Schema(description = "URL de la tienda (Play Store o App Store) para actualizar la app.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String urlTienda,
        @Schema(description = "Versión de los términos y condiciones vigentes que la app refleja.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String versionTerminos,
        @Schema(description = "Versión de la política de privacidad vigente que la app refleja.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String versionPrivacidad) {
}
