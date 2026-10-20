package com.example.saveup.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;

/**
 * Construye el mismo problem+json para los controladores y para los handlers
 * de seguridad (401/403), que corren fuera del DispatcherServlet.
 */
public final class ProblemaHttp {

    private ProblemaHttp() {
    }

    public static ProblemDetail crear(CodigoError codigo,
                                      String detalle,
                                      HttpStatus status,
                                      HttpServletRequest request,
                                      List<ErrorCampo> errores,
                                      String idError) {
        String detail = detalle != null && !detalle.isBlank()
                ? detalle
                : codigo.getDetail();

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detail);
        problema.setTitle(codigo.getTitle());
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("codigo", codigo.name());

        if (errores != null && !errores.isEmpty()) {
            problema.setProperty("errores", errores);
        }
        if (idError != null) {
            problema.setProperty("idError", idError);
        }

        return problema;
    }
}
