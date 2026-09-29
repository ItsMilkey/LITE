package com.example.saveup.controller;

import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.Usuario;
import com.example.saveup.security.JwtService;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/register")
    public ResponseEntity<?> registerUsuario(@Valid @RequestBody UsuarioRegistroDTO usuarioDTO) {
        try {
            usuarioService.registrarUsuario(usuarioDTO);
            return new ResponseEntity<>(Map.of("message", "Usuario registrado exitosamente"), HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Ocurrió un error inesperado en el servidor."), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUsuario(@Valid @RequestBody UsuarioLoginDTO loginDTO) {
        try {
            Usuario usuario = usuarioService.autenticarUsuario(loginDTO);
            String token = jwtService.generateToken(usuario.getRut(), usuario.getEmail());
            return ResponseEntity.ok(Map.of("token", token));
        } catch (BadCredentialsException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> obtenerPerfilActual() {
        try {
            String rutAutenticado = securityUtils.getAuthenticatedRut();
            Usuario usuario = usuarioService.obtenerPerfil(rutAutenticado);
            return ResponseEntity.ok(usuario);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
}
