package com.example.saveup.service;

import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MovimientoService {

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRegistroDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioRut())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + dto.getUsuarioRut()));

        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuario);
        movimiento.setMonto(dto.getMonto());
        movimiento.setDescripcion(dto.getDescripcion());
        movimiento.setTipoMovimiento(dto.getTipoMovimiento());

        Movimiento movimientoGuardado = movimientoRepository.save(movimiento);
        return convertirAEntidadResponseDTO(movimientoGuardado);
    }

    /**
     * Obtiene el historial de movimientos de un usuario.
     * Si se proporciona un límite, devuelve solo esa cantidad de movimientos recientes.
     * Si no, devuelve el historial completo.
     */
    @Transactional(readOnly = true)
    public List<MovimientoResponseDTO> obtenerMovimientosPorUsuario(String rut, Integer limit) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }

        List<Movimiento> movimientos;

        if (limit != null && limit > 0) {
            Pageable pageable = PageRequest.of(0, limit);
            // ¡CORRECCIÓN! Añadimos .getContent() para extraer la lista del objeto Page.
            movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut, pageable).getContent();
        } else {
            // Si no hay límite, usamos el método original que devuelve una Lista.
            movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut);
        }

        return movimientos.stream()
                .map(this::convertirAEntidadResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Double obtenerSaldoActual(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        Double saldo = movimientoRepository.findSaldoByUsuarioRut(rut);
        return saldo == null ? 0.0 : saldo;
    }

    /**
     * ¡NUEVO MÉTODO!
     * Obtiene el historial de movimientos de forma paginada.
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<MovimientoResponseDTO> obtenerMovimientosPaginados(String rut, Pageable pageable) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        
        // 1. Obtenemos la página de entidades desde el repositorio
        Page<Movimiento> paginaMovimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut, pageable);
        
        // 2. Convertimos el contenido de la página a una lista de DTOs
        List<MovimientoResponseDTO> contenidoDTO = paginaMovimientos.getContent().stream()
                .map(this::convertirAEntidadResponseDTO)
                .collect(Collectors.toList());

        // 3. Creamos y devolvemos nuestro DTO de respuesta de página
        PageResponseDTO<MovimientoResponseDTO> respuesta = new PageResponseDTO<>();
        respuesta.setContent(contenidoDTO);
        respuesta.setCurrentPage(paginaMovimientos.getNumber());
        respuesta.setTotalItems(paginaMovimientos.getTotalElements());
        respuesta.setTotalPages(paginaMovimientos.getTotalPages());
        
        return respuesta;
    }
    
    private MovimientoResponseDTO convertirAEntidadResponseDTO(Movimiento movimiento) {
        MovimientoResponseDTO dto = new MovimientoResponseDTO();
        dto.setId(movimiento.getId());
        dto.setMonto(movimiento.getMonto());
        dto.setDescripcion(movimiento.getDescripcion());
        dto.setFecha(movimiento.getFecha());
        dto.setTipoMovimiento(movimiento.getTipoMovimiento());
        return dto;
    }
}
