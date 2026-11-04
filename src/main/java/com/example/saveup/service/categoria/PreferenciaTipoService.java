package com.example.saveup.service.categoria;

import com.example.saveup.dto.PreferenciaCategoriaDTO;
import com.example.saveup.error.DatosInvalidosException;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.PreferenciaCategoriaUsuario;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.PreferenciaCategoriaUsuarioRepository;
import com.example.saveup.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tipos efectivos de las categorías del usuario (T10d, sección 3). Es el único
 * punto que resuelve el tipo efectivo: la preferencia del usuario si existe
 * y, si no, el tipo por defecto del catálogo. Lo consumen la ejecución general
 * de T5, el plan y la sugerencia de regla.
 *
 * <p>Las preferencias se cargan con una sola consulta por solicitud
 * ({@code Map<Long, TipoPresupuesto> categoriaId → tipo}) y se resuelven en
 * memoria; así ni el historial de T5 ni el plan caen en N+1.
 */
@Service
public class PreferenciaTipoService {

    /** Únicos tipos a los que se puede reclasificar una categoría (T10d, sección 3). */
    private static final List<TipoPresupuesto> TIPOS_RECLASIFICABLES = List.of(
            TipoPresupuesto.NECESIDAD, TipoPresupuesto.DESEO);

    private final PreferenciaCategoriaUsuarioRepository preferenciaRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public PreferenciaTipoService(PreferenciaCategoriaUsuarioRepository preferenciaRepository,
                                  CategoriaRepository categoriaRepository,
                                  UsuarioRepository usuarioRepository) {
        this.preferenciaRepository = preferenciaRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Preferencias del usuario con una única consulta. El mapa resultante va
     * de {@code categoriaId} al tipo preferido y solo contiene categorías con
     * preferencia; el resto resuelven a su tipo por defecto.
     */
    @Transactional(readOnly = true)
    public Map<Long, TipoPresupuesto> cargarPreferencias(String rut) {
        Map<Long, TipoPresupuesto> preferencias = new LinkedHashMap<>();
        for (PreferenciaCategoriaUsuario pref : preferenciaRepository.buscarDelUsuario(rut)) {
            preferencias.put(pref.getCategoria().getId(), pref.getTipoPresupuesto());
        }
        return preferencias;
    }

    /**
     * Tipo efectivo de una categoría dado el mapa de preferencias: la
     * preferencia si existe; si no, el tipo por defecto de la categoría.
     * Método estático y puro para que ningún servicio lo copie.
     */
    public static TipoPresupuesto tipoEfectivo(Map<Long, TipoPresupuesto> preferencias,
                                               Categoria categoria) {
        if (categoria == null) {
            return null;
        }
        if (preferencias != null) {
            TipoPresupuesto preferido = preferencias.get(categoria.getId());
            if (preferido != null) {
                return preferido;
            }
        }
        return categoria.getTipoPresupuesto();
    }

    /** Catálogo completo con tipoPorDefecto, tipoPreferido y tipoEfectivo (D01). */
    @Transactional(readOnly = true)
    public List<PreferenciaCategoriaDTO> listar(String rut) {
        Map<Long, TipoPresupuesto> preferencias = cargarPreferencias(rut);
        return categoriaRepository.findAll().stream()
                .sorted(OrdenCatalogo.CATALOGO)
                .map(categoria -> aDTO(categoria, preferencias.get(categoria.getId())))
                .toList();
    }

    /**
     * Reclasifica una categoría. La categoría debe existir (404), el tipo
     * pedido y la categoría deben ser reclasificables (400 DATOS_INVALIDOS:
     * D03/D04) y el valor AHORRO u OTROS también se rechaza (D04).
     */
    @Transactional
    public PreferenciaCategoriaDTO actualizar(String rut, Long categoriaId, TipoPresupuesto tipo) {
        Categoria categoria = buscarCategoria(categoriaId);
        validarTipo(tipo);
        validarCategoriaReclasificable(categoria);

        PreferenciaCategoriaUsuario preferencia =
                preferenciaRepository.findByUsuarioRutAndCategoriaId(rut, categoriaId)
                        .orElseGet(() -> {
                            PreferenciaCategoriaUsuario nueva = new PreferenciaCategoriaUsuario();
                            nueva.setUsuario(buscarUsuario(rut));
                            nueva.setCategoria(categoria);
                            return nueva;
                        });
        preferencia.setTipoPresupuesto(tipo);
        preferenciaRepository.save(preferencia);
        return aDTO(categoria, tipo);
    }

    /**
     * Vuelve al tipo por defecto quitando la preferencia. Es idempotente y
     * responde 204 tengas o no una preferencia previa (D06), pero la categoría
     * debe existir (404, D10).
     */
    @Transactional
    public void eliminar(String rut, Long categoriaId) {
        buscarCategoria(categoriaId);
        preferenciaRepository.findByUsuarioRutAndCategoriaId(rut, categoriaId)
                .ifPresent(preferenciaRepository::delete);
    }

    // ─────────────────────────── validaciones ───────────────────────────

    private void validarTipo(TipoPresupuesto tipo) {
        // Un valor desconocido no llega aquí: Jackson lo rechaza con SOLICITUD_INVALIDA (D05).
        if (tipo == TipoPresupuesto.AHORRO || tipo == TipoPresupuesto.OTROS) {
            throw new DatosInvalidosException(
                    "El tipo preferido solo puede ser NECESIDAD o DESEO.");
        }
        if (!TIPOS_RECLASIFICABLES.contains(tipo)) {
            throw new DatosInvalidosException(
                    "El tipo preferido solo puede ser NECESIDAD o DESEO.");
        }
    }

    private void validarCategoriaReclasificable(Categoria categoria) {
        if (!TIPOS_RECLASIFICABLES.contains(categoria.getTipoPresupuesto())) {
            throw new DatosInvalidosException(
                    "La categoría " + categoria.getNombre()
                            + " no se puede reclasificar; su tipo por defecto no es NECESIDAD ni DESEO.");
        }
    }

    // ────────────────────────────── helpers ──────────────────────────────

    private Categoria buscarCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Categoría no encontrada con ID: " + categoriaId));
    }

    private Usuario buscarUsuario(String rut) {
        return usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado con RUT: " + rut));
    }

    private static PreferenciaCategoriaDTO aDTO(Categoria categoria, TipoPresupuesto tipoPreferido) {
        PreferenciaCategoriaDTO dto = new PreferenciaCategoriaDTO();
        dto.setId(categoria.getId());
        dto.setNombre(categoria.getNombre());
        dto.setCodigo(categoria.getCodigo());
        dto.setIconId(categoria.getIconId());
        dto.setColorHex(categoria.getColorHex());
        dto.setTipoPorDefecto(categoria.getTipoPresupuesto());
        dto.setTipoPreferido(tipoPreferido);
        dto.setTipoEfectivo(tipoEfectivo(
                tipoPreferido == null ? Map.of() : Map.of(categoria.getId(), tipoPreferido),
                categoria));
        return dto;
    }
}
