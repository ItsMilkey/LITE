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
}
