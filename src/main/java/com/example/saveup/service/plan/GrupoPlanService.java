package com.example.saveup.service.plan;

import com.example.saveup.dto.GrupoPlanCreacionDTO;
import com.example.saveup.dto.GrupoPlanDTO;
import com.example.saveup.dto.GrupoPlanEdicionDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.error.ValidacionFallidaException;
import com.example.saveup.model.GrupoPlan;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.GrupoPlanRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CRUD de los grupos de partidas del usuario (T10d, sección 6). El grupo solo
 * ordena y resume; no participa del cálculo de dinero.
 */
@Service
public class GrupoPlanService {

    static final int MAX_GRUPOS = 20;
    static final int MAX_CARACTERES_NOMBRE = 60;
    private static final Pattern CARACTERES_CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final String MENSAJE_DUPLICADO =
            "Ya tienes un grupo con ese nombre.";

    private final GrupoPlanRepository grupoPlanRepository;
    private final PartidaPlanRepository partidaPlanRepository;
    private final UsuarioRepository usuarioRepository;
    private final SecurityUtils securityUtils;

    public GrupoPlanService(GrupoPlanRepository grupoPlanRepository,
                            PartidaPlanRepository partidaPlanRepository,
                            UsuarioRepository usuarioRepository,
                            SecurityUtils securityUtils) {
        this.grupoPlanRepository = grupoPlanRepository;
        this.partidaPlanRepository = partidaPlanRepository;
        this.usuarioRepository = usuarioRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public List<GrupoPlanDTO> listar() {
        String rut = securityUtils.getAuthenticatedRut();
        List<GrupoPlan> grupos = grupoPlanRepository.findByUsuarioRutOrderByOrdenAscIdAsc(rut);
        return aDTOs(grupos);
    }

    @Transactional
    public GrupoPlanDTO crear(GrupoPlanCreacionDTO dto) {
        String rut = securityUtils.getAuthenticatedRut();
        String nombre = validarNombre(dto.getNombre());
        String normalizado = NombrePartida.normalizar(nombre);

        if (grupoPlanRepository.countByUsuarioRut(rut) >= MAX_GRUPOS) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "No puedes tener más de " + MAX_GRUPOS + " grupos.");
        }
        if (grupoPlanRepository.existsByUsuarioRutAndNombreNormalizado(rut, normalizado)) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO, MENSAJE_DUPLICADO);
        }

        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));
        GrupoPlan grupo = new GrupoPlan();
        grupo.setUsuario(usuario);
        grupo.setNombre(nombre);
        grupo.setNombreNormalizado(normalizado);
        grupo.setOrden(dto.getOrden() == null ? 0 : dto.getOrden());
        return aDTO(grupoPlanRepository.save(grupo));
    }

    @Transactional
    public GrupoPlanDTO editar(Long id, GrupoPlanEdicionDTO dto) {
        GrupoPlan grupo = resolver(id);
        if (dto.getNombre() == null && dto.getOrden() == null) {
            throw new ValidacionFallidaException("grupo",
                    "Debes enviar al menos uno de los campos: nombre u orden.");
        }

        if (dto.getNombre() != null) {
            String nombre = validarNombre(dto.getNombre());
            String normalizado = NombrePartida.normalizar(nombre);
            if (grupoPlanRepository.existsByUsuarioRutAndNombreNormalizadoAndIdNot(
                    grupo.getUsuario().getRut(), normalizado, grupo.getId())) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO, MENSAJE_DUPLICADO);
            }
            grupo.setNombre(nombre);
            grupo.setNombreNormalizado(normalizado);
        }
        if (dto.getOrden() != null) {
            grupo.setOrden(dto.getOrden());
        }
        return aDTO(grupoPlanRepository.save(grupo));
    }

    /** Borra el grupo; sus partidas quedan sin grupo (ON DELETE SET NULL). */
    @Transactional
    public void eliminar(Long id) {
        GrupoPlan grupo = resolver(id);
        partidaPlanRepository.desvincularGrupo(grupo.getId());
        grupoPlanRepository.delete(grupo);
    }

    private GrupoPlan resolver(Long id) {
        GrupoPlan grupo = grupoPlanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado con ID: " + id));
        securityUtils.validarPropietario(grupo.getUsuario().getRut());
        return grupo;
    }

    /** Recorta y valida el nombre (1 a 60, sin caracteres de control). */
    static String validarNombre(String nombre) {
        if (nombre == null) {
            throw new ValidacionFallidaException("nombre", "El nombre del grupo es obligatorio.");
        }
        String recortado = nombre.trim();
        if (recortado.isEmpty()) {
            throw new ValidacionFallidaException("nombre", "El nombre del grupo es obligatorio.");
        }
        if (recortado.length() > MAX_CARACTERES_NOMBRE) {
            throw new ValidacionFallidaException("nombre",
                    "El nombre del grupo no puede superar los " + MAX_CARACTERES_NOMBRE + " caracteres.");
        }
        if (CARACTERES_CONTROL.matcher(recortado).find()) {
            throw new ValidacionFallidaException("nombre",
                    "El nombre del grupo no puede contener caracteres de control.");
        }
        return recortado;
    }

    private static GrupoPlanDTO aDTO(GrupoPlan grupo) {
        GrupoPlanDTO dto = new GrupoPlanDTO();
        dto.setId(grupo.getId());
        dto.setNombre(grupo.getNombre());
        dto.setOrden(grupo.getOrden());
        return dto;
    }

    private static List<GrupoPlanDTO> aDTOs(List<GrupoPlan> grupos) {
        List<GrupoPlanDTO> dtos = new ArrayList<>(grupos.size());
        for (GrupoPlan grupo : grupos) {
            dtos.add(aDTO(grupo));
        }
        return dtos;
    }
}
