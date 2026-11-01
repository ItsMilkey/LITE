package com.example.saveup.service.categoria;

import com.example.saveup.dto.SugerenciaCategoriaDTO;
import com.example.saveup.dto.SugerenciaCategoriaResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.ContextoSugerencia;
import com.example.saveup.model.enums.MotivoSugerencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T10b — Reglas de puntaje, selección, ambigüedad e historial (lógica pura). */
class SugeridorCategoriaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DiccionarioCategorias DICCIONARIO = new DiccionarioCategorias(MAPPER);
    private static final List<Categoria> CATALOGO = CatalogoDePrueba.categorias();
    private static final Set<String> CODIGOS = CATALOGO.stream()
            .map(Categoria::getCodigo)
            .collect(Collectors.toSet());
    private static final List<DiccionarioCategorias.Entrada> ENTRADAS = DICCIONARIO.entradasValidas(CODIGOS);

    private final SugeridorCategoria sugeridor = new SugeridorCategoria();

    @Test
    @DisplayName("G08: prefijo de al menos 4 caracteres → 0,75 con motivo PREFIJO")
    void g08_prefijo() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("superm", ContextoSugerencia.GASTO);

        assertEquals(List.of("ALIMENTACION_BASE"), codigos(respuesta));
        SugerenciaCategoriaDTO primera = respuesta.getSugerencias().get(0);
        assertEquals(new BigDecimal("0.75"), primera.getConfianza());
        assertEquals(MotivoSugerencia.PREFIJO, primera.getMotivo());
    }

    @Test
    @DisplayName("G09: error de una letra → 0,65 con motivo APROXIMADA")
    void g09_aproximada() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("supermecado", ContextoSugerencia.GASTO);

        assertEquals(List.of("ALIMENTACION_BASE"), codigos(respuesta));
        SugerenciaCategoriaDTO primera = respuesta.getSugerencias().get(0);
        assertEquals(new BigDecimal("0.65"), primera.getConfianza());
        assertEquals(MotivoSugerencia.APROXIMADA, primera.getMotivo());
    }

    @Test
    @DisplayName("G28: netflx → 0,65 con motivo APROXIMADA")
    void g28_aproximada() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("netflx", ContextoSugerencia.GASTO);

        assertEquals(List.of("SUSCRIPCIONES_DIGITAL"), codigos(respuesta));
        assertEquals(new BigDecimal("0.65"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(MotivoSugerencia.APROXIMADA, respuesta.getSugerencias().get(0).getMotivo());
    }

    @Test
    @DisplayName("G10: comida es ambigua por los pesos 1,00 y 0,90")
    void g10_comidaAmbigua() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("comida", ContextoSugerencia.GASTO);

        assertEquals(List.of("ALIMENTACION_BASE", "RESTAURANTES_SALIDAS"), codigos(respuesta));
        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(new BigDecimal("0.81"), respuesta.getSugerencias().get(1).getConfianza());
        assertTrue(respuesta.isAmbigua());
    }

    @Test
    @DisplayName("G14: empate a 0,90 se ordena por código y es ambiguo")
    void g14_empateOrdenadoPorCodigo() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("cena aniversario", ContextoSugerencia.GASTO);

        assertEquals(List.of("REGALOS_CELEBRACIONES", "RESTAURANTES_SALIDAS"), codigos(respuesta));
        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(1).getConfianza());
        assertTrue(respuesta.isAmbigua());
    }

    @Test
    @DisplayName("G50: diferencia exactamente 0,10 no es ambigua")
    void g50_diferenciaExacta010() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("uber eats", ContextoSugerencia.GASTO);

        assertEquals(List.of("RESTAURANTES_SALIDAS", "TRANSPORTE_MOVILIDAD"), codigos(respuesta));
        assertEquals(new BigDecimal("1.00"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(1).getConfianza());
        assertFalse(respuesta.isAmbigua());
    }

    @Test
    @DisplayName("U01: texto de 2 caracteres no produce sugerencias")
    void u01_dosCaracteres() {
        assertTrue(sugerir("ab", ContextoSugerencia.GASTO).getSugerencias().isEmpty());
    }

    @Test
    @DisplayName("U02: texto sin coincidencias no produce sugerencias")
    void u02_sinCoincidencias() {
        assertTrue(sugerir("xyzqwerty", ContextoSugerencia.GASTO).getSugerencias().isEmpty());
    }

    @Test
    @DisplayName("U03: una frase no se corrige por aproximación")
    void u03_fraseNoAproximada() {
        assertTrue(sugerir("gasto personal libre", ContextoSugerencia.GASTO).getSugerencias().isEmpty());
    }

    @Test
    @DisplayName("U07: el orden de los tokens no cambia el resultado")
    void u07_ordenDeTokens() {
        assertEquals(codigos(sugerir("pago netflix", ContextoSugerencia.GASTO)),
                codigos(sugerir("netflix pago", ContextoSugerencia.GASTO)));
    }

    @Test
    @DisplayName("U08: con tildes y sin tildes el resultado es el mismo")
    void u08_tildes() {
        assertEquals(sugerir("educación", ContextoSugerencia.GASTO).getSugerencias(),
                sugerir("educacion", ContextoSugerencia.GASTO).getSugerencias());
    }

    @Test
    @DisplayName("U09: se devuelven como máximo 3 sugerencias")
    void u09_maximoTres() {
        List<DiccionarioCategorias.Entrada> entradas = List.of(
                entrada("OCIO_RECREACION", "prueba", "1.00"),
                entrada("DEUDAS", "prueba", "1.00"),
                entrada("MASCOTAS", "prueba", "1.00"),
                entrada("HOGAR_CONSUMIBLES", "prueba", "1.00"),
                entrada("SALUD_BIENESTAR", "prueba", "1.00"));

        SugerenciaCategoriaResponseDTO respuesta = sugeridor.sugerir(
                "prueba", ContextoSugerencia.GASTO, CATALOGO, entradas, null, null);

        assertEquals(3, respuesta.getSugerencias().size());
        assertEquals(List.of("DEUDAS", "HOGAR_CONSUMIBLES", "MASCOTAS"), codigos(respuesta));
    }

    @Test
    @DisplayName("U10: empate de puntajes se ordena por código y es estable")
    void u10_empateEstable() {
        List<DiccionarioCategorias.Entrada> entradas = List.of(
                entrada("OCIO_RECREACION", "prueba", "1.00"),
                entrada("DEUDAS", "prueba", "1.00"));

        SugerenciaCategoriaResponseDTO primera = sugeridor.sugerir(
                "prueba", ContextoSugerencia.GASTO, CATALOGO, entradas, null, null);
        SugerenciaCategoriaResponseDTO segunda = sugeridor.sugerir(
                "prueba", ContextoSugerencia.GASTO, CATALOGO, entradas, null, null);

        assertEquals(List.of("DEUDAS", "OCIO_RECREACION"), codigos(primera));
        assertEquals(codigos(primera), codigos(segunda));
    }

    @Test
    @DisplayName("U11: diferencia exactamente 0,10 → ambigua falsa")
    void u11_diferenciaExacta() {
        List<DiccionarioCategorias.Entrada> entradas = List.of(
                entrada("ALIMENTACION_BASE", "prueba frase", "1.00"),
                entrada("RESTAURANTES_SALIDAS", "prueba frase", "0.90"));

        SugerenciaCategoriaResponseDTO respuesta = sugeridor.sugerir(
                "prueba frase", ContextoSugerencia.GASTO, CATALOGO, entradas, null, null);

        assertEquals(new BigDecimal("1.00"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(1).getConfianza());
        assertFalse(respuesta.isAmbigua());
    }

    @Test
    @DisplayName("U12: diferencia 0,09 → ambigua verdadera")
    void u12_diferenciaMenorQue010() {
        List<DiccionarioCategorias.Entrada> entradas = List.of(
                entrada("ALIMENTACION_BASE", "prueba", "1.00"),
                entrada("RESTAURANTES_SALIDAS", "prueba", "0.90"));

        SugerenciaCategoriaResponseDTO respuesta = sugeridor.sugerir(
                "prueba", ContextoSugerencia.GASTO, CATALOGO, entradas, null, null);

        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(new BigDecimal("0.81"), respuesta.getSugerencias().get(1).getConfianza());
        assertTrue(respuesta.isAmbigua());
    }

    @Test
    @DisplayName("U13: dos entradas de la misma categoría cuentan una sola vez con el máximo")
    void u13_mismaCategoriaCuentaUnaVez() {
        SugerenciaCategoriaResponseDTO respuesta = sugerir("super", ContextoSugerencia.GASTO);

        assertEquals(List.of("ALIMENTACION_BASE"), codigos(respuesta));
        assertEquals(new BigDecimal("0.90"), respuesta.getSugerencias().get(0).getConfianza());
    }

    @Test
    @DisplayName("U15: el historial sube la categoría a 0,95 con motivo HISTORIAL")
    void u15_historial() {
        FuenteHistorialCategoria historial = (rut, texto) -> Optional.of("DEUDAS");

        SugerenciaCategoriaResponseDTO respuesta = sugeridor.sugerir(
                "netflix", ContextoSugerencia.GASTO, CATALOGO, ENTRADAS, "11111111-1", historial);

        assertEquals(List.of("DEUDAS", "SUSCRIPCIONES_DIGITAL"), codigos(respuesta));
        assertEquals(new BigDecimal("0.95"), respuesta.getSugerencias().get(0).getConfianza());
        assertEquals(MotivoSugerencia.HISTORIAL, respuesta.getSugerencias().get(0).getMotivo());
    }

    @Test
    @DisplayName("U15: el historial respeta el contexto (SUELDO no aparece en GASTO)")
    void u15_historialRespetaContexto() {
        FuenteHistorialCategoria historial = (rut, texto) -> Optional.of("SUELDO");

        SugerenciaCategoriaResponseDTO respuesta = sugeridor.sugerir(
                "netflix", ContextoSugerencia.GASTO, CATALOGO, ENTRADAS, "11111111-1", historial);

        assertEquals(List.of("SUSCRIPCIONES_DIGITAL"), codigos(respuesta));
    }

    @Test
    @DisplayName("Contexto INGRESO solo permite SUELDO y OTROS")
    void contextoIngreso() {
        assertEquals(List.of("SUELDO"), codigos(sugerir("sueldo", ContextoSugerencia.INGRESO)));
        assertEquals(List.of("OTROS"), codigos(sugerir("otros ingresos", ContextoSugerencia.INGRESO)));
        assertTrue(sugerir("comida", ContextoSugerencia.INGRESO).getSugerencias().isEmpty());
    }

    @Test
    @DisplayName("Nunca devuelve OTROS por su cuenta")
    void nuncaDevuelveOtrosPorSuCuenta() {
        assertTrue(sugerir("texto sin coincidencia", ContextoSugerencia.GASTO).getSugerencias().isEmpty());
    }

    @Test
    @DisplayName("U19: 1.000 llamadas con el diccionario completo, sin base de datos y en tiempo razonable")
    void u19_rendimiento() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 1_000; i++) {
                SugerenciaCategoriaResponseDTO respuesta =
                        sugerir("supermercado", ContextoSugerencia.GASTO);
                assertEquals(List.of("ALIMENTACION_BASE"), codigos(respuesta));
            }
        });
    }

    private SugerenciaCategoriaResponseDTO sugerir(String texto, ContextoSugerencia contexto) {
        return sugeridor.sugerir(texto, contexto, CATALOGO, ENTRADAS, null, null);
    }

    private static List<String> codigos(SugerenciaCategoriaResponseDTO respuesta) {
        return respuesta.getSugerencias().stream()
                .map(SugerenciaCategoriaDTO::getCodigo)
                .toList();
    }

    private static DiccionarioCategorias.Entrada entrada(String codigo, String texto, String peso) {
        return new DiccionarioCategorias.Entrada(codigo,
                NormalizadorTexto.tokens(texto),
                new BigDecimal(peso).setScale(2, RoundingMode.HALF_UP));
    }
}
