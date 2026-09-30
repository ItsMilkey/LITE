package com.example.saveup.service;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeudaService {

    @Autowired
    private DeudaRepository deudaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private SecurityUtils securityUtils;

    @Transactional
    public DeudaResponseDTO crearDeuda(DeudaCreacionDTO dto) {
        String rut = securityUtils.getAuthenticatedRut();

        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        Deuda deuda = new Deuda();
        deuda.setUsuario(usuario);
        deuda.setNombre(dto.getNombre());
        deuda.setDescripcion(dto.getDescripcion());
        deuda.setMontoTotal(dto.getMontoTotal());
        deuda.setCantidadCuotas(dto.getCantidadCuotas());

        Deuda deudaGuardada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaGuardada);
    }

    /**
     * Obtiene todas las deudas de un usuario con sus agregaciones calculadas en una única consulta JPQL
     * optimizada, eliminando el problema de N+1 queries.
     */
    @Transactional(readOnly = true)
    public List<DeudaResponseDTO> obtenerDeudasPorUsuario(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        return deudaRepository.findDeudasDTOByUsuarioRut(rut);
    }

    @Transactional
    public DeudaResponseDTO registrarPago(Long deudaId, PagoDeudaDTO pagoDTO) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        if (deuda.getEstado() != EstadoDeuda.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden registrar pagos en deudas pendientes. Estado actual: " + deuda.getEstado());
        }
        Categoria categoriaDeudas = categoriaRepository.findByNombre("Deudas")
                .orElseThrow(() -> new IllegalStateException("La categoría 'Deudas' no fue encontrada. Asegúrate de que exista en la base de datos."));

        // Crear y guardar el movimiento de pago
        Movimiento pago = new Movimiento();
        pago.setUsuario(deuda.getUsuario());
        pago.setDeuda(deuda);
        pago.setMonto(pagoDTO.getMonto().negate()); // Los pagos son egresos, por lo tanto negativos
        pago.setDescripcion(pagoDTO.getDescripcion());
        pago.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        pago.setCategoria(categoriaDeudas);
        movimientoRepository.save(pago);

        // Verificar si la deuda está completamente pagada después del nuevo pago
        java.math.BigDecimal totalPagado = deudaRepository.findTotalPagadoPorDeuda(deuda.getId()).abs();
        if (totalPagado.compareTo(deuda.getMontoTotal()) >= 0) {
            deuda.setEstado(EstadoDeuda.PAGADA);
        }
        Deuda deudaActualizada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaActualizada);
    }

    @Transactional
    public DeudaResponseDTO editarDeuda(Long deudaId, DeudaCreacionDTO dto) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        Integer cuotasPagadas = deudaRepository.countPagosPorDeuda(deudaId);
        if (cuotasPagadas != null && cuotasPagadas > 0) {
            throw new IllegalStateException("No se puede editar una deuda que ya tiene pagos registrados.");
        }

        deuda.setNombre(dto.getNombre());
        deuda.setDescripcion(dto.getDescripcion());
        deuda.setMontoTotal(dto.getMontoTotal());
        deuda.setCantidadCuotas(dto.getCantidadCuotas());
        Deuda deudaActualizada = deudaRepository.save(deuda);

        return convertirADeudaResponseDTO(deudaActualizada);
    }

    @Transactional
    public DeudaResponseDTO cancelarDeuda(Long deudaId) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        if (deuda.getEstado() == EstadoDeuda.PAGADA) {
            throw new IllegalStateException("No se puede cancelar una deuda que ya fue pagada.");
        }

        deuda.setEstado(EstadoDeuda.CANCELADA);
        Deuda deudaCancelada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaCancelada);
    }

    private DeudaResponseDTO convertirADeudaResponseDTO(Deuda deuda) {
        return deudaRepository.findDeudaDTOById(deuda.getId())
                .orElseGet(() -> {
                    DeudaResponseDTO dto = new DeudaResponseDTO();
                    dto.setId(deuda.getId());
                    dto.setNombre(deuda.getNombre());
                    dto.setDescripcion(deuda.getDescripcion());
                    dto.setMontoTotal(deuda.getMontoTotal());
                    dto.setCantidadCuotas(deuda.getCantidadCuotas());
                    dto.setEstado(deuda.getEstado());
                    dto.setFechaCreacion(deuda.getFechaCreacion());
                    dto.setMontoPagado(java.math.BigDecimal.ZERO);
                    dto.setMontoRestante(deuda.getMontoTotal());
                    dto.setCuotasPagadas(0);
                    return dto;
                });
    }
}