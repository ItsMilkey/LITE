package com.example.saveup.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Error de validación asociado a un campo. {@code campo} usa el nombre JSON de
 * la propiedad (por ejemplo {@code condiciones.montoCapital}), nunca el nombre
 * del atributo Java.
 */
public record ErrorCampo(
        @Schema(description = "Nombre JSON del campo inválido.", requiredMode = Schema.RequiredMode.REQUIRED)
        String campo,
        @Schema(description = "Mensaje de validación en español.", requiredMode = Schema.RequiredMode.REQUIRED)
        String mensaje) {
}
