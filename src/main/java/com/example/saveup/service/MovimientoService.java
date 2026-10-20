package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.dto.MovimientoEdicionDTO;
import com.example.saveup.dto.MovimientoFiltroDTO;
import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.ModoPago;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.pagination.Paginacion;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.MovimientoSpecifications;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.movimiento.SignoMovimiento;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MovimientoService {

    /**
     * Tipos que se pueden registrar directamente por este endpoint. Los movimientos
     * hacia y desde metas (ABONO_META, RETIRO_META) solo se crean desde
     * /api/metas/{id}/abonar y /api/metas/{id}/retirar, para que siempre exista la
     * meta que los respalda.
     */
    private static final Set<TipoMovimiento> TIPOS_PERMITIDOS = Set.of(
            TipoMovimiento.INGRESO_GENERAL,
            TipoMovimiento.GASTO_GENERAL,
            TipoMovimiento.PAGO_DEUDA);

    /**
     * Tipos editables/borrables desde este endpoint (T4). El resto se modifican
     * desde su módulo (deudas, metas): intentarlo responde 409.
     */
    private static final Set<TipoMovimiento> TIPOS_EDITABLES = Set.of(
            TipoMovimiento.INGRESO_GENERAL,
            TipoMovimiento.GASTO_GENERAL);

    private final MovimientoRepository movimientoRepository;

    private final UsuarioRepository usuarioRepository;

    private final CategoriaRepository categoriaRepository;

    private final SmartSplitProcessor smartSplitProcessor;

    private final SecurityUtils securityUtils;

    private final DeudaService deudaService;

    public MovimientoService(MovimientoRepository movimientoRepository, UsuarioRepository usuarioRepository,
                             CategoriaRepository categoriaRepository, SmartSplitProcessor smartSplitProcessor,
                             SecurityUtils securityUtils, DeudaService deudaService) {
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.smartSplitProcessor = smartSplitProcessor;
        this.securityUtils = securityUtils;
        this.deudaService = deudaService;
    }

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRegistroDTO dto) {
        // 1. Obtener identidad segura desde el token JWT
        String rut = securityUtils.getAuthenticatedRut();

        // 2. Solo los tipos de este módulo se registran aquí. El signo lo fija el
        // tipo, no el cliente (SignoMovimiento).
        validarTipoRegistrable(dto.getTipoMovimiento());

        // 3. Los pagos de deuda tienen un único productor: DeudaService.registrarPago.
        if (dto.getTipoMovimiento() == TipoMovimiento.PAGO_DEUDA) {
            return registrarPagoDeuda(dto);
        }

        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        BigDecimal montoCanonico = SignoMovimiento.aplicar(dto.getTipoMovimiento(), dto.getMonto());

        // 4. Crear la entidad Movimiento a partir del DTO.
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuario);
        movimiento.setMonto(montoCanonico);
        movimiento.setDescripcion(dto.getDescripcion());
        movimiento.setTipoMovimiento(dto.getTipoMovimiento());
        // La fecha se establece automáticamente gracias a @PrePersist.

        // ASOCIACIÓN DE CATEGORÍA
        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Categoría no encontrada con ID: " + dto.getCategoriaId()));
            movimiento.setCategoria(categoria);
        }

        // 5. Guardar el ingreso PRIMERO: así Smart-Split puede dejar en cada abono
        //    automático el vínculo movimientoOrigen (T4).
        Movimiento movimientoGuardado = movimientoRepository.save(movimiento);

        // LÓGICA SMART-SPLIT: Delegada a un servicio de dominio especializado.
        if (Boolean.TRUE.equals(dto.getAplicarPresupuesto())
                && dto.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL) {
            smartSplitProcessor.procesarDistribucion(movimientoGuardado, montoCanonico);
        }

        // 6. Convertir la entidad guardada a un DTO de respuesta y devolverla.
        return convertirAEntidadResponseDTO(movimientoGuardado);
    }

    /**
     * Delega el pago de deuda en {@link DeudaService#registrarPago}, que es el
     * único camino para crear un PAGO_DEUDA (misma validación, mismo signo y
     * mismo estado de la deuda). El monto viaja en positivo: el signo lo aplica
     * el servicio con SignoMovimiento.
     */
    private MovimientoResponseDTO registrarPagoDeuda(MovimientoRegistroDTO dto) {
        if (dto.getDeudaId() == null) {
            throw new IllegalArgumentException("El campo 'deudaId' es obligatorio para movimientos de tipo PAGO_DEUDA.");
        }
        if (dto.getMonto() == null) {
            throw new IllegalArgumentException("El monto del pago es obligatorio.");
        }

        PagoDeudaDTO pago = new PagoDeudaDTO();
        pago.setMonto(dto.getMonto().abs());
        pago.setDescripcion(dto.getDescripcion());
        pago.setModoPago(ModoPago.MONTO_PERSONALIZADO);

        return deudaService.registrarPago(dto.getDeudaId(), pago).movimiento();
    }

    /**
     * Obtiene el historial de movimientos de un usuario.
     * Si se proporciona un límite, devuelve solo esa cantidad de movimientos recientes.
     * Si no, el historial completo.
     */
    @Transactional(readOnly = true)
    public List<MovimientoResponseDTO> obtenerMovimientosPorUsuario(String rut, Integer limit) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }

        List<Movimiento> movimientos;

        if (limit != null) {
            Pageable pageable = Paginacion.crear(0, limit);
            movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut, pageable).getContent();
        } else {
            movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut);
        }

        return movimientos.stream()
                .map(this::convertirAEntidadResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal obtenerSaldoActual(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        BigDecimal saldo = movimientoRepository.findSaldoByUsuarioRut(rut);
        return saldo == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : saldo.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Obtiene el historial de movimientos de forma paginada, filtrando siempre
     * por el RUT del usuario autenticado. Los filtros son opcionales (T4).
     *
     * @throws IllegalArgumentException si {@code desde > hasta} o
     *                                  {@code montoMin > montoMax} (400)
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<MovimientoResponseDTO> obtenerMovimientosPaginados(String rut, Pageable pageable,
                                                                              MovimientoFiltroDTO filtro) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }

        validarRango(filtro);

        // 1. Obtenemos la página de entidades desde el repositorio aplicando filtros.
        Specification<Movimiento> especificacion = MovimientoSpecifications.conFiltros(rut, filtro);
        Page<Movimiento> paginaMovimientos = movimientoRepository.findAll(especificacion, pageable);

        // 2. Convertimos el contenido de la página de entidades a una lista de DTO
        List<MovimientoResponseDTO> contenidoDTO = paginaMovimientos.getContent().stream()
                .map(this::convertirAEntidadResponseDTO)
                .collect(Collectors.toList());

        // 3. Creamos y devolvemos un DTO de respuesta de página
        PageResponseDTO<MovimientoResponseDTO> respuesta = new PageResponseDTO<>();
        respuesta.setContent(contenidoDTO);
        respuesta.setCurrentPage(paginaMovimientos.getNumber());
        respuesta.setTotalItems(paginaMovimientos.getTotalElements());
        respuesta.setTotalPages(paginaMovimientos.getTotalPages());

        return respuesta;
    }

    /**
     * Edita un movimiento editable (T4): solo descripción, monto y categoría. La
     * fecha no se toca y el signo lo impone el tipo (SignoMovimiento).
     *
     * @throws jakarta.persistence.EntityNotFoundException si el movimiento no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si es de otro usuario (403)
     * @throws ErrorNegocio si el tipo no es editable o el ingreso tiene Smart-Split (409)
     */
    @Transactional
    public MovimientoResponseDTO editarMovimiento(Long id, MovimientoEdicionDTO dto) {
        Movimiento movimiento = buscarPropioOAccesoDenegado(id);
        validarEditable(movimiento);

        // Edición parcial (T4): solo se tocan los campos que el cliente envía.
        if (dto.getDescripcion() != null) {
            movimiento.setDescripcion(dto.getDescripcion());
        }
        if (dto.getMonto() != null) {
            movimiento.setMonto(SignoMovimiento.aplicar(movimiento.getTipoMovimiento(), dto.getMonto()));
        }
        if (dto.getCategoriaId() != null) {
            movimiento.setCategoria(resolverCategoria(dto.getCategoriaId()));
        }

        return convertirAEntidadResponseDTO(movimientoRepository.save(movimiento));
    }

    /**
     * Elimina un movimiento editable (T4) con las mismas validaciones que la edición.
     *
     * @throws jakarta.persistence.EntityNotFoundException si el movimiento no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si es de otro usuario (403)
     * @throws ErrorNegocio si el tipo no es editable o el ingreso tiene Smart-Split (409)
     */
    @Transactional
    public void eliminarMovimiento(Long id) {
        Movimiento movimiento = buscarPropioOAccesoDenegado(id);
        validarEditable(movimiento);
        movimientoRepository.delete(movimiento);
    }

    private Movimiento buscarPropioOAccesoDenegado(Long id) {
        Movimiento movimiento = movimientoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Movimiento no encontrado con ID: " + id));
        securityUtils.validarPropietario(movimiento.getUsuario().getRut());
        return movimiento;
    }

    /**
     * Solo se editan/borran INGRESO_GENERAL y GASTO_GENERAL sin deuda ni meta, y
     * sin abonos automáticos derivados (Smart-Split). Cualquier otro caso es 409.
     */
    private void validarEditable(Movimiento movimiento) {
        if (!TIPOS_EDITABLES.contains(movimiento.getTipoMovimiento())
                || movimiento.getDeuda() != null
                || movimiento.getMetaAhorro() != null) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "Este movimiento no se puede editar ni eliminar desde aquí; hazlo desde deudas o metas.");
        }
        if (movimientoRepository.existsByMovimientoOrigenId(movimiento.getId())) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "Este ingreso generó abonos automáticos a tus metas y no se puede editar ni eliminar.");
        }
    }

    private void validarRango(MovimientoFiltroDTO filtro) {
        if (filtro == null) {
            return;
        }
        if (filtro.getDesde() != null && filtro.getHasta() != null && filtro.getDesde().isAfter(filtro.getHasta())) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (filtro.getMontoMin() != null && filtro.getMontoMin().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto mínimo no puede ser negativo.");
        }
        if (filtro.getMontoMax() != null && filtro.getMontoMax().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto máximo no puede ser negativo.");
        }
        if (filtro.getMontoMin() != null && filtro.getMontoMax() != null
                && filtro.getMontoMin().compareTo(filtro.getMontoMax()) > 0) {
            throw new IllegalArgumentException("El monto mínimo no puede ser mayor que el monto máximo.");
        }
    }

    private Categoria resolverCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada con ID: " + categoriaId));
    }

    /**
     * Rechaza los tipos que no corresponden a este endpoint. Los movimientos hacia y
     * desde metas se crean solo desde /api/metas, donde existe la meta que los respalda.
     */
    private void validarTipoRegistrable(TipoMovimiento tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio.");
        }
        if (!TIPOS_PERMITIDOS.contains(tipo)) {
            throw new IllegalArgumentException(
                    "Los movimientos hacia y desde metas se registran desde la sección de metas.");
        }
    }

    private MovimientoResponseDTO convertirAEntidadResponseDTO(Movimiento movimiento) {
        return MovimientoResponseDTO.from(movimiento);
    }
}
