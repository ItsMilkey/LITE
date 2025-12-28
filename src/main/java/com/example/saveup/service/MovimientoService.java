package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.DeudaRepository;
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
import com.example.saveup.model.AsignacionMetaPresupuesto;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.MetaAhorroRepository;

@Service
public class MovimientoService {

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DeudaRepository deudaRepository;

    @Autowired
    private com.example.saveup.repository.CategoriaRepository categoriaRepository;

    @Autowired
    private MetaAhorroRepository metaAhorroRepository;

    @Autowired
    private ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;

    @Autowired
    private AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRegistroDTO dto) {
        // 1. Validar que el usuario exista.
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioRut())
                .orElseThrow(
                        () -> new EntityNotFoundException("Usuario no encontrado con RUT: " + dto.getUsuarioRut()));

        // 2. Crear la entidad Movimiento a partir del DTO.
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuario);
        movimiento.setMonto(dto.getMonto());
        movimiento.setDescripcion(dto.getDescripcion());
        movimiento.setTipoMovimiento(dto.getTipoMovimiento());
        // La fecha se establece automáticamente gracias a @PrePersist.

        // --- LÓGICA DE ASOCIACIÓN CON DEUDAS Y METAS ---

        // Si se proporciona un deudaId, se asocia el movimiento a esa deuda.
        if (dto.getDeudaId() != null) {
            // Validación de negocio: Solo los PAGO_DEUDA pueden tener un deudaId.
            if (dto.getTipoMovimiento() != TipoMovimiento.PAGO_DEUDA) {
                throw new IllegalArgumentException(
                        "El campo 'deudaId' solo es válido para movimientos de tipo PAGO_DEUDA.");
            }
            // Se busca la deuda y se asocia.
            Deuda deuda = deudaRepository.findById(dto.getDeudaId())
                    .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + dto.getDeudaId()));
            movimiento.setDeuda(deuda);

            // Opcional pero recomendado: Verificar si este pago salda la deuda.
            // Esto crea consistencia si se usa este endpoint en vez del de DeudaService.
            // Nota: Se realiza una comprobación después de que el movimiento se guarde
            // teóricamente.
            double nuevoTotalPagado = Math.abs(deudaRepository.findTotalPagadoPorDeuda(deuda.getId()))
                    + Math.abs(dto.getMonto());
            if (nuevoTotalPagado >= deuda.getMontoTotal()) {
                deuda.setEstado(EstadoDeuda.PAGADA);
                deudaRepository.save(deuda);
            }
        }

        /*
         * // Si se proporciona un metaId, se asocia el movimiento a esa meta (para el
         * futuro).
         * if (dto.getMetaId() != null) {
         * if (dto.getTipoMovimiento() != TipoMovimiento.ABONO_META &&
         * dto.getTipoMovimiento() != TipoMovimiento.RETIRO_META) {
         * throw new
         * IllegalArgumentException("El campo 'metaId' solo es válido para movimientos relacionados con metas."
         * );
         * }
         * MetaAhorro meta = metaAhorroRepository.findById(dto.getMetaId())
         * .orElseThrow(() -> new
         * EntityNotFoundException("Meta de ahorro no encontrada con ID: " +
         * dto.getMetaId()));
         * movimiento.setMetaAhorro(meta);
         * }
         */

        // 6. ASOCIACIÓN DE CATEGORÍA
        if (dto.getCategoriaId() != null) {
            com.example.saveup.model.Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Categoría no encontrada con ID: " + dto.getCategoriaId()));
            movimiento.setCategoria(categoria);
        }

        // 7. LÓGICA SMART-SPLIT (Planificación Automática)
        if (Boolean.TRUE.equals(dto.getAplicarPresupuesto())
                && dto.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL) {
            configuracionPresupuestoRepository.findByUsuarioRut(usuario.getRut()).ifPresent(config -> {
                if (Boolean.TRUE.equals(config.getActivo())) {
                    // 1. Calcular Monto para Ahorro
                    double montoAhorro = dto.getMonto() * (config.getPorcentajeAhorro() / 100.0);

                    // 2. Distribuir en Metas
                    List<AsignacionMetaPresupuesto> asignaciones = asignacionMetaPresupuestoRepository
                            .findByConfiguracionId(config.getId());

                    for (AsignacionMetaPresupuesto asignacion : asignaciones) {
                        double montoAbono = montoAhorro * (asignacion.getPorcentajeAsignacion() / 100.0);
                        if (montoAbono > 0) {
                            // Crear Movimiento de Abono a Meta
                            Movimiento abonoMovimiento = new Movimiento();
                            abonoMovimiento.setUsuario(usuario);
                            abonoMovimiento.setMonto(-montoAbono); // Se resta del saldo general virtualmente (o se
                                                                   // registra como gasto/transferencia?)
                            // NOTA: En SaveUp, ABONO_META suele tratarse como un EGRESO del saldo
                            // disponible y un INGRESO a la meta.
                            // Pero aquí lo registramos como ABONO_META. Dependiendo de cómo se calcule el
                            // saldo, esto podría restar.
                            // Vamos a asumir que ABONO_META resta del saldo 'disponible' en el dashboard si
                            // el saldo se calcula como Sum(Movimientos).

                            abonoMovimiento.setDescripcion("Abono Auto: " + asignacion.getMeta().getNombre());
                            abonoMovimiento.setTipoMovimiento(TipoMovimiento.ABONO_META);
                            abonoMovimiento.setMetaAhorro(asignacion.getMeta()); // Relationship exists

                            // Actualizar el saldo de la Meta
                            MetaAhorro meta = asignacion.getMeta();
                            meta.setMontoActual(meta.getMontoActual() + montoAbono);
                            metaAhorroRepository.save(meta);

                            movimientoRepository.save(abonoMovimiento);
                        }
                    }
                }
            });
        }

        // 3. Guardar la entidad en la base de datos.
        Movimiento movimientoGuardado = movimientoRepository.save(movimiento);

        // 4. Convertir la entidad guardada a un DTO de respuesta y devolverla.
        return convertirAEntidadResponseDTO(movimientoGuardado);
    }

    /**
     * Obtiene el historial de movimientos de un usuario.
     * Si se proporciona un límite, devuelve solo esa cantidad de movimientos
     * recientes.
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

        // Si el movimiento tiene una categoría, la convertimos a DTO y la añadimos.
        if (movimiento.getCategoria() != null) {
            CategoriaDTO categoriaDTO = new CategoriaDTO();
            categoriaDTO.setId(movimiento.getCategoria().getId());
            categoriaDTO.setNombre(movimiento.getCategoria().getNombre());
            categoriaDTO.setIconId(movimiento.getCategoria().getIconId());
            categoriaDTO.setColorHex(movimiento.getCategoria().getColorHex());
            dto.setCategoria(categoriaDTO);
        }

        return dto;
    }
}
