package com.example.saveup.service;

import com.example.saveup.model.TokenVerificacion;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoVerificacion;
import com.example.saveup.repository.TokenVerificacionRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.TokenOpaco;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contratos de {@link PasswordService} (T9.3): anti-enumeración en la
 * solicitud, token de un solo uso con 1 hora de vida, invalidación de tokens
 * previos y revocación de todas las sesiones activas en cada cambio de
 * contraseña.
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final String RUT = "12345678-5";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Password123";
    private static final String NUEVA = "NuevaPass123";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenVerificacionRepository tokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CorreoService correoService;

    private PasswordService passwordService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(usuarioRepository, passwordEncoder,
                new com.example.saveup.security.VerificadorDeContrasena(passwordEncoder),
                tokenRepository, refreshTokenService, correoService);

        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Ana");
        usuario.setApellido("Perez");
        usuario.setEmail(EMAIL);
        usuario.setContrasena("$2a$10$hash-actual");
    }

    // ───────────────── solicitarRecuperacion ─────────────────

    @Test
    @DisplayName("solicitarRecuperacion con correo inexistente → no registra token, no invalida nada y no envía correo")
    void solicitarRecuperacion_correoInexistente_noHaceNada() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertDoesNotThrowSinRespuestaVisible();

        verify(tokenRepository, never()).save(any(TokenVerificacion.class));
        verify(tokenRepository, never()).saveAll(any());
        verifyNoInteractions(correoService);
    }

    private void assertDoesNotThrowSinRespuestaVisible() {
        passwordService.solicitarRecuperacion(EMAIL);
    }

    @Test
    @DisplayName("solicitarRecuperacion con correo existente → invalida tokens previos, guarda hash SHA-256 con 1 h y envía correo")
    void solicitarRecuperacion_guardaTokenDeUnUsoYEnviaCorreo() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        TokenVerificacion previo = new TokenVerificacion();
        previo.setUsuario(usuario);
        previo.setTipo(TipoVerificacion.RESET_PASSWORD);
        previo.setTokenHash("hash-previo");
        previo.setFechaExpiracion(new Date(System.currentTimeMillis() + 500_000));
        previo.setUsado(false);
        when(tokenRepository.findByUsuario_RutAndTipoAndUsadoFalse(RUT, TipoVerificacion.RESET_PASSWORD))
                .thenReturn(List.of(previo));

        long antes = System.currentTimeMillis();
        passwordService.solicitarRecuperacion(EMAIL);

        // Tokens previos quedan invalidados (un solo token vigente a la vez)
        assertTrue(previo.estaUsado(), "Los tokens previos se marcan como usados.");
        verify(tokenRepository).saveAll(List.of(previo));

        // El token nuevo se guarda SOLO como hash SHA-256 (64 hex en minúscula)
        ArgumentCaptor<TokenVerificacion> captor = ArgumentCaptor.forClass(TokenVerificacion.class);
        verify(tokenRepository).save(captor.capture());
        TokenVerificacion nuevo = captor.getValue();
        assertEquals(usuario, nuevo.getUsuario());
        assertEquals(TipoVerificacion.RESET_PASSWORD, nuevo.getTipo());
        assertTrue(nuevo.getTokenHash().matches("[0-9a-f]{64}"),
                "El token se persiste como hash SHA-256 hexadecimal.");
        assertTrue(Boolean.FALSE.equals(nuevo.getUsado()), "El token nuevo nace sin usar.");
        assertTrue(nuevo.getFechaExpiracion().getTime() >= antes + 59 * 60_000
                        && nuevo.getFechaExpiracion().getTime() <= System.currentTimeMillis() + 61 * 60_000,
                "El token vale exactamente 1 hora (±1 min).");

        // El correo salió dirigido al usuario con el asunto de recuperación
        verify(correoService).enviar(eq(EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("solicitarRecuperacion trims espacios del correo antes de buscar")
    void solicitarRecuperacion_normalizaCorreo() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        passwordService.solicitarRecuperacion("  test@example.com ");

        // Al existir, se registra token (la búsqueda normalizada encontró al usuario)
        verify(tokenRepository).save(any(TokenVerificacion.class));
    }

    // ───────────────── restablecer ─────────────────

    private TokenVerificacion registroActivo(String token) {
        TokenVerificacion registro = new TokenVerificacion();
        registro.setUsuario(usuario);
        registro.setTipo(TipoVerificacion.RESET_PASSWORD);
        registro.setTokenHash(TokenOpaco.hashSha256Hex(token));
        registro.setFechaExpiracion(new Date(System.currentTimeMillis() + 500_000));
        registro.setUsado(false);
        return registro;
    }

    @Test
    @DisplayName("restablecer con token vigente → marca usado, guarda BCrypt y revoca todas las sesiones")
    void restablecer_exito() {
        String token = TokenOpaco.generar();
        TokenVerificacion registro = registroActivo(token);
        when(tokenRepository.findByTokenHash(TokenOpaco.hashSha256Hex(token)))
                .thenReturn(Optional.of(registro));
        when(tokenRepository.save(registro)).thenReturn(registro);
        when(passwordEncoder.encode(NUEVA)).thenReturn("$2a$10$nueva-bcrypt");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        passwordService.restablecer(token, NUEVA);

        assertTrue(registro.estaUsado(), "El token se consume en el primer uso.");
        assertEquals("$2a$10$nueva-bcrypt", usuario.getContrasena(),
                "La contraseña queda con el hash BCrypt de la nueva.");
        verify(refreshTokenService).revocarTodas(RUT);
    }

    @Test
    @DisplayName("restablecer con token desconocido → TokenVerificacionInvalidoException sin tocar datos")
    void restablecer_tokenDesconocido() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(TokenVerificacionInvalidoException.class,
                () -> passwordService.restablecer("no-existe", NUEVA));

        verify(tokenRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("restablecer con token ya usado → TokenVerificacionInvalidoException (un solo uso)")
    void restablecer_tokenReusado() {
        String token = TokenOpaco.generar();
        TokenVerificacion registro = registroActivo(token);
        registro.setUsado(true);
        when(tokenRepository.findByTokenHash(TokenOpaco.hashSha256Hex(token)))
                .thenReturn(Optional.of(registro));

        assertThrows(TokenVerificacionInvalidoException.class,
                () -> passwordService.restablecer(token, NUEVA));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("restablecer con token expirado (>1h) → TokenVerificacionInvalidoException")
    void restablecer_tokenExpirado() {
        String token = TokenOpaco.generar();
        TokenVerificacion registro = registroActivo(token);
        registro.setFechaExpiracion(new Date(System.currentTimeMillis() - 1_000));
        when(tokenRepository.findByTokenHash(TokenOpaco.hashSha256Hex(token)))
                .thenReturn(Optional.of(registro));

        assertThrows(TokenVerificacionInvalidoException.class,
                () -> passwordService.restablecer(token, NUEVA));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    // ───────────────── cambiarContrasena ─────────────────

    @Test
    @DisplayName("cambiarContrasena con actual correcta → guarda BCrypt y revoca todas las sesiones")
    void cambiarContrasena_exito() {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, usuario.getContrasena())).thenReturn(true);
        when(passwordEncoder.encode(NUEVA)).thenReturn("$2a$10$nueva-bcrypt");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        passwordService.cambiarContrasena(RUT, PASSWORD, NUEVA);

        assertEquals("$2a$10$nueva-bcrypt", usuario.getContrasena());
        assertNotEquals(usuario.getContrasena(), PASSWORD);
        verify(refreshTokenService).revocarTodas(RUT);
    }

    @Test
    @DisplayName("cambiarContrasena con actual incorrecta → BadCredentialsException y sin revocar sesiones")
    void cambiarContrasena_actualErronea() {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123", usuario.getContrasena())).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> passwordService.cambiarContrasena(RUT, "Erronea123", NUEVA));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("cambiarContrasena con RUT inexistente → BadCredentialsException (mensaje genérico)")
    void cambiarContrasena_usuarioNoExiste() {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> passwordService.cambiarContrasena(RUT, PASSWORD, NUEVA));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }
}
