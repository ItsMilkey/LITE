package com.example.saveup.controller;

import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.model.Usuario;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUsuario(@Valid @RequestBody UsuarioRegistroDTO usuarioDTO) {
        try {
            usuarioService.registrarUsuario(usuarioDTO);
            // Respuesta exitosa sin devolver datos sensibles.
            return new ResponseEntity<>(Map.of("message", "Usuario registrado exitosamente"), HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            // Error de negocio (ej. RUT o email duplicado).
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        } catch (Exception e) {
            // Captura de cualquier otro error inesperado.
            return new ResponseEntity<>(Map.of("error", "Ocurrió un error inesperado en el servidor."), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUsuario(@Valid @RequestBody UsuarioLoginDTO loginDTO) {
        try {
            Usuario usuario = usuarioService.autenticarUsuario(loginDTO);

            // ¡IMPORTANTE! NUNCA DEVUELVAS LA CONTRASEÑA, NI SIQUIERA EL HASH.
            // Al poner el campo en null, el JSON de respuesta no lo incluirá.
            usuario.setContrasena(null); 

            return ResponseEntity.ok(usuario);

        } catch (BadCredentialsException e) {
            // Si las credenciales son incorrectas, devolvemos un error 401 Unauthorized.
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }
}
