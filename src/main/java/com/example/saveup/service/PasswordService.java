package com.example.saveup.service;

import com.example.saveup.model.TokenVerificacion;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoVerificacion;
import com.example.saveup.repository.TokenVerificacionRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.TokenOpaco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Contraseñas de la app móvil (T9.3): recuperación con token de un solo uso y
 * cambio con sesión activa. Ambos caminos terminan revocando TODAS las
 * sesiones activas del usuario ({@link RefreshTokenService#revocarTodas}), de
 * modo que un dispositivo ajeno con la anterior contraseña pierde acceso.
 * <p>
 * Anti-enumeración: {@link #solicitarRecuperacion} responde igual si el
 * correo existe o no, y las reglas del token (válido 1 hora, un solo uso,
 * invalidado por cualquier solicitud posterior) se resuelven aquí.
 */
@Service
public class PasswordService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordService.class);

    /** 1 hora de vida del token de recuperación (T9.3, decisión de diseño). */
    private static final long EXPIRACION_TOKEN_MS = 60L * 60 * 1000;

    private static final String ASUNTO_RECUPERACION = "Recuperación de contraseña — SaveUp Lite";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenVerificacionRepository tokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final CorreoService correoService;

    /** Pantalla pública de restablecimiento de la app; vacío = solo token en el texto. */
    @Value("${app.mail.url-restablecer:}")
    private String urlRestablecer;

    public PasswordService(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           TokenVerificacionRepository tokenRepository,
                           RefreshTokenService refreshTokenService,
                           CorreoService correoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.correoService = correoService;
    }

    /**
     * Solicitud de recuperación: genera un token de un solo uso (hash en BD,
     * valor en claro por correo) válido 1 hora. Si el correo no existe no
     * registra nada: el envelope responde genérico de todas formas.
     */
    @Transactional
    public void solicitarRecuperacion(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email == null ? "" : email.trim());
        if (usuario.isEmpty()) {
            LOG.info("Solicitud de recuperación para un correo sin cuenta registrada.");
            return;
        }

        invalidarTokensPrevios(usuario.get());

        String token = TokenOpaco.generar();
        TokenVerificacion registro = new TokenVerificacion();
        registro.setUsuario(usuario.get());
        registro.setTipo(TipoVerificacion.RESET_PASSWORD);
        registro.setTokenHash(TokenOpaco.hashSha256Hex(token));
        registro.setFechaExpiracion(new Date(System.currentTimeMillis() + EXPIRACION_TOKEN_MS));
        registro.setUsado(false);
        tokenRepository.save(registro);

        correoService.enviar(usuario.get().getEmail(), ASUNTO_RECUPERACION, cuerpoRecuperacion(token));
    }

    /**
     * Restablece la contraseña consumiendo el token. Actualiza con BCrypt y
     * revoca todas las sesiones activas del usuario.
     *
     * @throws TokenVerificacionInvalidoException si el token no existe, ya se
     *                                            usó, fue invalidado o expiró.
     */
    @Transactional
    public void restablecer(String token, String nuevaContrasena) {
        TokenVerificacion registro = tokenRepository
                .findByTokenHash(TokenOpaco.hashSha256Hex(token == null ? "" : token.trim()))
                .orElseThrow(TokenVerificacionInvalidoException::new);

        if (registro.estaUsado()) {
            throw new TokenVerificacionInvalidoException();
        }
        if (registro.estaExpirado(new Date())) {
            throw new TokenVerificacionInvalidoException();
        }

        registro.setUsado(true);
        tokenRepository.save(registro);

        Usuario usuario = registro.getUsuario();
        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);

        refreshTokenService.revocarTodas(usuario.getRut());
    }

    /**
     * Cambio de contraseña con sesión activa: exige la contraseña actual,
     * guarda la nueva con BCrypt y revoca todas las sesiones activas. El JWT
     * de acceso actual sigue vigente sus pocos minutos restantes (no hay lista
     * de revocación para access tokens) y el cliente loguea de nuevo.
     *
     * @throws BadCredentialsException si el RUT no existe o la contraseña
     *                                 actual no coincide (mensaje genérico).
     */
    @Transactional
    public void cambiarContrasena(String rut, String contrasenaActual, String nuevaContrasena) {
        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas."));

        if (!passwordEncoder.matches(contrasenaActual, usuario.getContrasena())) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }

        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);

        refreshTokenService.revocarTodas(rut);
    }

    /** Un solo token vigente a la vez: los anteriores se marcan como usados. */
    private void invalidarTokensPrevios(Usuario usuario) {
        List<TokenVerificacion> previos = tokenRepository
                .findByUsuario_RutAndTipoAndUsadoFalse(usuario.getRut(), TipoVerificacion.RESET_PASSWORD);
        previos.forEach(registro -> registro.setUsado(true));
        tokenRepository.saveAll(previos);
    }

    private String cuerpoRecuperacion(String token) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("Recibimos una solicitud para restablecer tu contraseña en SaveUp Lite.\n")
                .append("Si no fuiste tú, ignora este correo y tu contraseña sigue igual.\n\n")
                .append("Token de un solo uso, válido por 1 hora: ").append(token).append("\n");
        if (urlRestablecer != null && !urlRestablecer.isBlank()) {
            cuerpo.append("\nO abre directamente: ").append(urlRestablecer).append(token).append("\n");
        }
        return cuerpo.toString();
    }
}
