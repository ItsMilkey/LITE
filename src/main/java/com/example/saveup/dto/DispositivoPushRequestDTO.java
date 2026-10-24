package com.example.saveup.dto;

import com.example.saveup.model.enums.Plataforma;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta (o refresco) de un dispositivo para push (T9.6). La app envía el token
 * que le entrega FCM y su plataforma; el usuario sale del JWT, nunca del cuerpo.
 */
@Schema(description = "Dispositivo que recibirá notificaciones push.")
public record DispositivoPushRequestDTO(

        @Schema(description = "Token de Firebase Cloud Messaging del dispositivo.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank(message = "El token es obligatorio")
        @Size(max = 255, message = "El token no puede superar los 255 caracteres")
        String token,

        @Schema(description = "Plataforma del dispositivo.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "La plataforma es obligatoria")
        Plataforma plataforma) {
}
