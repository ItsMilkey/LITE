package com.example.saveup.service.categoria;

import com.example.saveup.dto.SugerenciaCategoriaDTO;
import com.example.saveup.dto.SugerenciaCategoriaResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.ContextoSugerencia;
import com.example.saveup.model.enums.MotivoSugerencia;
import com.example.saveup.service.plan.NombrePartida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica pura del sugeridor de categoría (T10b): no conoce Spring ni la base
 * de datos. Recibe el catálogo, el diccionario y la fuente de historial.
 *
 * <p>Puntajes (escala 2, HALF_UP): frase 1.00, palabra exacta 0.90, prefijo
 * 0.75 y aproximada 0.65; todos multiplicados por el peso de la entrada. La
 * coincidencia aproximada solo se evalúa cuando el texto normalizado tiene un
 * único token, para no inventar coincidencias en frases largas.
 */
public class SugeridorCategoria {

    private static final BigDecimal BASE_FRASE = new BigDecimal("1.00");
    private static final BigDecimal BASE_PALABRA = new BigDecimal("0.90");
    private static final BigDecimal BASE_PREFIJO = new BigDecimal("0.75");
    private static final BigDecimal BASE_APROXIMADA = new BigDecimal("0.65");
    private static final BigDecimal CONFIANZA_HISTORIAL = new BigDecimal("0.95");
    private static final BigDecimal CONFIANZA_MINIMA = new BigDecimal("0.60");
    private static final BigDecimal DIFERENCIA_AMBIGUA = new BigDecimal("0.10");
    private static final int MIN_CARACTERES_NORMALIZADOS = 3;
    private static final int MAX_SUGERENCIAS = 3;

    public SugerenciaCategoriaResponseDTO sugerir(String textoOriginal,
                                                  ContextoSugerencia contexto,
                                                  List<Categoria> catalogo,
                                                  List<DiccionarioCategorias.Entrada> entradas,
                                                  String rut,
                                                  FuenteHistorialCategoria historial) {
        String texto = textoOriginal == null ? "" : textoOriginal;
        SugerenciaCategoriaResponseDTO respuesta = new SugerenciaCategoriaResponseDTO();
        respuesta.setTexto(texto);
        respuesta.setSugerencias(List.of());
        respuesta.setAmbigua(false);

        String normalizado = NormalizadorTexto.normalizar(texto);
        List<String> tokens = NormalizadorTexto.tokens(texto);
        if (normalizado.length() < MIN_CARACTERES_NORMALIZADOS || tokens.isEmpty()) {
            return respuesta;
        }

        Map<String, Categoria> permitidas = new LinkedHashMap<>();
        for (Categoria categoria : catalogo) {
            if (categoria.getCodigo() != null && contexto.permite(categoria.getCodigo())) {
                permitidas.putIfAbsent(categoria.getCodigo(), categoria);
            }
        }

        Map<String, Puntaje> mejores = new HashMap<>();
        for (DiccionarioCategorias.Entrada entrada : entradas) {
            if (!permitidas.containsKey(entrada.codigo())) {
                continue;
            }
            Puntaje puntaje = puntuar(entrada, tokens);
            if (puntaje != null) {
                mejores.merge(entrada.codigo(), puntaje, SugeridorCategoria::elMayor);
            }
        }

        if (historial != null && rut != null) {
            // T10c: el historial usa la normalización de partidas (sin quitar
            // palabras vacías ni puntuación), no la del diccionario.
            historial.codigoPara(rut, NombrePartida.normalizar(texto))
                    .filter(permitidas::containsKey)
                    .ifPresent(codigo -> mejores.merge(codigo,
                            new Puntaje(CONFIANZA_HISTORIAL, MotivoSugerencia.HISTORIAL),
                            SugeridorCategoria::elMayor));
        }

        List<SugerenciaCategoriaDTO> sugerencias = mejores.entrySet().stream()
                .filter(entrada -> entrada.getValue().confianza().compareTo(CONFIANZA_MINIMA) >= 0)
                .sorted((a, b) -> {
                    int porConfianza = b.getValue().confianza().compareTo(a.getValue().confianza());
                    return porConfianza != 0 ? porConfianza : a.getKey().compareTo(b.getKey());
                })
                .limit(MAX_SUGERENCIAS)
                .map(entrada -> aDTO(permitidas.get(entrada.getKey()), entrada.getValue()))
                .toList();

        respuesta.setSugerencias(sugerencias);
        respuesta.setAmbigua(esAmbigua(sugerencias));
        return respuesta;
    }

    private Puntaje puntuar(DiccionarioCategorias.Entrada entrada, List<String> tokens) {
        List<String> palabras = entrada.palabras();
        if (palabras.size() > 1) {
            return contieneSecuencia(tokens, palabras)
                    ? new Puntaje(escala(BASE_FRASE.multiply(entrada.peso())), MotivoSugerencia.PALABRA_CLAVE)
                    : null;
        }

        String palabra = palabras.get(0);
        boolean textoDeUnToken = tokens.size() == 1;
        Puntaje mejor = null;
        for (String token : tokens) {
            BigDecimal base = null;
            MotivoSugerencia motivo = null;
            if (palabra.equals(token)) {
                base = BASE_PALABRA;
                motivo = MotivoSugerencia.PALABRA_CLAVE;
            } else if (token.length() >= 4 && palabra.startsWith(token)) {
                base = BASE_PREFIJO;
                motivo = MotivoSugerencia.PREFIJO;
            } else if (textoDeUnToken && esAproximada(palabra, token)) {
                base = BASE_APROXIMADA;
                motivo = MotivoSugerencia.APROXIMADA;
            }
            if (base != null) {
                Puntaje candidato = new Puntaje(escala(base.multiply(entrada.peso())), motivo);
                if (mejor == null || candidato.confianza().compareTo(mejor.confianza()) > 0) {
                    mejor = candidato;
                }
            }
        }
        return mejor;
    }

    private static boolean contieneSecuencia(List<String> tokens, List<String> palabras) {
        for (int i = 0; i + palabras.size() <= tokens.size(); i++) {
            if (tokens.subList(i, i + palabras.size()).equals(palabras)) {
                return true;
            }
        }
        return false;
    }

    private static boolean esAproximada(String palabra, String token) {
        int menorLongitud = Math.min(palabra.length(), token.length());
        if (menorLongitud < 5) {
            return false;
        }
        int tope = menorLongitud >= 9 ? 2 : 1;
        return DistanciaEdicion.distancia(palabra, token, tope) <= tope;
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static Puntaje elMayor(Puntaje actual, Puntaje candidato) {
        return candidato.confianza().compareTo(actual.confianza()) > 0 ? candidato : actual;
    }

    private static boolean esAmbigua(List<SugerenciaCategoriaDTO> sugerencias) {
        if (sugerencias.size() < 2) {
            return false;
        }
        BigDecimal diferencia = sugerencias.get(0).getConfianza()
                .subtract(sugerencias.get(1).getConfianza());
        return diferencia.compareTo(DIFERENCIA_AMBIGUA) < 0;
    }

    private static SugerenciaCategoriaDTO aDTO(Categoria categoria, Puntaje puntaje) {
        SugerenciaCategoriaDTO dto = new SugerenciaCategoriaDTO();
        dto.setCategoriaId(categoria.getId());
        dto.setCodigo(categoria.getCodigo());
        dto.setNombre(categoria.getNombre());
        dto.setConfianza(puntaje.confianza());
        dto.setMotivo(puntaje.motivo());
        return dto;
    }

    private record Puntaje(BigDecimal confianza, MotivoSugerencia motivo) {
    }
}
