package com.example.saveup.service;

import com.example.saveup.dto.AbonoRetiroDTO;
import com.example.saveup.dto.MetaAhorroCreacionDTO;
import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.movimiento.SignoMovimiento;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MetaAhorroService {

    private final MetaAhorroRepository metaAhorroRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final com.example.saveup.repository.CategoriaRepository categoriaRepository;
    private final SecurityUtils securityUtils;

    public MetaAhorroService(MetaAhorroRepository metaAhorroRepository, UsuarioRepository usuarioRepository,
                             MovimientoRepository movimientoRepository,
                             com.example.saveup.repository.CategoriaRepository categoriaRepository,
                             SecurityUtils securityUtils) {
        this.metaAhorroRepository = metaAhorroRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.categoriaRepository = categoriaRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public MetaAhorroResponseDTO crearMeta(MetaAhorroCreacionDTO dto) {
        String rut = securityUtils.getAuthenticatedRut();

        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        MetaAhorro meta = new MetaAhorro();
        meta.setUsuario(usuario);
        meta.setNombre(dto.getNombre());
        meta.setMontoObjetivo(dto.getMontoObjetivo());
        meta.setFechaLimite(dto.getFechaLimite());

        MetaAhorro metaGuardada = metaAhorroRepository.save(meta);
        return convertirADTO(metaGuardada);
    }

    @Transactional(readOnly = true)
    public List<MetaAhorroResponseDTO> obtenerMetasPorUsuario(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        return metaAhorroRepository.findByUsuarioRut(rut).stream()
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public MetaAhorroResponseDTO realizarAbono(Long metaId, AbonoRetiroDTO dto) {
        MetaAhorro meta = metaAhorroRepository.findById(metaId)
                .orElseThrow(() -> new EntityNotFoundException("Meta no encontrada con ID: " + metaId));

        securityUtils.validarPropietario(meta.getUsuario().getRut());

        // El signo lo impone el tipo: el movimiento queda negativo y el saldo de la
        // meta sube exactamente ese monto canónico (invariante T1d).
        BigDecimal montoMovimiento = SignoMovimiento.aplicar(TipoMovimiento.ABONO_META, dto.getMonto());

        Movimiento abono = new Movimiento();
        abono.setUsuario(meta.getUsuario());
        abono.setMetaAhorro(meta);
        abono.setTipoMovimiento(TipoMovimiento.ABONO_META);
        abono.setMonto(montoMovimiento); // El dinero "sale" del saldo principal
        abono.setDescripcion(dto.getDescripcion());

        // ASIGNAR CATEGORÍA AHORRO
        categoriaRepository.findByNombre("Ahorro").ifPresent(abono::setCategoria);

        movimientoRepository.save(abono);

        // Update Meta Amount
        meta.setMontoActual(meta.getMontoActual().add(montoMovimiento.abs()));
        metaAhorroRepository.save(meta);

        return convertirADTO(meta);
    }

    @Transactional
    public MetaAhorroResponseDTO realizarRetiro(Long metaId, AbonoRetiroDTO dto) {
        MetaAhorro meta = metaAhorroRepository.findById(metaId)
                .orElseThrow(() -> new EntityNotFoundException("Meta no encontrada con ID: " + metaId));

        securityUtils.validarPropietario(meta.getUsuario().getRut());

        // El signo lo impone el tipo. El control de "total ahorrado" se hace sobre el
        // monto canónico en valor absoluto, no sobre lo que envió el cliente.
        BigDecimal montoMovimiento = SignoMovimiento.aplicar(TipoMovimiento.RETIRO_META, dto.getMonto());
        BigDecimal montoRetiro = montoMovimiento.abs();

        java.math.BigDecimal totalAhorradoRaw = metaAhorroRepository.findTotalAhorradoByMetaId(metaId);
        java.math.BigDecimal totalAhorrado = (totalAhorradoRaw != null ? totalAhorradoRaw : java.math.BigDecimal.ZERO).abs();
        if (montoRetiro.compareTo(totalAhorrado) > 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "El monto a retirar no puede ser mayor al total ahorrado.");
        }

        Movimiento retiro = new Movimiento();
        retiro.setUsuario(meta.getUsuario());
        retiro.setMetaAhorro(meta);
        retiro.setTipoMovimiento(TipoMovimiento.RETIRO_META);
        retiro.setMonto(montoMovimiento); // El dinero "vuelve" al saldo principal
        retiro.setDescripcion(dto.getDescripcion());

        // ASIGNAR CATEGORÍA AHORRO
        categoriaRepository.findByNombre("Ahorro").ifPresent(retiro::setCategoria);

        movimientoRepository.save(retiro);

        // Update Meta Amount
        meta.setMontoActual(meta.getMontoActual().subtract(montoRetiro));
        metaAhorroRepository.save(meta);

        return convertirADTO(meta);
    }

    @Transactional
    public void eliminarMeta(Long metaId) {
        MetaAhorro meta = metaAhorroRepository.findById(metaId)
                .orElseThrow(() -> new EntityNotFoundException("Meta no encontrada con ID: " + metaId));

        securityUtils.validarPropietario(meta.getUsuario().getRut());

        // No se puede eliminar la meta por defecto "Ahorros"
        if ("Ahorros".equalsIgnoreCase(meta.getNombre())) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "La meta por defecto 'Ahorros' no se puede eliminar.");
        }

        java.math.BigDecimal totalAhorradoRaw = metaAhorroRepository.findTotalAhorradoByMetaId(metaId);
        java.math.BigDecimal totalAhorrado = (totalAhorradoRaw != null ? totalAhorradoRaw : java.math.BigDecimal.ZERO).abs();

        if (totalAhorrado.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Devolver el dinero al saldo principal
            Movimiento devolucion = new Movimiento();
            devolucion.setUsuario(meta.getUsuario());
            devolucion.setTipoMovimiento(TipoMovimiento.RETIRO_META);
            devolucion.setMonto(SignoMovimiento.aplicar(TipoMovimiento.RETIRO_META, totalAhorrado));
            devolucion.setDescripcion("Devolución de fondos por eliminar meta: " + meta.getNombre());
            movimientoRepository.save(devolucion);
        }

        // Desvincular movimientos de la meta antes de borrarla (optimizado sin findAll)
        List<Movimiento> movimientosAsociados = movimientoRepository.findByMetaAhorroId(metaId);
        movimientosAsociados.forEach(m -> m.setMetaAhorro(null));
        movimientoRepository.saveAll(movimientosAsociados);

        metaAhorroRepository.delete(meta);
    }

    @Transactional
    public MetaAhorroResponseDTO editarMeta(Long metaId, MetaAhorroCreacionDTO dto) {
        MetaAhorro meta = metaAhorroRepository.findById(metaId)
                .orElseThrow(() -> new EntityNotFoundException("Meta no encontrada con ID: " + metaId));

        securityUtils.validarPropietario(meta.getUsuario().getRut());

        // Regla: La meta por defecto "Ahorros" no puede tener objetivo ni fecha.
        boolean isDefaultMeta = meta.getMontoObjetivo() == null && meta.getFechaLimite() == null;

        if (isDefaultMeta) {
            meta.setNombre(dto.getNombre()); // Solo permite cambiar el nombre
        } else {
            meta.setNombre(dto.getNombre());
            meta.setMontoObjetivo(dto.getMontoObjetivo());
            meta.setFechaLimite(dto.getFechaLimite());
        }

        MetaAhorro metaActualizada = metaAhorroRepository.save(meta);
        return convertirADTO(metaActualizada);
    }

    private MetaAhorroResponseDTO convertirADTO(MetaAhorro meta) {
        MetaAhorroResponseDTO dto = new MetaAhorroResponseDTO();
        dto.setId(meta.getId());
        dto.setNombre(meta.getNombre());
        dto.setMontoObjetivo(meta.getMontoObjetivo());
        dto.setFechaLimite(meta.getFechaLimite());
        dto.setMontoActual(meta.getMontoActual());
        return dto;
    }
}