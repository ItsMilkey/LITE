package com.example.saveup.service.categoria;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.saveup.model.Categoria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T10b — Carga, normalización y validación del diccionario. */
class DiccionarioCategoriasTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("U16: todos los códigos del diccionario pertenecen a las 18 categorías de T10a")
    void todosLosCodigosPertenecenAlCatalogo() {
        DiccionarioCategorias diccionario = new DiccionarioCategorias(MAPPER);
        Set<String> catalogo = CatalogoDePrueba.categorias().stream()
                .map(Categoria::getCodigo)
                .collect(Collectors.toSet());
        Set<String> delDiccionario = diccionario.entradas().stream()
                .map(DiccionarioCategorias.Entrada::codigo)
                .collect(Collectors.toSet());

        assertEquals(18, catalogo.size());
        assertEquals(18, delDiccionario.size());
        assertEquals(catalogo, delDiccionario);
    }

    @Test
    @DisplayName("Al cargar, cada entrada se normaliza y el peso por defecto es 1,00")
    void normalizaEntradasYPesoPorDefecto() {
        String json = """
                {
                  "ALIMENTACION_BASE": [
                    { "t": "Permiso de Circulación" },
                    { "t": "comida", "peso": 0.90 },
                    { "t": "super" }
                  ]
                }
                """;
        DiccionarioCategorias diccionario = desdeJson(json);

        List<DiccionarioCategorias.Entrada> entradas = diccionario.entradas();
        assertEquals(3, entradas.size());
        assertEquals(List.of("permiso", "circulacion"), entradas.get(0).palabras());
        assertEquals(new BigDecimal("1.00"), entradas.get(0).peso());
        assertEquals(new BigDecimal("0.90"), entradas.get(1).peso());
        assertEquals(List.of("super"), entradas.get(2).palabras());
    }

    @Test
    @DisplayName("U14: un código inexistente se ignora con WARN y la carga no falla")
    void codigoInexistenteSeIgnoraConWarn() {
        String json = """
                {
                  "ALIMENTACION_BASE": [ { "t": "supermercado" } ],
                  "NO_EXISTE": [ { "t": "fantasma" } ]
                }
                """;
        DiccionarioCategorias diccionario = desdeJson(json);

        Logger logger = (Logger) LoggerFactory.getLogger(DiccionarioCategorias.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            List<DiccionarioCategorias.Entrada> validas =
                    diccionario.entradasValidas(Set.of("ALIMENTACION_BASE"));
            diccionario.entradasValidas(Set.of("ALIMENTACION_BASE"));

            assertEquals(1, validas.size());
            assertEquals("ALIMENTACION_BASE", validas.get(0).codigo());

            long avisos = appender.list.stream()
                    .filter(evento -> evento.getLevel() == Level.WARN)
                    .filter(evento -> evento.getFormattedMessage().contains("NO_EXISTE"))
                    .count();
            assertEquals(1, avisos, "El WARN debe emitirse una sola vez por código desconocido");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("entradasValidas conserva todas las entradas cuando el catálogo las contiene")
    void entradasValidasConCatalogoCompleto() {
        DiccionarioCategorias diccionario = new DiccionarioCategorias(MAPPER);
        Set<String> catalogo = CatalogoDePrueba.categorias().stream()
                .map(Categoria::getCodigo)
                .collect(Collectors.toSet());

        List<DiccionarioCategorias.Entrada> validas = diccionario.entradasValidas(catalogo);

        assertEquals(diccionario.entradas().size(), validas.size());
        assertTrue(validas.stream().allMatch(entrada -> catalogo.contains(entrada.codigo())));
    }

    private static DiccionarioCategorias desdeJson(String json) {
        return DiccionarioCategorias.desdeJson(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), MAPPER);
    }
}
