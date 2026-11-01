package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CategoriaService {

    /** Orden estable del catálogo: NECESIDAD → DESEO → AHORRO → OTROS. */
    private static final List<TipoPresupuesto> ORDEN_TIPOS = List.of(
            TipoPresupuesto.NECESIDAD,
            TipoPresupuesto.DESEO,
            TipoPresupuesto.AHORRO,
            TipoPresupuesto.OTROS);

    private static final Comparator<Categoria> ORDEN_CATALOGO =
            Comparator.comparingInt((Categoria c) -> {
                        int indice = ORDEN_TIPOS.indexOf(c.getTipoPresupuesto());
                        return indice < 0 ? ORDEN_TIPOS.size() : indice;
                    })
                    .thenComparing(Categoria::getNombre, String.CASE_INSENSITIVE_ORDER);

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoriaDTO> obtenerTodas() {
        return categoriaRepository.findAll().stream()
                .sorted(ORDEN_CATALOGO)
                .map(this::convertirADTO)
                .toList();
    }

    private CategoriaDTO convertirADTO(Categoria categoria) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(categoria.getId());
        dto.setCodigo(categoria.getCodigo());
        dto.setNombre(categoria.getNombre());
        dto.setIconId(categoria.getIconId());
        dto.setColorHex(categoria.getColorHex());
        dto.setTipoPresupuesto(categoria.getTipoPresupuesto());
        return dto;
    }
}
