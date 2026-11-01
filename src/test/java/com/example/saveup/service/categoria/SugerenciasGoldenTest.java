package com.example.saveup.service.categoria;

import com.example.saveup.dto.SugerenciaCategoriaDTO;
import com.example.saveup.dto.SugerenciaCategoriaResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.ContextoSugerencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T10b — Tabla dorada G01 a G50. Cada fila del CSV es un caso; agregar un caso
 * es agregar una fila.
 */
class SugerenciasGoldenTest {

    private static final String CSV = "/categorias/sugerencias-golden.csv";
    private static final DiccionarioCategorias DICCIONARIO =
            new DiccionarioCategorias(new ObjectMapper());
    private static final List<Categoria> CATALOGO = CatalogoDePrueba.categorias();
    private static final Set<String> CODIGOS = CATALOGO.stream()
            .map(Categoria::getCodigo)
            .collect(Collectors.toSet());
    private static final List<DiccionarioCategorias.Entrada> ENTRADAS =
            DICCIONARIO.entradasValidas(CODIGOS);

    private final SugeridorCategoria sugeridor = new SugeridorCategoria();

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("casos")
    @DisplayName("G01-G50: cada fila del CSV produce los códigos y la ambigüedad esperados")
    void golden(String id, String texto, ContextoSugerencia contexto,
                List<String> esperados, boolean ambigua) {
        SugerenciaCategoriaResponseDTO respuesta =
                sugeridor.sugerir(texto, contexto, CATALOGO, ENTRADAS, null, null);

        assertEquals(texto, respuesta.getTexto(), id + ": debe devolver el texto original");
        assertEquals(esperados, codigos(respuesta), id + ": códigos esperados en orden");
        assertEquals(ambigua, respuesta.isAmbigua(), id + ": bandera ambigua");
    }

    static Stream<Arguments> casos() throws IOException {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(SugerenciasGoldenTest.class.getResourceAsStream(CSV),
                        "No se encontró " + CSV),
                StandardCharsets.UTF_8))) {
            List<Arguments> casos = new ArrayList<>();
            String linea;
            int numero = 0;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                if (numero == 0) {
                    numero++;
                    continue; // cabecera
                }
                String[] partes = linea.split(",", -1);
                assertEquals(4, partes.length, "Fila CSV inválida: " + linea);

                String id = String.format("G%02d", numero);
                numero++;

                String texto = partes[0]; // sin trim: G03 conserva los espacios
                ContextoSugerencia contexto = ContextoSugerencia.valueOf(partes[1].trim());
                List<String> codigos = partes[2].isEmpty()
                        ? List.of()
                        : List.of(partes[2].split("\\|"));
                boolean ambigua = Boolean.parseBoolean(partes[3].trim());

                casos.add(Arguments.of(id, texto, contexto, codigos, ambigua));
            }
            assertEquals(50, casos.size(), "La tabla dorada debe tener 50 casos G01-G50");
            return casos.stream();
        }
    }

    private static List<String> codigos(SugerenciaCategoriaResponseDTO respuesta) {
        return respuesta.getSugerencias().stream()
                .map(SugerenciaCategoriaDTO::getCodigo)
                .toList();
    }
}
