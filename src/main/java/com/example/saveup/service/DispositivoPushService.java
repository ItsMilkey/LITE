package com.example.saveup.service;

import com.example.saveup.dto.DispositivoPushRequestDTO;
import com.example.saveup.dto.DispositivoPushResponseDTO;
import com.example.saveup.model.DispositivoPush;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.DispositivoPushRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.service.push.TokensPush;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Alta, baja y listado de dispositivos push del usuario (T9.6). La identidad
 * llega siempre como RUT del token; nunca se acepta un usuario en el cuerpo.
 */
@Service
public class DispositivoPushService {

    private final DispositivoPushRepository dispositivoRepository;

    private final UsuarioRepository usuarioRepository;

    public DispositivoPushService(DispositivoPushRepository dispositivoRepository,
                                  UsuarioRepository usuarioRepository) {
        this.dispositivoRepository = dispositivoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Alta idempotente. Si el token ya existe se reasigna al usuario actual y se
     * reactiva: un teléfono puede haber cambiado de cuenta.
     */
    @Transactional
    public Registro registrar(String rut, DispositivoPushRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        Date ahora = new Date();
        DispositivoPush dispositivo = dispositivoRepository.findByToken(dto.token())
                .orElseGet(() -> {
                    DispositivoPush nuevo = new DispositivoPush();
                    nuevo.setToken(dto.token());
                    nuevo.setFechaRegistro(ahora);
                    return nuevo;
                });

        boolean creado = dispositivo.getId() == null;
        dispositivo.setUsuario(usuario);
        dispositivo.setPlataforma(dto.plataforma());
        dispositivo.setActivo(true);
        dispositivo.setFechaUltimoUso(ahora);

        return new Registro(aResponse(dispositivoRepository.save(dispositivo)), creado);
    }

    /**
     * Baja idempotente: un token desconocido no es un error. Si el token es de
     * otro usuario se ignora en silencio, para no revelar su existencia.
     */
    @Transactional
    public void baja(String rut, String token) {
        dispositivoRepository.findByToken(token)
                .filter(dispositivo -> dispositivo.getUsuario().getRut().equals(rut))
                .ifPresent(dispositivoRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<DispositivoPushResponseDTO> listar(String rut) {
        return dispositivoRepository.findByUsuarioRutAndActivoTrueOrderByFechaUltimoUsoDesc(rut)
                .stream()
                .map(DispositivoPushService::aResponse)
                .toList();
    }

    private static DispositivoPushResponseDTO aResponse(DispositivoPush dispositivo) {
        return new DispositivoPushResponseDTO(
                dispositivo.getId(),
                dispositivo.getPlataforma(),
                dispositivo.estaActivo(),
                dispositivo.getFechaRegistro(),
                TokensPush.enmascarar(dispositivo.getToken()));
    }

    /** Resultado del alta: el DTO y si el dispositivo se creó en esta llamada. */
    public record Registro(DispositivoPushResponseDTO dispositivo, boolean creado) {
    }
}
