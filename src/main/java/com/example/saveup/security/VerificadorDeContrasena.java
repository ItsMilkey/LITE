package com.example.saveup.security;

import com.example.saveup.model.Usuario;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Único punto que compara una contraseña en claro contra el hash guardado
 * (T9.2/T9.3). Las confirmaciones críticas — borra cuenta y cambio de
 * contraseña — pasan todas por aquí y reciben el MISMO mensaje genérico de
 * {@link BadCredentialsException}, que no filtra si falló el usuario o la
 * contraseña.
 * <p>
 * No cubre el login: ahí la verificación vive en
 * {@code UsuarioService.autenticarUsuario} porque debe notificar el fallo al
 * freno de fuerza bruta ({@link LoginRateLimiterService}) antes de lanzar.
 */
@Service
public class VerificadorDeContrasena {

    private final PasswordEncoder passwordEncoder;

    public VerificadorDeContrasena(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifica la contraseña actual del usuario.
     *
     * @throws BadCredentialsException con mensaje genérico si no hay usuario
     *                                 que verificar o la contraseña no coincide.
     */
    public void verificarContrasena(Usuario usuario, String contrasenaPlana) {
        if (usuario == null || contrasenaPlana == null
                || !passwordEncoder.matches(contrasenaPlana, usuario.getContrasena())) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }
    }
}
