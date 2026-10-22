package com.example.saveup.service;

import com.example.saveup.dto.UsuarioLoginDTO;
import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.dto.UsuarioResponseDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.LoginRateLimiterService;
import com.example.saveup.security.VerificadorDeContrasena;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificadorDeContrasena verificadorDeContrasena;
    private final MetaAhorroRepository metaAhorroRepository;
    private final EliminacionDeCuentaService eliminacionDeCuentaService;
    private final LoginRateLimiterService rateLimiter;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          VerificadorDeContrasena verificadorDeContrasena,
                          MetaAhorroRepository metaAhorroRepository,
                          EliminacionDeCuentaService eliminacionDeCuentaService,
                          LoginRateLimiterService rateLimiter) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificadorDeContrasena = verificadorDeContrasena;
        this.metaAhorroRepository = metaAhorroRepository;
        this.eliminacionDeCuentaService = eliminacionDeCuentaService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Registra un nuevo usuario en el sistema.
     * Valida la existencia, codifica la contraseña y guarda el nuevo usuario.
     *
     * @param usuarioDTO Datos del usuario provenientes del frontend.
     * @throws ErrorNegocio si el RUT o el email ya están en uso.
     */

    @Transactional
    public void registrarUsuario(UsuarioRegistroDTO usuarioDTO) {
        if (usuarioRepository.existsById(usuarioDTO.getRut())
                || usuarioRepository.existsByEmail(usuarioDTO.getEmail())) {
            throw new ErrorNegocio(CodigoError.USUARIO_YA_REGISTRADO);
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setRut(usuarioDTO.getRut());
        nuevoUsuario.setNombre(usuarioDTO.getNombre());
        nuevoUsuario.setApellido(usuarioDTO.getApellido());
        nuevoUsuario.setEmail(usuarioDTO.getEmail());
        nuevoUsuario.setContrasena(passwordEncoder.encode(usuarioDTO.getContrasena()));

        // 1. Guardar el usuario
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // 2. Crear la meta de ahorro por defecto usando el usuario ya guardado
        MetaAhorro metaPorDefecto = new MetaAhorro();
        metaPorDefecto.setNombre("Ahorros");
        metaPorDefecto.setUsuario(usuarioGuardado);
        metaAhorroRepository.save(metaPorDefecto);
    }

    /**
     * Autentica a un usuario comparando las credenciales con la base de datos,
     * bajo vigilancia del freno de fuerza bruta (T9.5): el bloqueo se consulta
     * ANTES de tocar la BD (429), cada 401 suma un intento fallido para esa
     * combinación email+IP y un login exitoso limpia el contador.
     *
     * @param loginDTO  Objeto con el email y la contraseña.
     * @param ipCliente IP resuelta del cliente (LoginRateLimiterService.resolverIp).
     * @return El objeto Usuario si las credenciales son correctas.
     * @throws BloqueoPorFuerzaBrutaException si el par email+IP ya está bloqueado.
     * @throws BadCredentialsException        si el usuario no existe o la contraseña es incorrecta.
     */
    public Usuario autenticarUsuario(UsuarioLoginDTO loginDTO, String ipCliente) {
        // Todo el ciclo del contador usa la misma clave email+IP (record AccesoLogin).
        LoginRateLimiterService.AccesoLogin acceso =
                new LoginRateLimiterService.AccesoLogin(loginDTO.getEmail(), ipCliente);
        rateLimiter.exigirPermitido(acceso);

        // Busca al usuario por email. Si no lo encuentra, lanza excepción.
        // Un 401 por correo inexistente TAMBIÉN suma intento (T9.5, parche del
        // review): si no contara, la enumeración de correos quedaría sin freno.
        Usuario usuario = usuarioRepository.findByEmail(loginDTO.getEmail())
                .orElseGet(() -> {
                    rateLimiter.registrarFallo(acceso);
                    throw new BadCredentialsException("Credenciales inválidas.");
                });

        // Compara la contraseña del DTO con la contraseña hasheada de la BD.
        if (passwordEncoder.matches(loginDTO.getContrasena(), usuario.getContrasena())) {
            rateLimiter.resetear(acceso);
            return usuario; // Si coinciden, devuelve el usuario y limpia el contador.
        }
        // Si no coinciden, la misma excepción para no dar pistas a atacantes (T9.5).
        rateLimiter.registrarFallo(acceso);
        throw new BadCredentialsException("Credenciales inválidas.");
    }

    public UsuarioResponseDTO obtenerPerfil(String rut) {
        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado con RUT: " + rut));
        return new UsuarioResponseDTO(usuario.getRut(), usuario.getNombre(), usuario.getApellido(),
                usuario.getEmail(), usuario.getFechaRegistro());
    }

    /**
     * Elimina de forma permanente la cuenta del usuario autenticado (T9.2,
     * requisito de Apple y Google para publicar). En una misma transacción:
     * valida la contraseña actual, borra los datos en cascada (metas, deudas,
     * movimientos, presupuestos y sesiones) y elimina el usuario.
     *
     * @throws BadCredentialsException si el RUT no existe en la BD o la
     *                                 contraseña no coincide (mismo mensaje para
     *                                 no filtrar cuál de las dos falló).
     */
    @Transactional
    public void eliminarCuenta(String rut, String contrasena) {
        Usuario usuario = usuarioRepository.findById(rut).orElse(null);

        // Un solo dueño para la comparación de contraseñas críticas; el mensaje
        // genérico no aclara si falló el RUT o la contraseña.
        verificadorDeContrasena.verificarContrasena(usuario, contrasena);

        // Primero los hijos en orden seguro; el usuario al final. Desde V10 las
        // FKs hacia usuario también tienen ON DELETE CASCADE como respaldo.
        eliminacionDeCuentaService.eliminarTodo(usuario);
        usuarioRepository.delete(usuario);
    }
}
