package com.example.saveup.config;

import com.example.saveup.dto.ProblemaHttpDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrato OpenAPI de la API v1. El esquema de errores es el mismo problem+json
 * de T8b y se agrega a todas las operaciones desde un único customizer.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_BEARER = "bearerAuth";
    public static final String ESQUEMA_PROBLEMA = "ProblemaHttp";

    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    public OpenAPI saveUpOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SaveUp Lite API")
                        .version("1.0.0")
                        .description("""
                                API REST de finanzas personales SaveUp Lite.
                                Todas las rutas públicas están versionadas bajo /api/v1.
                                Los errores usan application/problem+json con un código estable en "codigo".
                                """))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .name(ESQUEMA_BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT obtenido en POST /api/v1/usuarios/login.")));
    }

    @Bean
    public OpenApiCustomizer respuestasDeErrorEstandar() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }

            Schema<?> problema = ModelConverters.getInstance()
                    .readAllAsResolvedSchema(ProblemaHttpDTO.class).schema;
            problema.setName(ESQUEMA_PROBLEMA);
            openApi.getComponents().addSchemas(ESQUEMA_PROBLEMA, problema);

            String referencia = "#/components/schemas/" + ESQUEMA_PROBLEMA;
            Map<String, String> errores = erroresEstandar();

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
                        }
                        // Rellena solo los códigos que la operación no declare ya: una
                        // respuesta específica (p. ej. un 409 propio con su descripción)
                        // siempre gana sobre la plantilla estándar.
                        errores.forEach((codigo, descripcion) ->
                                operation.getResponses().putIfAbsent(codigo,
                                        respuestaProblemJson(descripcion, referencia)));

                        // Login documenta además su 429 propio (T9.5, freno de fuerza bruta).
                        if ("loginUsuario".equals(operation.getOperationId())) {
                            operation.getResponses().putIfAbsent("429", respuestaProblemJson(
                                    "Demasiados intentos fallidos de login; espera unos minutos.",
                                    referencia));
                        }
                    }));
        };
    }

    private Map<String, String> erroresEstandar() {
        Map<String, String> errores = new LinkedHashMap<>();
        errores.put("400", "Solicitud inválida. Revisa el cuerpo y los parámetros.");
        errores.put("401", "No autenticado o token inválido/expirado.");
        errores.put("403", "No tienes permisos para acceder al recurso.");
        errores.put("404", "Recurso no encontrado.");
        errores.put("409", "Conflicto con una regla de negocio.");
        errores.put("500", "Error interno. Usa idError para reportarlo.");
        return errores;
    }

    private ApiResponse respuestaProblemJson(String descripcion, String referencia) {
        return new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref(referencia))));
    }
}
