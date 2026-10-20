package com.example.saveup.controller;

import com.example.saveup.dto.LoginResponseDTO;
import com.example.saveup.dto.MensajeResponseDTO;
import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.Usuario;
import com.example.saveup.security.JwtService;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;

    public UsuarioController(UsuarioService usuarioService, JwtService jwtService,
                             SecurityUtils securityUtils) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/register")
    public ResponseEntity<MensajeResponseDTO> registerUsuario(
            @Valid @RequestBody UsuarioRegistroDTO usuarioDTO) {
        usuarioService.registrarUsuario(usuarioDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MensajeResponseDTO("Usuario registrado exitosamente"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> loginUsuario(
            @Valid @RequestBody UsuarioLoginDTO loginDTO) {
        Usuario usuario = usuarioService.autenticarUsuario(loginDTO);
        String token = jwtService.generateToken(usuario.getRut(), usuario.getEmail());
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @GetMapping("/me")
    public ResponseEntity<Usuario> obtenerPerfilActual() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        Usuario usuario = usuarioService.obtenerPerfil(rutAutenticado);
        return ResponseEntity.ok(usuario);
    }
}
