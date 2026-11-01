package com.example.saveup.service.categoria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diccionario de palabras clave por categoría (T10b). Se carga una sola vez
 * desde {@code classpath:categorias/diccionario.json}.
 *
 * <p>Cada entrada se normaliza con {@link NormalizadorTexto} (incluida la
 * eliminación de palabras vacías). Los códigos que no existen en el catálogo
 * se ignoran con un WARN, sin fallar.
 */
@Component
public class DiccionarioCategorias {

    /** Entrada del diccionario: código de categoría, palabras normalizadas y peso. */
    public record Entrada(String codigo, List<String> palabras, BigDecimal peso) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DiccionarioCategorias.class);
    private static final String RECURSO = "categorias/diccionario.json";

    private final List<Entrada> entradas;
    private final Set<String> codigosAvisados = ConcurrentHashMap.newKeySet();

    @Autowired
    public DiccionarioCategorias(ObjectMapper objectMapper) {
        this(cargarDesdeClasspath(objectMapper));
    }

    DiccionarioCategorias(List<Entrada> entradas) {
        this.entradas = List.copyOf(entradas);
    }

    /** Carga el diccionario desde un JSON con la forma {@code { "CODIGO": [ {"t": "...", "peso": 1.00} ] }}. */
    public static DiccionarioCategorias desdeJson(InputStream json, ObjectMapper objectMapper) {
        try {
            return new DiccionarioCategorias(leer(json, objectMapper));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el diccionario de categorías.", e);
        }
    }

    /** Todas las entradas cargadas, sin filtrar por catálogo. */
    public List<Entrada> entradas() {
        return entradas;
    }

    /**
     * Entradas cuyo código existe en el catálogo recibido. Los códigos
     * desconocidos se descartan y se avisa una sola vez por código.
     */
    public List<Entrada> entradasValidas(Set<String> codigosCatalogo) {
        List<Entrada> validas = new ArrayList<>();
        for (Entrada entrada : entradas) {
            if (codigosCatalogo.contains(entrada.codigo())) {
                validas.add(entrada);
            } else if (codigosAvisados.add(entrada.codigo())) {
                LOG.warn("Diccionario: el código {} no existe en el catálogo; se ignora.", entrada.codigo());
            }
        }
        return List.copyOf(validas);
    }

    private static List<Entrada> cargarDesdeClasspath(ObjectMapper objectMapper) {
        ClassPathResource recurso = new ClassPathResource(RECURSO);
        try (InputStream entrada = recurso.getInputStream()) {
            return leer(entrada, objectMapper);
        } catch (IOException e) {
            throw new IllegalStateException("No se encontró el recurso " + RECURSO + ".", e);
        }
    }

    private static List<Entrada> leer(InputStream json, ObjectMapper objectMapper) throws IOException {
        JsonNode raiz = objectMapper.readTree(json);
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalStateException("El diccionario de categorías debe ser un objeto JSON.");
        }

        List<Entrada> resultado = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> categorias = raiz.fields();
        while (categorias.hasNext()) {
            Map.Entry<String, JsonNode> categoria = categorias.next();
            String codigo = categoria.getKey();
            if (!categoria.getValue().isArray()) {
                throw new IllegalStateException("El diccionario de " + codigo + " debe ser una lista.");
            }
            for (JsonNode nodo : categoria.getValue()) {
                String texto = nodo.path("t").asText(null);
                if (texto == null || texto.isBlank()) {
                    throw new IllegalStateException("Entrada sin 't' en el diccionario de " + codigo + ".");
                }
                List<String> palabras = NormalizadorTexto.tokens(texto);
                if (palabras.isEmpty()) {
                    continue;
                }
                BigDecimal peso = nodo.has("peso")
                        ? nodo.get("peso").decimalValue()
                        : BigDecimal.ONE;
                resultado.add(new Entrada(codigo, palabras, peso.setScale(2, RoundingMode.HALF_UP)));
            }
        }
        return resultado;
    }
}
