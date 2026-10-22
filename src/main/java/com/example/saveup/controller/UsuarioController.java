package com.example.saveup.controller;

import com.example.saveup.dto.LoginResponseDTO;
import com.example.saveup.dto.MensajeResponseDTO;
import com.example.saveup.dto.CambioContrasenaDTO;
import com.example.saveup.dto.UsuarioEliminacionDTO;
import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.dto.UsuarioResponseDTO;
import com.example.saveup.model.Usuario;
import com.example.saveup.security.LoginRateLimiterService;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.PasswordService;
import com.example.saveup.service.RefreshTokenService;
import com.example.saveup.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Registro, inicio de sesión y perfil del usuario autenticado.")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordService passwordService;
    private final SecurityUtils securityUtils;
    private final LoginRateLimiterService loginRateLimiterService;

    public UsuarioController(UsuarioService usuarioService, RefreshTokenService refreshTokenService,
                             PasswordService passwordService, SecurityUtils securityUtils,
                             LoginRateLimiterService loginRateLimiterService) {
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.passwordService = passwordService;
        this.securityUtils = securityUtils;
        this.loginRateLimiterService = loginRateLimiterService;
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
    @Operation(summary = "Inicia sesión y entrega el par de tokens de la sesión",
            description = """
                    Con acierto entrega {token, refreshToken, expiraEn} y limpia el contador
                    de intentos fallidos. Cinco intentos fallidos con el mismo correo e IP
                    bloquean esa combinación 15 minutos: mientras esté bloqueada responde
                    429 INTENTOS_LOGIN_EXCEDIDOS antes de consultar credenciales.""",
            operationId = "loginUsuario")
    public ResponseEntity<LoginResponseDTO> loginUsuario(
            @Valid @RequestBody UsuarioLoginDTO loginDTO, HttpServletRequest request) {
        String ipCliente = loginRateLimiterService.resolverIp(request);
        Usuario usuario = usuarioService.autenticarUsuario(loginDTO, ipCliente);
        RefreshTokenService.Sesion sesion = refreshTokenService.iniciarSesion(usuario);
        return ResponseEntity.ok(new LoginResponseDTO(sesion.token(), sesion.refreshToken(), sesion.expiracion()));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtiene el perfil del usuario autenticado", operationId = "obtenerPerfilActual")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UsuarioResponseDTO> obtenerPerfilActual() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(usuarioService.obtenerPerfil(rutAutenticado));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Elimina de forma permanente la cuenta del usuario autenticado",
            description = """
                    Borra en cascada todos los datos del usuario (movimientos, deudas,
                    metas, presupuestos y sesiones) y la cuenta misma. Es irreversible:
                    exige confirmar la contraseña actual. Requerido por las políticas de
                    Apple y Google para apps con cuentas.""",
            operationId = "eliminarCuentaActual")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Cuenta eliminada de forma permanente")
    })
    public ResponseEntity<Void> eliminarCuentaActual(
            @Valid @RequestBody UsuarioEliminacionDTO confirmacion) {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        usuarioService.eliminarCuenta(rutAutenticado, confirmacion.contrasena());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/contrasena")
    @Operation(summary = "Cambia la contraseña del usuario autenticado",
            description = """
                    Exige la contraseña actual, guarda la nueva con BCrypt y revoca
                    TODAS las sesiones activas del usuario (otros dispositivos quedan
                    fuera). El JWT de acceso actual sigue siendo válido hasta expirar
                    (máximo 15 min); el cliente inicia sesión de nuevo.""",
            operationId = "cambiarContrasena")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MensajeResponseDTO> cambiarContrasenaActual(
            @Valid @RequestBody CambioContrasenaDTO cambio) {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        passwordService.cambiarContrasena(rutAutenticado, cambio.contrasenaActual(), cambio.nuevaContrasena());
        return ResponseEntity.ok(new MensajeResponseDTO(
                "Contraseña actualizada. Inicia sesión con tu nueva contraseña."));
    }
}
