package com.example.saveup.error;

import com.example.saveup.security.BloqueoPorFuerzaBrutaException;
import com.example.saveup.service.RefreshTokenInvalidoException;
import com.example.saveup.service.TokenVerificacionInvalidoException;
import com.example.saveup.service.finanzas.CalculoFinancieroException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Único punto donde se traducen excepciones a problem+json. Los controladores
 * no capturan excepciones.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MENSAJE_FORMATO_INVALIDO = "El valor no tiene el formato esperado";

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ProblemDetail> validacion(BindException ex, HttpServletRequest request) {
        List<FieldError> erroresCampo = ex.getBindingResult().getFieldErrors();
        if (!erroresCampo.isEmpty() && erroresCampo.stream().allMatch(this::esTypeMismatch)) {
            return respuesta(CodigoError.PARAMETRO_INVALIDO, null, HttpStatus.BAD_REQUEST,
                    request, erroresTipoMismatch(erroresCampo), null);
        }
        return respuesta(CodigoError.VALIDACION_FALLIDA, null, HttpStatus.BAD_REQUEST,
                request, erroresDe(erroresCampo), null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> validacionParametros(HandlerMethodValidationException ex,
                                                              HttpServletRequest request) {
        List<ErrorCampo> errores = new ArrayList<>();
        ex.getAllValidationResults().forEach(resultado -> {
            String campoParametro = resultado.getMethodParameter().getParameterName();
            resultado.getResolvableErrors().forEach(error -> {
                String campo = error instanceof FieldError fieldError
                        && fieldError.getField() != null && !fieldError.getField().isBlank()
                        ? fieldError.getField()
                        : nombreCampoDesdePropertyPath(campoParametro);
                errores.add(new ErrorCampo(campo, error.getDefaultMessage()));
            });
        });
        return respuesta(CodigoError.VALIDACION_FALLIDA, null, HttpStatus.BAD_REQUEST,
                request, errores, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> validacionRestricciones(ConstraintViolationException ex,
                                                                 HttpServletRequest request) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(violacion -> new ErrorCampo(
                        nombreCampoDesdePropertyPath(violacion.getPropertyPath().toString()),
                        violacion.getMessage()))
                .toList();
        return respuesta(CodigoError.VALIDACION_FALLIDA, null, HttpStatus.BAD_REQUEST,
                request, errores, null);
    }

    /**
     * Nombre de campo para el cliente a partir del propertyPath de una
     * restricción de método. T8b pide el nombre JSON (p. ej.
     * {@code condiciones.montoCapital}), no el rastro interno: la forma
     * {@code metodo.argumento[0].condiciones.montoCapital} se recorta hasta
     * la cola del campo y un nombre simple ({@code limit}) sigue igual.
     */
    static String nombreCampoDesdePropertyPath(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "parametro";
        }
        int ultimoCierre = propertyPath.lastIndexOf(']');
        if (ultimoCierre >= 0) {
            int desde = ultimoCierre + 1;
            if (desde < propertyPath.length() && propertyPath.charAt(desde) == '.') {
                desde++;
            }
            return propertyPath.substring(desde);
        }
        int ultimoPunto = propertyPath.lastIndexOf('.');
        return ultimoPunto >= 0 ? propertyPath.substring(ultimoPunto + 1) : propertyPath;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> solicitudIlegible(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        return respuesta(CodigoError.SOLICITUD_INVALIDA, null, HttpStatus.BAD_REQUEST,
                request, List.of(), null);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MissingPathVariableException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ProblemDetail> parametroInvalido(Exception ex, HttpServletRequest request) {
        return respuesta(CodigoError.PARAMETRO_INVALIDO, null, HttpStatus.BAD_REQUEST,
                request, List.of(), null);
    }

    /**
     * El {@code detail} solo muestra mensajes escritos por el repo
     * ({@link DatosInvalidosException} y {@link CalculoFinancieroException}).
     * Un IAE de librería (incluido {@link NumberFormatException}, su subclase)
     * o un parseo de fechas fallido no exponen su texto: puede venir en inglés
     * o revelar clases. El cliente recibe el detail genérico y la causa queda
     * en el log para diagnosticar.
     */
    @ExceptionHandler({IllegalArgumentException.class, CalculoFinancieroException.class,
            DateTimeParseException.class})
    public ResponseEntity<ProblemDetail> datosInvalidos(RuntimeException ex, HttpServletRequest request) {
        if (ex instanceof DatosInvalidosException || ex instanceof CalculoFinancieroException) {
            return respuesta(CodigoError.DATOS_INVALIDOS, ex.getMessage(), HttpStatus.BAD_REQUEST,
                    request, List.of(), null);
        }
        LOG.warn("Entrada inválida no controlada en {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respuesta(CodigoError.DATOS_INVALIDOS, CodigoError.DATOS_INVALIDOS.getDetail(),
                HttpStatus.BAD_REQUEST, request, List.of(), null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> noEncontrado(EntityNotFoundException ex, HttpServletRequest request) {
        return respuesta(CodigoError.NO_ENCONTRADO, null, HttpStatus.NOT_FOUND,
                request, List.of(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> prohibido(AccessDeniedException ex, HttpServletRequest request) {
        return respuesta(CodigoError.PROHIBIDO, CodigoError.PROHIBIDO.getDetail(), HttpStatus.FORBIDDEN,
                request, List.of(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> credencialesInvalidas(BadCredentialsException ex,
                                                               HttpServletRequest request) {
        return respuesta(CodigoError.CREDENCIALES_INVALIDAS, null, HttpStatus.UNAUTHORIZED,
                request, List.of(), null);
    }

    @ExceptionHandler(RefreshTokenInvalidoException.class)
    public ResponseEntity<ProblemDetail> refreshInvalido(RefreshTokenInvalidoException ex,
                                                         HttpServletRequest request) {
        return respuesta(CodigoError.REFRESH_INVALIDO, null, HttpStatus.UNAUTHORIZED,
                request, List.of(), null);
    }

    @ExceptionHandler(TokenVerificacionInvalidoException.class)
    public ResponseEntity<ProblemDetail> tokenVerificacionInvalido(TokenVerificacionInvalidoException ex,
                                                                   HttpServletRequest request) {
        return respuesta(CodigoError.TOKEN_VERIFICACION_INVALIDO, null, HttpStatus.BAD_REQUEST,
                request, List.of(), null);
    }

    @ExceptionHandler(BloqueoPorFuerzaBrutaException.class)
    public ResponseEntity<ProblemDetail> bloqueoPorFuerzaBruta(BloqueoPorFuerzaBrutaException ex,
                                                               HttpServletRequest request) {
        return respuesta(CodigoError.INTENTOS_LOGIN_EXCEDIDOS, null, HttpStatus.TOO_MANY_REQUESTS,
                request, List.of(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflictoIntegridad(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        String causa = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        LOG.warn("Conflicto de integridad en {}: {}", request.getRequestURI(), causa);
        return respuesta(CodigoError.CONFLICTO_REGLA_NEGOCIO, null, HttpStatus.CONFLICT,
                request, List.of(), null, false);
    }

    @ExceptionHandler(ErrorNegocio.class)
    public ResponseEntity<ProblemDetail> errorNegocio(ErrorNegocio ex, HttpServletRequest request) {
        return respuesta(ex.getCodigo(), ex.getMessage(), ex.getCodigo().getStatus(),
                request, List.of(), null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> rutaNoEncontrada(Exception ex, HttpServletRequest request) {
        return respuesta(CodigoError.NO_ENCONTRADO, null, HttpStatus.NOT_FOUND,
                request, List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> metodoNoSoportado(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) {
        return respuesta(CodigoError.SOLICITUD_INVALIDA, null, HttpStatus.METHOD_NOT_ALLOWED,
                request, List.of(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> tipoContenidoNoSoportado(HttpMediaTypeNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return respuesta(CodigoError.SOLICITUD_INVALIDA, null, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                request, List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> errorInterno(Exception ex, HttpServletRequest request) {
        String idError = UUID.randomUUID().toString();
        LOG.error("Error interno idError={} en {}", idError, request.getRequestURI(), ex);
        return respuesta(CodigoError.ERROR_INTERNO, null, HttpStatus.INTERNAL_SERVER_ERROR,
                request, List.of(), idError);
    }

    private List<ErrorCampo> erroresDe(List<FieldError> erroresCampo) {
        return erroresCampo.stream()
                .map(this::aErrorCampo)
                .toList();
    }

    private List<ErrorCampo> erroresTipoMismatch(List<FieldError> erroresCampo) {
        return erroresCampo.stream()
                .map(error -> new ErrorCampo(error.getField(), MENSAJE_FORMATO_INVALIDO))
                .toList();
    }

    private boolean esTypeMismatch(FieldError error) {
        return error.isBindingFailure() || "typeMismatch".equals(error.getCode());
    }

    private ErrorCampo aErrorCampo(FieldError error) {
        String mensaje = esTypeMismatch(error)
                ? MENSAJE_FORMATO_INVALIDO
                : error.getDefaultMessage();
        if (mensaje == null || mensaje.isBlank()) {
            mensaje = MENSAJE_FORMATO_INVALIDO;
        }
        return new ErrorCampo(error.getField(), mensaje);
    }

    private ResponseEntity<ProblemDetail> respuesta(CodigoError codigo,
                                                    String detalle,
                                                    HttpStatus status,
                                                    HttpServletRequest request,
                                                    List<ErrorCampo> errores,
                                                    String idError) {
        return respuesta(codigo, detalle, status, request, errores, idError, true);
    }

    private ResponseEntity<ProblemDetail> respuesta(CodigoError codigo,
                                                    String detalle,
                                                    HttpStatus status,
                                                    HttpServletRequest request,
                                                    List<ErrorCampo> errores,
                                                    String idError,
                                                    boolean registrarInfo) {
        if (registrarInfo && status.is4xxClientError()) {
            LOG.info("Error 4xx status={} codigo={} metodo={} path={}",
                    status.value(), codigo.name(), request.getMethod(), request.getRequestURI());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemaHttp.crear(codigo, detalle, status, request, errores, idError));
    }
}
