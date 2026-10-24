package com.example.saveup.dto;

import com.example.saveup.model.enums.Plataforma;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * Dispositivo registrado (T9.6). El token se devuelve enmascarado: es una
 * credencial de envío y no debe quedar expuesta en capturas ni en logs.
 */
@Schema(description = "Dispositivo registrado para notificaciones push.")
public record DispositivoPushResponseDTO(

        @Schema(description = "Identificador interno del registro.", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "Plataforma del dispositivo.", requiredMode = Schema.RequiredMode.REQUIRED)
        Plataforma plataforma,

        @Schema(description = "false si FCM rechazó el token y ya no se intenta.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean activo,

        @Schema(description = "Instante de registro en ISO-8601 UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
        Date fechaRegistro,

        @Schema(description = "Token con solo los primeros y últimos caracteres visibles.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenEnmascarado) {
}
