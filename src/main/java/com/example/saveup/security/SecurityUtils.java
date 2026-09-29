package com.example.saveup.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    /**
     * Obtiene el RUT del usuario autenticado a partir del Subject del token JWT
     * cargado en el SecurityContextHolder.
     *
     * @return El RUT del usuario autenticado.
     * @throws AccessDeniedException si no existe una sesión autenticada válida.
     */
    public String getAuthenticatedRut() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("No hay una sesión autenticada válida.");
        }
        return auth.getName();
    }

    /**
     * Valida que el RUT objetivo coincida estrictamente con el RUT del usuario en sesión.
     * Previene ataques de referencia directa insegura a objetos (IDOR).
     *
     * @param rutTarget RUT del recurso solicitado.
     * @throws AccessDeniedException si los RUTs no coinciden.
     */
    public void validarPropietario(String rutTarget) {
        if (rutTarget == null || rutTarget.isBlank()) {
            throw new AccessDeniedException("El RUT del recurso no es válido.");
        }
        String rutAutenticado = getAuthenticatedRut();
        if (!rutAutenticado.equalsIgnoreCase(rutTarget.trim())) {
            throw new AccessDeniedException("Acceso denegado: No tienes permisos sobre los recursos de este usuario.");
        }
    }
}
