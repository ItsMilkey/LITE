package com.example.saveup.service;

import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.saveup.dto.UsuarioLoginDTO;
import org.springframework.security.authentication.BadCredentialsException;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Registra un nuevo usuario en el sistema.
     * Valida la existencia, codifica la contraseña y guarda el nuevo usuario.
     *
     * @param usuarioDTO Datos del usuario provenientes del frontend.
     * @return El usuario guardado en la base de datos.
     * @throws IllegalStateException si el RUT o el email ya están en uso.
     */
    public Usuario registrarUsuario(UsuarioRegistroDTO usuarioDTO) {
        if (usuarioRepository.existsById(usuarioDTO.getRut())) {
            throw new IllegalStateException("El RUT ingresado ya está registrado.");
        }
        if (usuarioRepository.existsByEmail(usuarioDTO.getEmail())) {
            throw new IllegalStateException("El correo electrónico ya está en uso.");
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setRut(usuarioDTO.getRut());
        nuevoUsuario.setNombre(usuarioDTO.getNombre());
        nuevoUsuario.setApellido(usuarioDTO.getApellido());
        nuevoUsuario.setEmail(usuarioDTO.getEmail());

        // --- PUNTO CLAVE DE SEGURIDAD ---
        // Codificar la contraseña antes de guardarla.
        nuevoUsuario.setContrasena(passwordEncoder.encode(usuarioDTO.getContrasena()));

        return usuarioRepository.save(nuevoUsuario);
    }

    /**
     * Autentica a un usuario comparando las credenciales con la base de datos.
     *
     * @param loginDTO Objeto con el email y la contraseña.
     * @return El objeto Usuario si las credenciales son correctas.
     * @throws BadCredentialsException si el usuario no existe o la contraseña es incorrecta.
     */
    public Usuario autenticarUsuario(UsuarioLoginDTO loginDTO) {
        // Busca al usuario por email. Si no lo encuentra, lanza excepción.
        Usuario usuario = usuarioRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas."));

        // Compara la contraseña del DTO con la contraseña hasheada de la BD.
        if (passwordEncoder.matches(loginDTO.getContrasena(), usuario.getContrasena())) {
            return usuario; // Si coinciden, devuelve el usuario.
        } else {
            // Si no coinciden, lanza la misma excepción para no dar pistas a atacantes.
            throw new BadCredentialsException("Credenciales inválidas.");
        }
    }

}
