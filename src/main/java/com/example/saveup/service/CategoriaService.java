package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.repository.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.saveup.model.Categoria;
import com.example.saveup.repository.CategoriaRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<CategoriaDTO> obtenerTodas() {
        return categoriaRepository.findAll().stream()
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    private CategoriaDTO convertirADTO(Categoria categoria) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(categoria.getId());
        dto.setNombre(categoria.getNombre());
        dto.setIconId(categoria.getIconId());
        dto.setColorHex(categoria.getColorHex());
        dto.setTipoPresupuesto(categoria.getTipoPresupuesto());
        return dto;
    }
}
