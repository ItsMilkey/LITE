package com.example.saveup.dto;

import com.example.saveup.error.ErrorCampo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Esquema documental del problem+json de T8b. No se usa en tiempo de ejecución:
 * el contrato debe describir el formato estable que consume el cliente Flutter.
 */
@Data
@Schema(name = "ProblemaHttp", description = "Error uniforme de la API (RFC 7807 / application/problem+json).")
public class ProblemaHttpDTO {

    @Schema(
            description = "URI que identifica el tipo de problema. Normalmente about:blank.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(
            description = "Título breve del error, en español.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(
            description = "Código HTTP de la respuesta.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int status;

    @Schema(
            description = "Mensaje legible de respaldo. El cliente debe decidir el texto final por codigo.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String detail;

    @Schema(
            description = "Ruta de la solicitud que originó el error.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String instance;

    @Schema(
            description = "Código estable de negocio (por ejemplo VALIDACION_FALLIDA o DEUDA_SOBREPAGO).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigo;

    @Schema(description = "Errores de validación por campo. Solo aparece cuando corresponde.")
    private List<ErrorCampo> errores;

    @Schema(description = "UUID de correlación para errores 500. Solo aparece en ERROR_INTERNO.")
    private String idError;
}
