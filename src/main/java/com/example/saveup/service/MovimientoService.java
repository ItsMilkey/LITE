package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
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

    @Autowired
    private DeudaRepository deudaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SmartSplitProcessor smartSplitProcessor;

    @Autowired
    private SecurityUtils securityUtils;

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRegistroDTO dto) {
        // 1. Obtener identidad segura desde el token JWT
        String rut = securityUtils.getAuthenticatedRut();
        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        // 2. Crear la entidad Movimiento a partir del DTO.
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuario);
        movimiento.setMonto(dto.getMonto());
        movimiento.setDescripcion(dto.getDescripcion());
        movimiento.setTipoMovimiento(dto.getTipoMovimiento());
        // La fecha se establece automáticamente gracias a @PrePersist.

        // --- LÓGICA DE ASOCIACIÓN CON DEUDAS ---
        if (dto.getDeudaId() != null) {
            if (dto.getTipoMovimiento() != TipoMovimiento.PAGO_DEUDA) {
                throw new IllegalArgumentException(
                        "El campo 'deudaId' solo es válido para movimientos de tipo PAGO_DEUDA.");
            }
            Deuda deuda = deudaRepository.findById(dto.getDeudaId())
                    .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + dto.getDeudaId()));

            // Validación de propiedad de la deuda
            securityUtils.validarPropietario(deuda.getUsuario().getRut());
            movimiento.setDeuda(deuda);

            java.math.BigDecimal nuevoTotalPagado = deudaRepository.findTotalPagadoPorDeuda(deuda.getId()).abs()
                    .add(dto.getMonto().abs());
            if (nuevoTotalPagado.compareTo(deuda.getMontoTotal()) >= 0) {
                deuda.setEstado(EstadoDeuda.PAGADA);
                deudaRepository.save(deuda);
            }
        }

        // ASOCIACIÓN DE CATEGORÍA
        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Categoría no encontrada con ID: " + dto.getCategoriaId()));
            movimiento.setCategoria(categoria);
        }

        // LÓGICA SMART-SPLIT: Delegada a un servicio de dominio especializado
        if (Boolean.TRUE.equals(dto.getAplicarPresupuesto())
                && dto.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL) {
            smartSplitProcessor.procesarDistribucion(usuario, dto.getMonto());
        }

        // 3. Guardar la entidad en la base de datos.
        Movimiento movimientoGuardado = movimientoRepository.save(movimiento);

        // 4. Convertir la entidad guardada a un DTO de respuesta y devolverla.
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
            movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut, pageable).getContent();
        } else {
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
        java.math.BigDecimal saldo = movimientoRepository.findSaldoByUsuarioRut(rut);
        return saldo == null ? 0.0 : saldo.doubleValue();
    }

    /**
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

        if (movimiento.getCategoria() != null) {
            CategoriaDTO categoriaDTO = new CategoriaDTO();
            categoriaDTO.setId(movimiento.getCategoria().getId());
            categoriaDTO.setNombre(movimiento.getCategoria().getNombre());
            categoriaDTO.setIconId(movimiento.getCategoria().getIconId());
            categoriaDTO.setColorHex(movimiento.getCategoria().getColorHex());
            categoriaDTO.setTipoPresupuesto(movimiento.getCategoria().getTipoPresupuesto());
            dto.setCategoria(categoriaDTO);
        }

        return dto;
    }
}
