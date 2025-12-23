package com.example.saveup.service;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.saveup.model.Categoria;
import com.example.saveup.repository.CategoriaRepository;

import java.util.List;
import java.util.stream.Collectors;

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

    @Transactional
    public DeudaResponseDTO crearDeuda(DeudaCreacionDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioRut())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + dto.getUsuarioRut()));

        Deuda deuda = new Deuda();
        deuda.setUsuario(usuario);
        deuda.setNombre(dto.getNombre());
        deuda.setDescripcion(dto.getDescripcion());
        deuda.setMontoTotal(dto.getMontoTotal());
        deuda.setCantidadCuotas(dto.getCantidadCuotas());
        // El estado y la fecha se asignan automáticamente por @PrePersist

        Deuda deudaGuardada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaGuardada);
    }

    @Transactional(readOnly = true)
    public List<DeudaResponseDTO> obtenerDeudasPorUsuario(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        List<Deuda> deudas = deudaRepository.findByUsuarioRut(rut);
        return deudas.stream()
                .map(this::convertirADeudaResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public DeudaResponseDTO registrarPago(Long deudaId, PagoDeudaDTO pagoDTO) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        if (deuda.getEstado() != EstadoDeuda.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden registrar pagos en deudas pendientes. Estado actual: " + deuda.getEstado());
        }
        Categoria categoriaDeudas = categoriaRepository.findByNombre("Deudas")
                .orElseThrow(() -> new IllegalStateException("La categoría 'Deudas' no fue encontrada. Asegúrate de que exista en la base de datos."));

        // Crear y guardar el movimiento de pago
        Movimiento pago = new Movimiento();
        pago.setUsuario(deuda.getUsuario());
        pago.setDeuda(deuda);
        pago.setMonto(pagoDTO.getMonto() * -1); // Los pagos son egresos, por lo tanto negativos
        pago.setDescripcion(pagoDTO.getDescripcion());
        pago.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        pago.setCategoria(categoriaDeudas);
        movimientoRepository.save(pago);

        // Verificar si la deuda está completamente pagada después del nuevo pago
        // El cálculo de totalPagado ahora es más preciso
        double totalPagado = Math.abs(movimientoRepository.findByDeuda(deuda).stream().mapToDouble(Movimiento::getMonto).sum());
        if (totalPagado >= deuda.getMontoTotal()) {
            deuda.setEstado(EstadoDeuda.PAGADA);
        }
        Deuda deudaActualizada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaActualizada);
    }
    
    @Transactional
    public DeudaResponseDTO editarDeuda(Long deudaId, DeudaCreacionDTO dto) {
        Deuda deuda = deudaRepository.findById(deudaId)
            .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        Integer cuotasPagadas = deudaRepository.countPagosPorDeuda(deudaId);
        if (cuotasPagadas > 0) {
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
        
        if (deuda.getEstado() == EstadoDeuda.PAGADA) {
             throw new IllegalStateException("No se puede cancelar una deuda que ya fue pagada.");
        }

        deuda.setEstado(EstadoDeuda.CANCELADA);
        Deuda deudaCancelada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaCancelada);
    }


    // Método de utilidad para convertir y calcular los campos dinámicos
    private DeudaResponseDTO convertirADeudaResponseDTO(Deuda deuda) {
        DeudaResponseDTO dto = new DeudaResponseDTO();
        dto.setId(deuda.getId());
        dto.setNombre(deuda.getNombre());
        dto.setDescripcion(deuda.getDescripcion());
        dto.setMontoTotal(deuda.getMontoTotal());
        dto.setCantidadCuotas(deuda.getCantidadCuotas());
        dto.setEstado(deuda.getEstado());
        dto.setFechaCreacion(deuda.getFechaCreacion());

        // --- La Magia de los Cálculos en Tiempo Real ---
        double montoPagadoAbsoluto = Math.abs(deudaRepository.findTotalPagadoPorDeuda(deuda.getId()));
        dto.setMontoPagado(montoPagadoAbsoluto);
        dto.setMontoRestante(deuda.getMontoTotal() - montoPagadoAbsoluto);
        dto.setCuotasPagadas(deudaRepository.countPagosPorDeuda(deuda.getId()));

        return dto;
    }
}