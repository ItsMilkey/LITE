package com.example.saveup.controller;

import com.example.saveup.dto.RefreshTokenRequestDTO;
import com.example.saveup.dto.RestablecerContrasenaDTO;
import com.example.saveup.dto.SolicitudRecuperacionDTO;
import com.example.saveup.model.AuthRefreshToken;
import com.example.saveup.model.TokenVerificacion;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoVerificacion;
import com.example.saveup.repository.AuthRefreshTokenRepository;
import com.example.saveup.repository.TokenVerificacionRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida de la sesión móvil (T9.1): refresh rotativo, detección de
 * reuso, logout idempotente y acceso público a los endpoints de autenticación.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String RUT = "12345678-5";
    private static final String EMAIL = "test@example.com";
    private static final String TOKEN_ORIGINAL = "refresh-original-del-cliente";
    private static final Date EXPIRACION_FUTURA = Date.from(Instant.parse("2090-01-01T00:00:00Z"));
    private static final Date EXPIRACION_PASADA = Date.from(Instant.parse("2000-01-01T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthRefreshTokenRepository refreshTokenRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private TokenVerificacionRepository tokenVerificacionRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Ana");
        usuario.setApellido("Perez");
        usuario.setEmail(EMAIL);

        when(refreshTokenRepository.save(any(AuthRefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.existsById(RUT)).thenReturn(true);
    }

    /** Registro activo que representa el refresh token que el cliente envía en el cuerpo. */
    private AuthRefreshToken tokenActivo() {
        AuthRefreshToken registro = new AuthRefreshToken();
        registro.setUsuario(usuario);
        registro.setTokenHash(hashSha256Hex(TOKEN_ORIGINAL));
        registro.setFechaExpiracion(EXPIRACION_FUTURA);
        return registro;
    }

    private String cuerpo(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(new RefreshTokenRequestDTO(refreshToken));
    }

    /** Espejo del hash que RefreshTokenService calcula: el test necesita buscar por el mismo valor. */
    private static String hashSha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("POST /auth/refresh → 200 con par nuevo y el token viejo queda rotado (uso + revocación)")
    void refresh_entregaSesionNuevaRotativa() throws Exception {
        when(refreshTokenRepository.findByTokenHash(hashSha256Hex(TOKEN_ORIGINAL.trim())))
                .thenReturn(Optional.of(tokenActivo()));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiraEn").value(matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")));

        ArgumentCaptor<AuthRefreshToken> guardado = ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(guardado.capture());

        List<AuthRefreshToken> tokens = guardado.getAllValues();
        AuthRefreshToken consumido = tokens.get(0);
        assertEquals(hashSha256Hex(TOKEN_ORIGINAL), consumido.getTokenHash(),
                "El token rotado es el original que envió el cliente.");
        assertNotNull(consumido.getFechaUso(), "La rotación marca FECHA_USO.");
        assertNotNull(consumido.getFechaRevocacion(), "La rotación marca FECHA_REVOCACION.");

        AuthRefreshToken nuevo = tokens.get(1);
        assertNotEquals(hashSha256Hex(TOKEN_ORIGINAL), nuevo.getTokenHash(),
                "La rotación emite un refresh nuevo (otro hash).");
        assertNull(nuevo.getFechaRevocacion(), "El refresh nuevo nace activo.");
        assertTrue(nuevo.getFechaExpiracion().after(new Date()),
                "El refresh nuevo vive 90 días desde hoy.");
        verify(refreshTokenRepository, never()).revocarActivasDeUsuario(anyString(), any());
    }

    @Test
    @DisplayName("POST /auth/refresh con token ya usado/revocado → 401 REFRESH_INVALIDO y revoca todas las sesiones del usuario")
    void refresh_tokenYaRevocado_revocaTodasLasSesiones() throws Exception {
        AuthRefreshToken revocado = tokenActivo();
        revocado.setFechaRevocacion(EXPIRACION_PASADA);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revocado));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"));

        verify(refreshTokenRepository).revocarActivasDeUsuario(eq(RUT), any());
        verify(refreshTokenRepository, never()).save(any(AuthRefreshToken.class));
    }

    @Test
    @DisplayName("POST /auth/refresh con token desconocido → 401 REFRESH_INVALIDO sin tocar sesiones")
    void refresh_tokenDesconocido() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("token-inexistente")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"));

        verify(refreshTokenRepository, never()).revocarActivasDeUsuario(anyString(), any());
        verify(refreshTokenRepository, never()).save(any(AuthRefreshToken.class));
    }

    @Test
    @DisplayName("POST /auth/refresh con token expirado (no revocado) → 401 REFRESH_INVALIDO")
    void refresh_tokenExpirado() throws Exception {
        AuthRefreshToken expirado = tokenActivo();
        expirado.setFechaExpiracion(EXPIRACION_PASADA);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expirado));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"));

        verify(refreshTokenRepository, never()).revocarActivasDeUsuario(anyString(), any());
    }

    @Test
    @DisplayName("POST /auth/refresh con usuario eliminado → 401 REFRESH_INVALIDO")
    void refresh_usuarioEliminado() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenActivo()));
        when(usuarioRepository.existsById(RUT)).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"));
    }

    @Test
    @DisplayName("POST /auth/logout → 200, marca FECHA_REVOCACION y no revoca al resto")
    void logout_revocaElToken() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenActivo()));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesión cerrada exitosamente"));

        ArgumentCaptor<AuthRefreshToken> guardado = ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(refreshTokenRepository).save(guardado.capture());
        assertNotNull(guardado.getValue().getFechaRevocacion(), "El logout marca FECHA_REVOCACION.");
        verify(refreshTokenRepository, never()).revocarActivasDeUsuario(anyString(), any());
    }

    @Test
    @DisplayName("Flujo completo: logout → reuso del mismo refresh → 401 REFRESH_INVALIDO y revoca TODAS las sesiones activas")
    void logout_reusoPosterior_revocaTodasLasSesiones() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenActivo()));

        // 1. Logout: 200 y el token recibido queda revocado (solo ese, sin consumo)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesión cerrada exitosamente"));

        ArgumentCaptor<AuthRefreshToken> guardado = ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(refreshTokenRepository).save(guardado.capture());
        assertNotNull(guardado.getValue().getFechaRevocacion(), "El logout marca FECHA_REVOCACION.");
        assertNull(guardado.getValue().getFechaUso(), "El logout NO consume el token (no marca FECHA_USO).");

        // 2. El mismo refresh (ya revocado) vuelve a presentarse como credencial
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TOKEN_ORIGINAL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"))
                .andExpect(jsonPath("$.title").value("Sesión inválida"));

        // 3. El reuso se trata como robo: revoca TODAS las sesiones activas del
        //    usuario y no se emitió ningún par nuevo (solo el save del logout).
        verify(refreshTokenRepository).revocarActivasDeUsuario(eq(RUT), any());
        verify(refreshTokenRepository, times(1)).save(any(AuthRefreshToken.class));
    }

    @Test
    @DisplayName("POST /auth/logout con token desconocido → 200 idempotente (sin enumeración)")
    void logout_tokenDesconocido_idempotente() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("token-que-nunca-existio")))
                .andExpect(status().isOk());

        verify(refreshTokenRepository, never()).save(any(AuthRefreshToken.class));
    }

    @Test
    @DisplayName("Los endpoints de /auth son públicos: el refresh token es la credencial, no el bearer")
    void auth_endpointsPublicos() throws Exception {
        // Sin cabecera Authorization, el refresh falla por contenido (REFRESH_INVALIDO),
        // no por falta de autenticación (NO_AUTENTICADO).
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("lo-que-sea")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"))
                .andExpect(jsonPath("$.title").value("Sesión inválida"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("lo-que-sea")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/refresh sin refreshToken en el cuerpo → 400 VALIDACION_FALLIDA")
    void refresh_sinToken_validacion() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("refreshToken"));
    }

    // ==================== T9.3: recuperación de contraseña ====================

    private String cuerpoRecuperacion(String email) throws Exception {
        return objectMapper.writeValueAsString(new SolicitudRecuperacionDTO(email));
    }

    private String cuerpoRestablecer(String token, String nuevaContrasena) throws Exception {
        return objectMapper.writeValueAsString(new RestablecerContrasenaDTO(token, nuevaContrasena));
    }

    /** Registro de token_de_validación que el servicio debería encontrar por hash. */
    private TokenVerificacion registroConTokenActivo(String tokenEnClaro) {
        TokenVerificacion registro = new TokenVerificacion();
        registro.setUsuario(usuario);
        registro.setTipo(TipoVerificacion.RESET_PASSWORD);
        registro.setTokenHash(hashSha256Hex(tokenEnClaro));
        registro.setFechaExpiracion(EXPIRACION_FUTURA);
        registro.setUsado(false);
        return registro;
    }

    @Test
    @DisplayName("POST /auth/recuperar-contrasena → 200 genérico con correo existente y con inexistente (anti-enumeración)")
    void recuperarContrasena_respondeIgualSiempre() throws Exception {
        String mensajeGenerico = "Si el correo está registrado, enviamos las instrucciones para restablecer la contraseña.";

        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(tokenVerificacionRepository.findByUsuario_RutAndTipoAndUsadoFalse(RUT, TipoVerificacion.RESET_PASSWORD))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRecuperacion(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(mensajeGenerico));

        // Correo inexistente: MISMA respuesta 200 y mismo mensaje.
        when(usuarioRepository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRecuperacion("nadie@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(mensajeGenerico));

        // Solo la solicitud del correo existente registra token.
        verify(tokenVerificacionRepository, times(1)).save(any(TokenVerificacion.class));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("Flujo completo: recuperar → token por log → restablecer guarda BCrypt y revoca todas las sesiones")
    void flujoCompleto_recuperarYRestablecer(CapturedOutput salida) throws Exception {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenVerificacionRepository.findByUsuario_RutAndTipoAndUsadoFalse(RUT, TipoVerificacion.RESET_PASSWORD))
                .thenReturn(List.of());
        when(tokenVerificacionRepository.save(any(TokenVerificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 1. Solicitud de recuperación
        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRecuperacion(EMAIL)))
                .andExpect(status().isOk());

        // 2. El token viaja en el correo (log-only en tests): se extrae del log
        Matcher tokenEnLog = Pattern
                .compile("Token de un solo uso, válido por 1 hora: (\\S+)")
                .matcher(salida.getAll());
        assertTrue(tokenEnLog.find(), "El correo logueado debe contener el token de un solo uso.");
        String token = tokenEnLog.group(1);

        // 3. Restablecimiento con el token exacto (hash SHA-256 )
        when(tokenVerificacionRepository.findByTokenHash(hashSha256Hex(token)))
                .thenReturn(Optional.of(registroConTokenActivo(token)));

        mockMvc.perform(post("/api/v1/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecer(token, "NuevaPass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña restablecida. Inicia sesión con tu nueva contraseña."));

        // La nueva contraseña quedó con BCrypt (no en claro)
        ArgumentCaptor<Usuario> usuarioGuardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioGuardado.capture());
        assertNotEquals("NuevaPass123", usuarioGuardado.getValue().getContrasena());
        assertTrue(usuarioGuardado.getValue().getContrasena().startsWith("$2"));

        // El token se marca como usado y se revocan TODAS las sesiones activas
        ArgumentCaptor<TokenVerificacion> tokenGuardado = ArgumentCaptor.forClass(TokenVerificacion.class);
        verify(tokenVerificacionRepository, times(2)).save(tokenGuardado.capture());
        assertTrue(tokenGuardado.getValue().estaUsado(), "El token queda consumido tras el restablecimiento.");
        verify(refreshTokenRepository).revocarActivasDeUsuario(eq(RUT), any());
    }

    @Test
    @DisplayName("POST /auth/restablecer-contrasena con token desconocido → 400 TOKEN_VERIFICACION_INVALIDO")
    void restablecer_tokenDesconocido() throws Exception {
        when(tokenVerificacionRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecer("token-inexistente", "NuevaPass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_VERIFICACION_INVALIDO"));
    }

    @Test
    @DisplayName("POST /auth/restablecer-contrasena con token ya usado → 400 TOKEN_VERIFICACION_INVALIDO")
    void restablecer_tokenReusado() throws Exception {
        TokenVerificacion usado = registroConTokenActivo("token-ya-usado");
        usado.setUsado(true);
        when(tokenVerificacionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usado));

        mockMvc.perform(post("/api/v1/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecer("token-ya-usado", "NuevaPass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_VERIFICACION_INVALIDO"));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("POST /auth/restablecer-contrasena con token expirado → 400 TOKEN_VERIFICACION_INVALIDO")
    void restablecer_tokenExpirado() throws Exception {
        TokenVerificacion expirado = registroConTokenActivo("token-expirado");
        expirado.setFechaExpiracion(EXPIRACION_PASADA);
        when(tokenVerificacionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expirado));

        mockMvc.perform(post("/api/v1/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecer("token-expirado", "NuevaPass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_VERIFICACION_INVALIDO"));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("POST /auth/restablecer-contrasena con contraseña débil → 400 VALIDACION_FALLIDA")
    void restablecer_contrasenaDebil_validacion() throws Exception {
        mockMvc.perform(post("/api/v1/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecer("token-lo-que-sea", "corta1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("nuevaContrasena"));
    }

    @Test
    @DisplayName("Los endpoints de recuperación son públicos: sin bearer responden lógica de negocio, no 401")
    void recuperacion_endpointsPublicos() throws Exception {
        when(tokenVerificacionRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/recuperar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRecuperacion("cualquiera@example.com")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRestablecer("cualquiera", "NuevaPass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_VERIFICACION_INVALIDO"));
    }
}
