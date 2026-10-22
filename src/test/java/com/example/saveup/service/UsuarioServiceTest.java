package com.example.saveup.service;

import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contrato de {@link UsuarioService#eliminarCuenta} (T9.2): exige contraseña
 * actual, delega el borrado en cascada a {@link EliminacionDeCuentaService}
 * antes de eliminar el usuario y ofrece una excepción genérica cuando las
 * credenciales no coinciden.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String RUT = "12345678-5";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Password123";
    private static final String HASH = "$2a$10$hash-de-prueba-para-eliminar";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MetaAhorroRepository metaAhorroRepository;

    @Mock
    private EliminacionDeCuentaService eliminacionDeCuentaService;

    @Mock
    private com.example.saveup.security.LoginRateLimiterService loginRateLimiterService;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder,
                new com.example.saveup.security.VerificadorDeContrasena(passwordEncoder),
                metaAhorroRepository, eliminacionDeCuentaService, loginRateLimiterService);
    }

    private Usuario usuarioConHash() {
        Usuario usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Ana");
        usuario.setApellido("Perez");
        usuario.setEmail(EMAIL);
        usuario.setContrasena(HASH);
        return usuario;
    }

    @Test
    @DisplayName("eliminarCuenta con contraseña correcta → borra en cascada y luego elimina al usuario")
    void eliminarCuenta_borraEnCascadaYUsuario() {
        Usuario usuario = usuarioConHash();
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertDoesNotThrow(() -> usuarioService.eliminarCuenta(RUT, PASSWORD));

        InOrder orden = inOrder(eliminacionDeCuentaService, usuarioRepository);
        orden.verify(eliminacionDeCuentaService).eliminarTodo(usuario);
        orden.verify(usuarioRepository).delete(usuario);
    }

    @Test
    @DisplayName("eliminarCuenta con contraseña incorrecta → BadCredentialsException y no borra nada")
    void eliminarCuenta_contraseñaIncorrecta_noBorra() {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.of(usuarioConHash()));
        when(passwordEncoder.matches("Otra12345", HASH)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> usuarioService.eliminarCuenta(RUT, "Otra12345"));

        verifyNoInteractions(eliminacionDeCuentaService);
        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    @DisplayName("eliminarCuenta con RUT inexistente → BadCredentialsException genérica (sin pistas)")
    void eliminarCuenta_rutInexistente() {
        when(usuarioRepository.findById(RUT)).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> usuarioService.eliminarCuenta(RUT, PASSWORD));

        verifyNoInteractions(eliminacionDeCuentaService, passwordEncoder);
        verify(usuarioRepository, never()).delete(any(Usuario.class));
        assertEquals("Credenciales inválidas.", ex.getMessage());
    }

    @Test
    @DisplayName("Tras el borrado, el mismo RUT y correo pueden volver a registrarse (re-registro)")
    void reRegistro_trasBorrado_funciona() {
        UsuarioRegistroDTO dto = new UsuarioRegistroDTO();
        dto.setRut(RUT);
        dto.setNombre("Ana");
        dto.setApellido("Perez");
        dto.setEmail(EMAIL);
        dto.setContrasena(PASSWORD);

        // La cuenta fue borrada: ni el RUT ni el email están ocupados.
        when(usuarioRepository.existsById(RUT)).thenReturn(false);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(metaAhorroRepository.save(any(MetaAhorro.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> usuarioService.registrarUsuario(dto));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertEquals(RUT, guardado.getValue().getRut());
        verify(metaAhorroRepository).save(any(MetaAhorro.class)); // meta "Ahorros" por defecto
    }
}
