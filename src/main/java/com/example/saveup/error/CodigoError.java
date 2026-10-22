package com.example.saveup.error;

import org.springframework.http.HttpStatus;

/**
 * Códigos estables del contrato de errores de la API. El cliente móvil decide
 * qué mensaje mostrar a partir de {@link #name()}; el {@code detail} es solo un
 * texto de respaldo en español.
 */
public enum CodigoError {

    VALIDACION_FALLIDA(HttpStatus.BAD_REQUEST, "Datos inválidos",
            "Revisa los datos ingresados."),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "Solicitud inválida",
            "La solicitud no es válida."),
    PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
            "Faltan parámetros obligatorios o tienen un formato incorrecto."),
    DATOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Datos inválidos",
            "Algunos datos no son válidos."),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "No autenticado",
            "No se pudo autenticar la solicitud."),
    TOKEN_EXPIRADO(HttpStatus.UNAUTHORIZED, "Sesión expirada",
            "Tu sesión ha expirado. Inicia sesión nuevamente."),
    REFRESH_INVALIDO(HttpStatus.UNAUTHORIZED, "Sesión inválida",
            "Tu sesión ya no es válida. Inicia sesión nuevamente."),
    TOKEN_VERIFICACION_INVALIDO(HttpStatus.BAD_REQUEST, "Token inválido",
            "El token no es válido o ya no está vigente. Solicita uno nuevo."),
    INTENTOS_LOGIN_EXCEDIDOS(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos",
            "Demasiados intentos de inicio de sesión fallidos. Espera unos minutos y vuelve a intentarlo."),
    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "Credenciales inválidas",
            "El correo o la contraseña no son correctos."),
    PROHIBIDO(HttpStatus.FORBIDDEN, "Prohibido",
            "No tienes permisos para acceder a este recurso."),
    NO_ENCONTRADO(HttpStatus.NOT_FOUND, "No encontrado",
            "No se encontró el recurso solicitado."),
    CONFLICTO_REGLA_NEGOCIO(HttpStatus.CONFLICT, "Conflicto",
            "La operación no se puede completar por una regla de negocio."),
    USUARIO_YA_REGISTRADO(HttpStatus.CONFLICT, "Cuenta ya registrada",
            "Ya existe una cuenta con esos datos."),
    DEUDA_SOBREPAGO(HttpStatus.CONFLICT, "Pago excesivo",
            "El monto del pago supera lo que queda por pagar."),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
            "Ocurrió un error inesperado");

    private final HttpStatus status;
    private final String title;
    private final String detail;

    CodigoError(HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }
}
