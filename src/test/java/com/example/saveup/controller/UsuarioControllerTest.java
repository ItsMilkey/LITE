package com.example.saveup.controller;

import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        mockMvc.perform(post("/api/usuarios/register")
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

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertFalse(body.contains(hashReal),
                            "La respuesta de /login no debe contener el hash de la contraseña");
                });
    }

    @Test
    @DisplayName("GET /me → 200 y la entidad Usuario serializada omite el hash")
    void me_noExponeHash() throws Exception {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHash));

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rut").value(RUT))
                .andExpect(jsonPath("$.email").value(EMAIL))
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
}
