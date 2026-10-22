package com.example.saveup.controller;

import com.example.saveup.dto.CambioContrasenaDTO;
import com.example.saveup.dto.UsuarioEliminacionDTO;
import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.AuthRefreshTokenRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que ningún endpoint de usuario exponga el hash de la contraseña.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioControllerTest {

    private static final String RUT = "12345678-5";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Password123";
    /** Hash de ejemplo que actúa como marcador: si apareciera en cualquier respuesta JSON, se filtra. */
    private static final String HASH_EN_BD = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private MetaAhorroRepository metaAhorroRepository;

    @MockBean
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    private Usuario usuarioConHash;
    private String validToken;

    @BeforeEach
    void setUp() {
        usuarioConHash = new Usuario();
        usuarioConHash.setRut(RUT);
        usuarioConHash.setNombre("Ana");
        usuarioConHash.setApellido("Perez");
        usuarioConHash.setEmail(EMAIL);
        usuarioConHash.setContrasena(HASH_EN_BD);
        usuarioConHash.setFechaRegistro(Date.from(Instant.parse("2026-10-15T12:00:00Z")));

        validToken = jwtService.generateToken(RUT, EMAIL);
    }

    @Test
    @DisplayName("POST /register → 201 y el cuerpo no contiene el hash ni el campo contrasena")
    void register_noExponeHash() throws Exception {
        when(usuarioRepository.existsById(RUT)).thenReturn(false);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioConHash);
        when(metaAhorroRepository.save(any(MetaAhorro.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UsuarioRegistroDTO dto = new UsuarioRegistroDTO();
        dto.setRut(RUT);
        dto.setNombre("Ana");
        dto.setApellido("Perez");
        dto.setEmail(EMAIL);
        dto.setContrasena(PASSWORD);

        mockMvc.perform(post("/api/v1/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Usuario registrado exitosamente"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(result -> assertFalse(
                        result.getResponse().getContentAsString().contains(HASH_EN_BD),
                        "La respuesta de /register no debe contener el hash de la contraseña"));
    }

    @Test
    @DisplayName("POST /login → 200 con token y el cuerpo no contiene el hash")
    void login_noExponeHash() throws Exception {
        // Hash real generado con el mismo encoder que usa la aplicación, para que el
        // login recorra el flujo completo hasta el 200.
        String hashReal = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(PASSWORD);
        Usuario usuarioLogin = new Usuario();
        usuarioLogin.setRut(RUT);
        usuarioLogin.setNombre("Ana");
        usuarioLogin.setApellido("Perez");
        usuarioLogin.setEmail(EMAIL);
        usuarioLogin.setContrasena(hashReal);
        usuarioLogin.setFechaRegistro(Date.from(Instant.parse("2026-10-15T12:00:00Z")));

        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioLogin));

        UsuarioLoginDTO dto = new UsuarioLoginDTO();
        dto.setEmail(EMAIL);
        dto.setContrasena(PASSWORD);

        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertFalse(body.contains(hashReal),
                            "La respuesta de /login no debe contener el hash de la contraseña");
                });
    }

    @Test
    @DisplayName("GET /me → 200 con UsuarioResponseDTO y sin hash")
    void me_noExponeHash() throws Exception {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHash));

        mockMvc.perform(get("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rut").value(RUT))
                .andExpect(jsonPath("$.nombre").value(usuarioConHash.getNombre()))
                .andExpect(jsonPath("$.apellido").value(usuarioConHash.getApellido()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fechaRegistro").exists())
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(result -> assertFalse(
                        result.getResponse().getContentAsString().contains(HASH_EN_BD),
                        "La respuesta de /me no debe contener el hash de la contraseña"));
    }

    @Test
    @DisplayName("La entidad Usuario tiene la contraseña excluida de toString() (@ToString.Exclude)")
    void toString_noContieneHash() {
        String comoTexto = usuarioConHash.toString();

        assertFalse(comoTexto.contains(HASH_EN_BD),
                "Usuario.toString() no debe imprimir el hash de la contraseña");
        assertFalse(comoTexto.contains("contrasena"),
                "Usuario.toString() no debe mencionar el campo contrasena");
    }

    @Test
    @DisplayName("Jackson no serializa el campo contrasena de la entidad Usuario (@JsonIgnore)")
    void jackson_noSerializaContrasena() throws Exception {
        String json = objectMapper.writeValueAsString(usuarioConHash);

        assertFalse(json.contains(HASH_EN_BD),
                "El JSON de Usuario no debe contener el hash de la contraseña");
        assertFalse(json.contains("contrasena"),
                "El JSON de Usuario no debe contener el campo contrasena");
    }

    // ────────────────────── T9.2: DELETE /me ──────────────────────

    /** Usuario con hash real, para que el borrado recorra el flujo completo. */
    private Usuario usuarioConHashReal() {
        Usuario usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Ana");
        usuario.setApellido("Perez");
        usuario.setEmail(EMAIL);
        usuario.setContrasena(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(PASSWORD));
        usuario.setFechaRegistro(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
        return usuario;
    }

    @Test
    @DisplayName("DELETE /me con contraseña correcta → 204 y borra al usuario")
    void eliminarCuenta_exito() throws Exception {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHashReal()));

        mockMvc.perform(delete("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioEliminacionDTO(PASSWORD))))
                .andExpect(status().isNoContent());

        verify(usuarioRepository).delete(any(Usuario.class));
    }

    @Test
    @DisplayName("DELETE /me con contraseña incorrecta → 401 CREDENCIALES_INVALIDAS y no borra")
    void eliminarCuenta_contraseñaIncorrecta() throws Exception {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHashReal()));

        mockMvc.perform(delete("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioEliminacionDTO("Incorrecta123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    @DisplayName("DELETE /me sin contraseña en el cuerpo → 400 VALIDACION_FALLIDA en el campo contrasena")
    void eliminarCuenta_faltaConfirmacion() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasena\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));

        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    @DisplayName("DELETE /me sin token → 401 NO_AUTENTICADO (endpoint protegido)")
    void eliminarCuenta_exigeAutenticacion() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioEliminacionDTO(PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    // ────────────────────── T9.3: PUT /me/contrasena ──────────────────────

    @Test
    @DisplayName("PUT /me/contrasena con contraseña actual correcta → 200, guarda BCrypt y revoca todas las sesiones")
    void cambiarContrasena_exito() throws Exception {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHashReal()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/usuarios/me/contrasena")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CambioContrasenaDTO(PASSWORD, "NuevaPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña actualizada. Inicia sesión con tu nueva contraseña."));

        // La nueva contraseña se guarda con BCrypt (no en claro ni con el hash anterior)
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertNotEquals(HASH_EN_BD, guardado.getValue().getContrasena());
        assertTrue(guardado.getValue().getContrasena().startsWith("$2"));

        // T9.3: el cambio revoca TODAS las sesiones activas (auth_refresh_token)
        verify(authRefreshTokenRepository).revocarActivasDeUsuario(eq(RUT), any());
    }

    @Test
    @DisplayName("PUT /me/contrasena con contraseña actual incorrecta → 401 CREDENCIALES_INVALIDAS y no cambia nada")
    void cambiarContrasena_actualErronea() throws Exception {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHashReal()));

        mockMvc.perform(put("/api/v1/usuarios/me/contrasena")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CambioContrasenaDTO("Incorrecta123", "NuevaPass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(authRefreshTokenRepository, never()).revocarActivasDeUsuario(any(), any());
    }

    @Test
    @DisplayName("PUT /me/contrasena con nueva contraseña débil → 400 VALIDACION_FALLIDA en el campo nuevaContrasena")
    void cambiarContrasena_nuevaDebil() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/me/contrasena")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CambioContrasenaDTO(PASSWORD, "corta1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("nuevaContrasena"));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("PUT /me/contrasena sin token → 401 NO_AUTENTICADO (endpoint protegido)")
    void cambiarContrasena_exigeAutenticacion() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/me/contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CambioContrasenaDTO(PASSWORD, "NuevaPass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // ────────────────────── T9.5: fuerza bruta en /login ──────────────────────
    // La caché del rate limiter es un bean real compartido por todo el contexto
    // de esta clase: cada test usa un email (y una IP) PROPIOS para no
    // contaminar el contador de los demás.

    /** Usuario con hash real bajo el email indicado, para recorrer el flujo completo de login. */
    private Usuario usuarioConEmailYHashReal(String email) {
        Usuario usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Ana");
        usuario.setApellido("Perez");
        usuario.setEmail(email);
        usuario.setContrasena(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(PASSWORD));
        usuario.setFechaRegistro(Date.from(Instant.parse("2026-10-15T12:00:00Z")));
        return usuario;
    }

    private String cuerpoLogin(String email, String contrasena) {
        return "{\"email\":\"" + email + "\",\"contrasena\":\"" + contrasena + "\"}";
    }

    @Test
    @DisplayName("POST /login con 5 fallos → la 6ª intentona (aun con clave correcta) responde 429 sin consultar la BD")
    void login_bloqueadoDespuesDe5Fallos() throws Exception {
        String emailBruto = "bruteforce@example.com";
        when(usuarioRepository.findByEmail(emailBruto)).thenReturn(Optional.of(usuarioConEmailYHashReal(emailBruto)));

        // 5 intentos fallidos → 401 cada uno; el quinto agota la ventaja
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/usuarios/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(emailBruto, "Incorrecta123")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }

        // 6ª intentona con la contraseña CORRECTA: igual 429, el bloqueo va antes de la BD
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailBruto, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("INTENTOS_LOGIN_EXCEDIDOS"))
                .andExpect(jsonPath("$.detail").value(
                        "Demasiados intentos de inicio de sesión fallidos. Espera unos minutos y vuelve a intentarlo."));

        // La BD solo se consultó en los 5 primeros; el bloqueo no tocó credenciales
        verify(usuarioRepository, times(5)).findByEmail(emailBruto);
    }

    @Test
    @DisplayName("POST /login con 5 fallos por CORREO INEXISTENTE → la 6ª intentona responde 429 INTENTOS_LOGIN_EXCEDIDOS")
    void login_correoInexistenteTambienCuenta() throws Exception {
        // Sin stub de findByEmail: el correo no existe en la BD y cada intento
        // es un 401 genérico. El parche del review (T9.5): cada intentona suma,
        // también con correo inexistente, o la enumeración de correos no se frena.
        String emailFantasma = "enumeracion@example.com";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/usuarios/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(emailFantasma, PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }

        // La 6ª petición con ese correo salta el freno ANTES de tocar la BD
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailFantasma, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("INTENTOS_LOGIN_EXCEDIDOS"));

        // El bloqueo no consultó credenciales: la BD solo vio los 5 primeros
        verify(usuarioRepository, times(5)).findByEmail(emailFantasma);
    }

    @Test
    @DisplayName("POST /login: un éxito resetea el contador; los fallos siguientes vuelven a 401 (no 429)")
    void login_exitoReseteaContador() throws Exception {
        String emailReset = "reset@example.com";
        when(usuarioRepository.findByEmail(emailReset)).thenReturn(Optional.of(usuarioConEmailYHashReal(emailReset)));

        // 3 fallos (de los 5 tolerados)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/usuarios/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(emailReset, "Incorrecta123")))
                    .andExpect(status().isUnauthorized());
        }

        // Un acierto limpia el contador
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailReset, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // Tras el reset, un fallo más es 401 normal, no 429
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailReset, "Incorrecta123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("POST /login: el bloqueo es por IP y X-Forwarded-For se resuelve con la primera dirección")
    void login_aislamientoPorIp() throws Exception {
        String emailAislado = "aislado@example.com";
        when(usuarioRepository.findByEmail(emailAislado)).thenReturn(Optional.of(usuarioConEmailYHashReal(emailAislado)));

        // 5 fallos desde la IP 9.9.9.9 → ese par queda bloqueado
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/usuarios/login")
                            .with(request -> {
                                request.setRemoteAddr("9.9.9.9");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(emailAislado, "Incorrecta123")))
                    .andExpect(status().isUnauthorized());
        }

        // La misma IP bloqueada vía XFF con esa IP primero → 429 aun con clave correcta
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .header("X-Forwarded-For", "9.9.9.9, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailAislado, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("INTENTOS_LOGIN_EXCEDIDOS"));

        // Otra IP con el mismo correo sigue con permiso: clave correcta → 200
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .header("X-Forwarded-For", "8.8.8.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailAislado, PASSWORD)))
                .andExpect(status().isOk());

        // XFF "7.7.7.7, 9.9.9.9": la primera dirección (7.7.7.7) manda → no hereda el bloqueo
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .header("X-Forwarded-For", "7.7.7.7, 9.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(emailAislado, PASSWORD)))
                .andExpect(status().isOk());
    }
}
