package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * Respuesta de POST /api/v1/usuarios/login y POST /api/v1/auth/refresh (T9.1).
 */
@Schema(description = "Par de tokens de la sesión: access token corto + refresh token rotativo.")
public record LoginResponseDTO(
        @Schema(description = "JWT de acceso, corto (15 min). Debe enviarse en Authorization: Bearer <token>; "
                + "al expirar el cliente lo renueva con /auth/refresh.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(description = "Refresh token rotativo (90 días). Guardar de forma segura y usarlo solo "
                + "en POST /auth/refresh; cada uso entrega uno nuevo y revoca el anterior. "
                + "Para cerrar sesión enríelo en /auth/logout.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,
        @Schema(description = "Instante UTC (ISO-8601 con sufijo Z) en que expira el access token. "
                + "Desde unos segundos antes empieza a refrescar de forma transparente.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Date expiraEn) {
}
