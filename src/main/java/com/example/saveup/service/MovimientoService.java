package com.example.saveup.service;

import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * Registra un nuevo movimiento para un usuario.
     *
     * @param dto Los datos del movimiento a registrar.
     * @return Un DTO con la información del movimiento guardado.
     */
    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRegistroDTO dto) {
        // 1. Validar que el usuario exista.
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioRut())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + dto.getUsuarioRut()));

        // 2. Crear la entidad Movimiento a partir del DTO.
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuario);
        movimiento.setMonto(dto.getMonto());
        movimiento.setDescripcion(dto.getDescripcion());
        movimiento.setTipoMovimiento(dto.getTipoMovimiento());
        // La fecha se establece automáticamente gracias a la anotación @PrePersist en la entidad.

        // Lógica futura para deudas y metas (se añadiría aquí).

        // 3. Guardar la entidad en la base de datos.
        Movimiento movimientoGuardado = movimientoRepository.save(movimiento);

        // 4. Convertir la entidad guardada a un DTO de respuesta y devolverla.
        return convertirAEntidadResponseDTO(movimientoGuardado);
    }

    /**
     * Obtiene el historial de movimientos de un usuario.
     *
     * @param rut El RUT del usuario.
     * @return Una lista de DTOs de movimientos, ordenados por fecha descendente.
     */
    @Transactional(readOnly = true) // readOnly = true optimiza las consultas de solo lectura.
    public List<MovimientoResponseDTO> obtenerMovimientosPorUsuario(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        List<Movimiento> movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut);
        // Convierte la lista de entidades a una lista de DTOs.
        return movimientos.stream()
                .map(this::convertirAEntidadResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calcula y devuelve el saldo actual de un usuario.
     *
     * @param rut El RUT del usuario.
     * @return El saldo total (la suma de todos los montos).
     */
    @Transactional(readOnly = true)
    public Double obtenerSaldoActual(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        Double saldo = movimientoRepository.findSaldoByUsuarioRut(rut);
        // Si un usuario no tiene movimientos, la suma devuelve null. Lo convertimos a 0.0.
        return saldo == null ? 0.0 : saldo;
    }

    // --- Método de utilidad para convertir Entidad -> DTO ---
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