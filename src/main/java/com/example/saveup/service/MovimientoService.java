package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.ModoPago;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.movimiento.SignoMovimiento;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

        // LÓGICA SMART-SPLIT: Delegada a un servicio de dominio especializado
        if (Boolean.TRUE.equals(dto.getAplicarPresupuesto())
                && dto.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL) {
            smartSplitProcessor.procesarDistribucion(usuario, montoCanonico);
        }

        // 5. Guardar la entidad en la base de datos.
        Movimiento movimientoGuardado = movimientoRepository.save(movimiento);

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
    public BigDecimal obtenerSaldoActual(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        BigDecimal saldo = movimientoRepository.findSaldoByUsuarioRut(rut);
        return saldo == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : saldo.setScale(2, RoundingMode.HALF_UP);
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
     * Rechaza los tipos que no corresponden a este endpoint. Los movimientos hacia y
     * desde metas se crean solo desde /api/metas, donde existe la meta que los respalda.
     */
    private void validarTipoRegistrable(TipoMovimiento tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio.");
        }
        if (!TIPOS_PERMITIDOS.contains(tipo)) {
            throw new IllegalArgumentException(
                    "El tipo " + tipo + " no se registra por este endpoint: "
                            + "los movimientos hacia y desde metas se crean desde /api/metas.");
        }
    }

    private MovimientoResponseDTO convertirAEntidadResponseDTO(Movimiento movimiento) {
        return MovimientoResponseDTO.from(movimiento);
    }
}
