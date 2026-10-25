package com.example.saveup.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regla T8b reforzada: el {@code detail} de DATOS_INVALIDOS solo lleva
 * mensajes de autoría del repo ({@code DatosInvalidosException} y
 * {@code CalculoFinancieroException}); los IAE de librería reciben el detail
 * genérico y su texto queda en el log, nunca en la respuesta.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/v1/simulaciones/credito");
    }

    @Test
    @DisplayName("DatosInvalidosException conserva su mensaje en el detail")
    void datosInvalidosPropios_mantieneMensaje() {
        ResponseEntity<ProblemDetail> respuesta = handler.datosInvalidos(
                new DatosInvalidosException("El límite mensual debe ser mayor a 0."), request);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("DATOS_INVALIDOS", respuesta.getBody().getProperties().get("codigo"));
        assertEquals("El límite mensual debe ser mayor a 0.", respuesta.getBody().getDetail());
    }

    @Test
    @DisplayName("IllegalArgumentException de librería → detail genérico, sin mensaje interno")
    void illegalArgumentDeLibreria_detailGenerico() {
        ResponseEntity<ProblemDetail> respuesta = handler.datosInvalidos(
                new IllegalArgumentException("For input string: \"abc\""), request);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("DATOS_INVALIDOS", respuesta.getBody().getProperties().get("codigo"));
        assertEquals(CodigoError.DATOS_INVALIDOS.getDetail(), respuesta.getBody().getDetail());
        assertTrue(respuesta.getBody().getProperties() == null
                || !respuesta.getBody().getProperties().containsKey("idError"));
    }

    @Test
    @DisplayName("NumberFormatException (subclase de IAE) → detail genérico")
    void numberFormatException_detailGenerico() {
        ResponseEntity<ProblemDetail> respuesta = handler.datosInvalidos(
                new NumberFormatException("For input string: \"12x\""), request);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals(CodigoError.DATOS_INVALIDOS.getDetail(), respuesta.getBody().getDetail());
    }

    @Test
    @DisplayName("DateTimeParseException → 400 DATOS_INVALIDOS con detail genérico (no 500)")
    void parseoDeFecha_detailGenerico() {
        ResponseEntity<ProblemDetail> respuesta = handler.datosInvalidos(
                new DateTimeParseException("Text '2026-13-01' could not be parsed", "2026-13-01", 0),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("DATOS_INVALIDOS", respuesta.getBody().getProperties().get("codigo"));
        assertEquals(CodigoError.DATOS_INVALIDOS.getDetail(), respuesta.getBody().getDetail());
    }
    @Test
    @DisplayName("nombreCampoDesdePropertyPath: recorta método y argumento, conserva el campo JSON")
    void nombreCampoDesdePropertyPath() {
        assertEquals("condiciones.montoCapital",
                GlobalExceptionHandler.nombreCampoDesdePropertyPath(
                        "registrarDeuda.argumento[0].condiciones.montoCapital"));
        assertEquals("limit",
                GlobalExceptionHandler.nombreCampoDesdePropertyPath("limit"));
        assertEquals("limit",
                GlobalExceptionHandler.nombreCampoDesdePropertyPath("ejecutar.limit"));
        assertEquals("parametro",
                GlobalExceptionHandler.nombreCampoDesdePropertyPath(null));
    }
}
