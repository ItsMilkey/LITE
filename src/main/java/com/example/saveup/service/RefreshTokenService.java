package com.example.saveup.service;

import com.example.saveup.model.AuthRefreshToken;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.AuthRefreshTokenRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.example.saveup.security.TokenOpaco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Sesiones de la app móvil (T9.1): access token corto (JWT) + refresh token
 * rotativo de larga vida.
 * <p>
 * Reglas del ciclo de vida:
 * <ul>
 * <li>El refresh que viaja al cliente es opaco y aleatorio (256 bits); en la
 * base de datos solo se guarda su hash SHA-256.</li>
 * <li><b>Rotación en cada uso</b>: {@link #renovarSesion} consume el refresh
 * actual (marca {@code fechaUso} y {@code fechaRevocacion}) y emite un par
 * nuevo. El access token expira pronto; el cliente rota con el refresh.</li>
 * <li><b>Detección de reuso</b>: si llega un token ya revocado/consumido, se
 * presupone robo y se revocan <em>todas</em> las sesiones activas del
 * usuario.</li>
 * <li><b>Logout</b>: {@link #cerrarSesion} revoca el refresh recibido. Es
 * idempotente: un token desconocido responde bien y no filtra información.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 90 días de vida útil de cada refresh token (T9.1, decisión de diseño). */
    private static final long DURACION_REFRESH_MS = 90L * 24 * 60 * 60 * 1000;

    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public RefreshTokenService(AuthRefreshTokenRepository refreshTokenRepository,
                               UsuarioRepository usuarioRepository,
                               JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
    }

    /**
     * Sesión emitida: access token + refresh token + instante de expiración del
     * access (contrato {@code {token, refreshToken, expiraEn}}).
     */
    public record Sesion(String token, String refreshToken, Date expiracion) {
    }

    /** Crea una sesión nueva (par JWT + refresh) para el usuario autenticado. */
    @Transactional
    public Sesion iniciarSesion(Usuario usuario) {
        JwtService.TokenConExpiracion access =
                jwtService.generarTokenConExpiracion(usuario.getRut(), usuario.getEmail());

        Date ahora = new Date();
        String refreshToken = TokenOpaco.generar();
        AuthRefreshToken registro = new AuthRefreshToken();
        registro.setUsuario(usuario);
        registro.setTokenHash(TokenOpaco.hashSha256Hex(refreshToken));
        registro.setFechaExpiracion(new Date(ahora.getTime() + DURACION_REFRESH_MS));
        refreshTokenRepository.save(registro);

        return new Sesion(access.token(), refreshToken, access.expiracion());
    }

    /**
     * Renueva la sesión: consume el refresh recibido y emite un par nuevo.
     *
     * @throws RefreshTokenInvalidoException si el token no existe, está expirado,
     *                                       ya fue us/revocado o su usuario ya no existe.
     *                                       Si ya fue usado se presume robo
     *                                       de token y se revocan todas las
     *                                       sesiones activas del usuario.
     */
    @Transactional
    public Sesion renovarSesion(String refreshToken) {
        AuthRefreshToken registro = buscarValido(refreshToken);
        Date ahora = new Date();
        Usuario usuario = registro.getUsuario();

        if (registro.estaRevocado()) {
            LOG.warn("Reuso de refresh token revocado para usuario {}: se revocan todas sus sesiones.",
                    registro.getId() != null ? registro.getId() : "id-desconocido");
            refreshTokenRepository.revocarActivasDeUsuario(usuario.getRut(), ahora);
            throw new RefreshTokenInvalidoException();
        }

        registro.setFechaUso(ahora);
        registro.setFechaRevocacion(ahora);
        refreshTokenRepository.save(registro);

        return iniciarSesion(usuario);
    }

    /**
     * Cierra la sesión revocando el refresh token recibido. Idempotente y sin
     * enumeración: un token desconocido no cambia la respuesta.
     */
    public void cerrarSesion(String refreshToken) {
        buscarPorHash(refreshToken).ifPresent(registro -> {
            if (!registro.estaRevocado()) {
                registro.setFechaRevocacion(new Date());
                refreshTokenRepository.save(registro);
            }
        });
    }

    /** Revoca todas las sesiones activas de un usuario (robo de token, reset de contraseña). */
    @Transactional
    public int revocarTodas(String rut) {
        return refreshTokenRepository.revocarActivasDeUsuario(rut, new Date());
    }

    /**
     * Valida que el refresh sea usable y devuelve su registro: hash conocido,
     * no revocado todavía y no expirado. La revocación y el reuso se resuelven
     * en {@link #renovarSesion}, no aquí.
     */
    private AuthRefreshToken buscarValido(String refreshToken) {
        AuthRefreshToken registro = buscarPorHash(refreshToken)
                .orElseThrow(RefreshTokenInvalidoException::new);

        if (registro.estaExpirado(new Date())) {
            throw new RefreshTokenInvalidoException();
        }
        if (!usuarioRepository.existsById(registro.getUsuario().getRut())) {
            throw new RefreshTokenInvalidoException();
        }
        return registro;
    }

    private Optional<AuthRefreshToken> buscarPorHash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(TokenOpaco.hashSha256Hex(refreshToken.trim()));
    }
}
