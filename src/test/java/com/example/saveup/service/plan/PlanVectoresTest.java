package com.example.saveup.service.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10c — C62: los vectores de {@code docs/plan-vectores.json} coinciden con lo
 * que produce la calculadora pura. {@code -Dplan.update=true} regenera el
 * archivo comprometido.
 */
class PlanVectoresTest {

    private static final Path ARCHIVO = Path.of("docs", "plan-vectores.json");
    private static final String INSTRUCCION_REGENERAR =
            "Regenera el archivo con: mvnw test -Dtest=PlanVectoresTest -Dplan.update=true";

    @Test
    @DisplayName("C62: docs/plan-vectores.json coincide con la calculadora pura")
    void vectoresCoinciden() throws Exception {
        JsonNode generado = PlanVectores.generar();
        ObjectMapper mapper = PlanVectores.mapper();

        if (Boolean.getBoolean("plan.update")) {
            Files.createDirectories(ARCHIVO.getParent());
            Files.writeString(ARCHIVO, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(generado) + System.lineSeparator(), StandardCharsets.UTF_8);
            return;
        }

        assertTrue(Files.exists(ARCHIVO), INSTRUCCION_REGENERAR);
        JsonNode guardado = PlanVectores.leer(Files.readString(ARCHIVO, StandardCharsets.UTF_8));

        // Se comparan como texto canónico: al leer, Jackson convierte 1 en
        // IntNode y el generador usa LongNode; ambos representan el mismo JSON.
        assertEquals(guardado.toString(), generado.toString(), INSTRUCCION_REGENERAR);
        assertTrue(generado.path("casos").size() >= 18,
                "plan-vectores.json debe cubrir al menos los casos V01-V18");
    }
}
