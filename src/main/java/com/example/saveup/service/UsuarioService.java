package com.example.saveup.service;

import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
}
