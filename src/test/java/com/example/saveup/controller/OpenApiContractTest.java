package com.example.saveup.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Path CONTRATO = Path.of("docs", "openapi.json");
    private static final String INSTRUCCION_REGENERAR =
            "El contrato docs/openapi.json no coincide con GET /v3/api-docs. "
                    + "Regénéralo con: .\\mvnw.cmd \"-Dopenapi.update=true\" test -Dtest=OpenApiContractTest";

    private static final List<String> METODOS = List.of("get", "post", "put", "patch", "delete", "head", "options");
    private static final List<String> ERRORES = List.of("400", "401", "403", "404", "409", "500");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("El contrato OpenAPI versionado coincide con la aplicación y cumple las reglas de T8c")
    void contratoOpenApiCoincide() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode generado = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        validarContrato(generado);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(CONTRATO.getParent());
            Files.writeString(CONTRATO, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generado)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
            return;
        }

        assertTrue(Files.exists(CONTRATO), INSTRUCCION_REGENERAR);
        JsonNode guardado = objectMapper.readTree(Files.readString(CONTRATO, StandardCharsets.UTF_8));
        assertEquals(guardado, generado, INSTRUCCION_REGENERAR);
    }

    private void validarContrato(JsonNode contrato) {
        JsonNode paths = contrato.path("paths");
        assertTrue(paths.isObject() && !paths.isEmpty(), "El contrato debe declarar rutas.");
        assertEquals("SaveUp Lite API", contrato.path("info").path("title").asText());
        assertEquals("1.0.0", contrato.path("info").path("version").asText());
        assertTrue(contrato.path("components").path("securitySchemes").has("bearerAuth"),
                "Debe existir el esquema bearerAuth.");

        Set<String> operationIds = new HashSet<>();
        Iterator<Map.Entry<String, JsonNode>> rutas = paths.fields();

        while (rutas.hasNext()) {
            Map.Entry<String, JsonNode> ruta = rutas.next();
            assertTrue(ruta.getKey().startsWith("/api/v1/"),
                    "Toda ruta pública debe empezar con /api/v1: " + ruta.getKey());

            for (String metodo : METODOS) {
                JsonNode operacion = ruta.getValue().path(metodo);
                if (operacion.isMissingNode()) {
                    continue;
                }

                String operationId = operacion.path("operationId").asText();
                assertFalse(operationId.isBlank(), "Falta operationId en " + metodo + " " + ruta.getKey());
                assertTrue(operationIds.add(operationId),
                        "operationId duplicado: " + operationId + " en " + metodo + " " + ruta.getKey());

                validarSeguridad(operacion, operationId, metodo, ruta.getKey());
                validarRespuesta2xx(operacion, metodo, ruta.getKey());
                validarErrores(operacion, metodo, ruta.getKey());
            }
        }

        assertFalse(operationIds.isEmpty(), "El contrato no contiene operaciones.");
    }

    private void validarSeguridad(JsonNode operacion, String operationId, String metodo, String ruta) {
        boolean publica = "registerUsuario".equals(operationId) || "loginUsuario".equals(operationId);
        JsonNode security = operacion.path("security");

        if (publica) {
            assertTrue(security.isMissingNode() || security.isEmpty(),
                    "La operación pública " + operationId + " no debe exigir bearerAuth.");
            return;
        }

        boolean bearer = false;
        if (security.isArray()) {
            for (JsonNode requisito : security) {
                bearer = bearer || requisito.has("bearerAuth");
            }
        }
        assertTrue(bearer, "La operación " + operationId + " (" + metodo + " " + ruta
                + ") debe exigir bearerAuth.");
    }

    private void validarRespuesta2xx(JsonNode operacion, String metodo, String ruta) {
        JsonNode respuestas = operacion.path("responses");
        boolean tiene2xx = false;

        Iterator<Map.Entry<String, JsonNode>> campos = respuestas.fields();
        while (campos.hasNext()) {
            Map.Entry<String, JsonNode> respuesta = campos.next();
            if (!respuesta.getKey().startsWith("2")) {
                continue;
            }
            tiene2xx = true;
            if ("204".equals(respuesta.getKey())) {
                continue;
            }

            JsonNode content = respuesta.getValue().path("content");
            assertFalse(content.isEmpty(), "La respuesta " + respuesta.getKey() + " de "
                    + metodo + " " + ruta + " debe declarar content.");
            content.fields().forEachRemaining(media -> {
                JsonNode schema = media.getValue().path("schema");
                assertFalse(schema.isMissingNode() || schema.isNull(),
                        "La respuesta 2xx de " + metodo + " " + ruta + " debe tener un esquema concreto.");
            });
        }

        assertTrue(tiene2xx, "Falta una respuesta 2xx en " + metodo + " " + ruta);
    }

    private void validarErrores(JsonNode operacion, String metodo, String ruta) {
        JsonNode respuestas = operacion.path("responses");
        for (String codigo : ERRORES) {
            assertTrue(respuestas.has(codigo),
                    "Falta la respuesta " + codigo + " en " + metodo + " " + ruta);
            JsonNode problemJson = respuestas.path(codigo).path("content").path("application/problem+json");
            assertFalse(problemJson.isMissingNode(),
                    "La respuesta " + codigo + " de " + metodo + " " + ruta + " debe ser application/problem+json");
        }
    }
}
