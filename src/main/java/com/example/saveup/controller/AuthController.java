package com.example.saveup.controller;

import com.example.saveup.dto.LoginResponseDTO;
import com.example.saveup.dto.MensajeResponseDTO;
import com.example.saveup.dto.RefreshTokenRequestDTO;
import com.example.saveup.dto.RestablecerContrasenaDTO;
import com.example.saveup.dto.SolicitudRecuperacionDTO;
import com.example.saveup.service.PasswordService;
import com.example.saveup.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ciclo de vida de la sesión móvil (T9.1) y recuperación de contraseña
 * (T9.3). Todos los endpoints son públicos: el refresh token es la credencial
 * y la recuperación va dirigida a un correo registrado.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación",
        description = "Renovación y cierre de sesión por refresh token, y recuperación de contraseña.")
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final PasswordService passwordService;

    public AuthController(RefreshTokenService refreshTokenService, PasswordService passwordService) {
        this.refreshTokenService = refreshTokenService;
        this.passwordService = passwordService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renueva la sesión rotando el refresh token",
            description = """
                    Consume el refresh token actual y entrega un par nuevo
                    {token, refreshToken, expiraEn}. El access token expira pronto y el
                    cliente, al recibir 401 TOKEN_EXPIRADO en una solicitud protegida,
                    debe llamar aquí una única vez. Si el refresh recibido ya fue
                    usado o revocado la respuesta es 401 REFRESH_INVALIDO: el usuario
                    debe volver a iniciar sesión.""",
            operationId = "refrescarSesion")
    public ResponseEntity<LoginResponseDTO> refrescarSesion(
            @Valid @RequestBody RefreshTokenRequestDTO solicitud) {
        RefreshTokenService.Sesion sesion = refreshTokenService.renovarSesion(solicitud.refreshToken());
        return ResponseEntity.ok(new LoginResponseDTO(sesion.token(), sesion.refreshToken(), sesion.expiracion()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cierra la sesión revocando el refresh token",
            description = """
                    Marca el refresh token como revocado. Una vez revocado, /auth/refresh
                    lo rechaza. Idempotente: si el token ya está revocado o no existe la
                    respuesta es igual (no filtra información).""", operationId = "cerrarSesion")
    public ResponseEntity<MensajeResponseDTO> cerrarSesion(
            @Valid @RequestBody RefreshTokenRequestDTO solicitud) {
        refreshTokenService.cerrarSesion(solicitud.refreshToken());
        return ResponseEntity.ok(new MensajeResponseDTO("Sesión cerrada exitosamente"));
    }

    @PostMapping("/recuperar-contrasena")
    @Operation(summary = "Solicita el restablecimiento de la contraseña",
            description = """
                    Envía (o loguea, con app.mail.log-only=true) un token de un solo uso
                    válido 1 hora al correo registrado. Responde 200 con un mensaje
                    genérico SIEMPRE, exista o no el correo, para no permitir enumerar
                    cuentas.""", operationId = "solicitarRecuperacionContrasena")
    public ResponseEntity<MensajeResponseDTO> recuperarContrasena(
            @Valid @RequestBody SolicitudRecuperacionDTO solicitud) {
        passwordService.solicitarRecuperacion(solicitud.email());
        return ResponseEntity.ok(new MensajeResponseDTO(
                "Si el correo está registrado, enviamos las instrucciones para restablecer la contraseña."));
    }

    @PostMapping("/restablecer-contrasena")
    @Operation(summary = "Restablece la contraseña con el token de un solo uso",
            description = """
                    Consume el token (1 uso, 1 hora), guarda la nueva contraseña con
                    BCrypt y revoca todas las sesiones activas del usuario. Token
                    inválido, reusado o expirado → 400 TOKEN_VERIFICACION_INVALIDO.""",
            operationId = "restablecerContrasena")
    public ResponseEntity<MensajeResponseDTO> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaDTO solicitud) {
        passwordService.restablecer(solicitud.token(), solicitud.nuevaContrasena());
        return ResponseEntity.ok(new MensajeResponseDTO(
                "Contraseña restablecida. Inicia sesión con tu nueva contraseña."));
    }
}
