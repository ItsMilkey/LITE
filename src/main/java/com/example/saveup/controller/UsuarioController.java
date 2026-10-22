package com.example.saveup.controller;

import com.example.saveup.dto.LoginResponseDTO;
import com.example.saveup.dto.MensajeResponseDTO;
import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.dto.UsuarioResponseDTO;
import com.example.saveup.model.Usuario;
import com.example.saveup.security.JwtService;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Registro, inicio de sesión y perfil del usuario autenticado.")
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
    @Operation(summary = "Registra una cuenta nueva", operationId = "registerUsuario")
    public ResponseEntity<MensajeResponseDTO> registerUsuario(
            @Valid @RequestBody UsuarioRegistroDTO usuarioDTO) {
        usuarioService.registrarUsuario(usuarioDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MensajeResponseDTO("Usuario registrado exitosamente"));
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y entrega un token JWT", operationId = "loginUsuario")
    public ResponseEntity<LoginResponseDTO> loginUsuario(
            @Valid @RequestBody UsuarioLoginDTO loginDTO) {
        Usuario usuario = usuarioService.autenticarUsuario(loginDTO);
        String token = jwtService.generateToken(usuario.getRut(), usuario.getEmail());
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtiene el perfil del usuario autenticado", operationId = "obtenerPerfilActual")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UsuarioResponseDTO> obtenerPerfilActual() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(usuarioService.obtenerPerfil(rutAutenticado));
    }
}
