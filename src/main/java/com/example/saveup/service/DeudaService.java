package com.example.saveup.service;

import com.example.saveup.dto.CondicionesCreditoInput;
import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.dto.PagoDeudaResponseDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO.CuotaSimulacionDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.ModoPago;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.finanzas.CalculoFinancieroException;
import com.example.saveup.service.finanzas.CondicionesCredito;
import com.example.saveup.service.finanzas.CuotaAmortizacion;
import com.example.saveup.service.finanzas.ImputacionPagos;
import com.example.saveup.service.finanzas.MotorFinanciero;
import com.example.saveup.service.finanzas.ResolutorCondiciones;
import com.example.saveup.service.finanzas.ResultadoCalculo;
import com.example.saveup.service.movimiento.SignoMovimiento;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Servicio de deudas. Es el único productor de movimientos PAGO_DEUDA y el
 * único lugar donde se calculan los saldos de una deuda: la matemática
 * financiera vive en el motor (MotorFinanciero, CalendarioCuotas e
 * ImputacionPagos) y los DTO quedan como datos.
 */
@Service
public class DeudaService {

    private static final Logger LOG = LoggerFactory.getLogger(DeudaService.class);

    private static final int ESCALA_DINERO = 2;
    private static final int ESCALA_PORCENTAJE_TASA = 4;
    private static final int ESCALA_TASA_ANUAL = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final DeudaRepository deudaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SecurityUtils securityUtils;
    private final MotorFinanciero motorFinanciero;

    public DeudaService(DeudaRepository deudaRepository, UsuarioRepository usuarioRepository,
                        MovimientoRepository movimientoRepository, CategoriaRepository categoriaRepository,
                        SecurityUtils securityUtils, MotorFinanciero motorFinanciero) {
        this.deudaRepository = deudaRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.categoriaRepository = categoriaRepository;
        this.securityUtils = securityUtils;
        this.motorFinanciero = motorFinanciero;
    }

    /** Resultado de registrar un pago: la deuda resultante y el movimiento que la registra. */
    public record ResultadoPago(DeudaResponseDTO deuda, MovimientoResponseDTO movimiento) {}

    @Transactional
    public DeudaResponseDTO crearDeuda(DeudaCreacionDTO dto) {
        String rut = securityUtils.getAuthenticatedRut();

        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        Deuda deuda = new Deuda();
        deuda.setUsuario(usuario);
        aplicarCalculoGuardar(deuda, dto);

        Deuda deudaGuardada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaGuardada);
    }

    @Transactional(readOnly = true)
    public List<DeudaResponseDTO> obtenerDeudasPorUsuario(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        List<DeudaResponseDTO> deudas = deudaRepository.findDeudasDTOByUsuarioRut(rut);
        // Los saldos se calculan en memoria sobre la proyección: sin consultas extra por deuda.
        deudas.forEach(this::enriquecer);
        return deudas;
    }

    /**
     * Registra un pago contra una deuda. Es el único camino para crear
     * movimientos PAGO_DEUDA: POST /api/movimientos con ese tipo delega aquí.
     *
     * @return la deuda resultante y el movimiento creado (negativo, por SignoMovimiento)
     * @throws IllegalArgumentException si el monto pedido no es válido (400)
     * @throws IllegalStateException    si se viola una regla de negocio (409)
     */
    @Transactional
    public ResultadoPago registrarPago(Long deudaId, PagoDeudaDTO pagoDTO) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        if (deuda.getEstado() != EstadoDeuda.PENDIENTE) {
            throw new IllegalStateException(
                    "Solo se pueden registrar pagos en deudas pendientes. Estado actual: " + deuda.getEstado());
        }

        BigDecimal pagosReales = totalPagadoReal(deudaId);
        BigDecimal totalPorPagar = totalPorPagar(deuda, pagosReales);
        if (totalPorPagar.signum() <= 0) {
            throw new IllegalStateException("La deuda no tiene saldo pendiente por pagar.");
        }

        List<CuotaAmortizacion> tabla = tablaAmortizacion(deuda);
        BigDecimal montoPago = modoPago(pagoDTO) == ModoPago.CUOTA_CALCULADA
                ? montoCuotaCalculada(tabla, deuda, pagosReales)
                : montoPersonalizado(pagoDTO.getMonto());

        if (montoPago.compareTo(totalPorPagar) > 0) {
            throw new IllegalStateException(
                    "El monto del pago (" + montoPago.toPlainString()
                            + ") supera el total por pagar (" + totalPorPagar.toPlainString() + ").");
        }

        Categoria categoriaDeudas = categoriaRepository.findByNombre("Deudas")
                .orElseThrow(() -> new IllegalStateException("La categoría 'Deudas' no fue encontrada. Asegúrate de que exista en la base de datos."));

        Movimiento pago = new Movimiento();
        pago.setUsuario(deuda.getUsuario());
        pago.setDeuda(deuda);
        pago.setMonto(SignoMovimiento.aplicar(TipoMovimiento.PAGO_DEUDA, montoPago));
        pago.setDescripcion(pagoDTO.getDescripcion());
        pago.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        pago.setCategoria(categoriaDeudas);
        Movimiento pagoGuardado = movimientoRepository.save(pago);

        BigDecimal pagadoConEstePago = pagosReales.add(montoPagadoPrevio(deuda)).add(montoPago);
        if (pagadoConEstePago.compareTo(deuda.getMontoTotal()) >= 0) {
            deuda.setEstado(EstadoDeuda.PAGADA);
        }
        deudaRepository.save(deuda);

        return new ResultadoPago(convertirADeudaResponseDTO(deuda), MovimientoResponseDTO.from(pagoGuardado));
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

        aplicarCalculoGuardar(deuda, dto);
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

    @Transactional(readOnly = true)
    public List<CuotaSimulacionDTO> obtenerAmortizacion(Long deudaId) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        CondicionesCredito condiciones = new CondicionesCredito(
                deuda.getModalidadCalculo(),
                deuda.getMontoCapital(),
                deuda.getCantidadCuotas(),
                deuda.getModalidadCalculo() == ModalidadCalculo.CUOTA_CONOCIDA ? null : deuda.getTasaMensual(),
                deuda.getModalidadCalculo() == ModalidadCalculo.CUOTA_CONOCIDA ? deuda.getValorCuota() : null,
                deuda.getGastosIniciales(),
                deuda.getCostoAdicionalPorCuota()
        );

        ResultadoCalculo resultado = motorFinanciero.calcular(condiciones);

        List<LocalDate> fechasVencimiento = null;
        if (deuda.getFechaPrimeraCuota() != null) {
            fechasVencimiento = motorFinanciero.fechasVencimiento(deuda.getFechaPrimeraCuota(), deuda.getCantidadCuotas());
        }

        List<CuotaSimulacionDTO> tabla = CuotaSimulacionDTO.from(resultado.tabla(), fechasVencimiento);

        // Estado de cada cuota por la imputación de los pagos reales (regla 40).
        BigDecimal pagosReales = totalPagadoReal(deudaId);
        List<ImputacionPagos.ImputacionCuota> imputacion = ImputacionPagos.imputar(
                resultado.tabla(), deuda.getCuotasPagadasPrevias(), pagosReales);
        for (int i = 0; i < tabla.size(); i++) {
            ImputacionPagos.ImputacionCuota fila = imputacion.get(i);
            tabla.get(i).setEstado(fila.estado());
            tabla.get(i).setMontoPagado(fila.pagado());
            tabla.get(i).setPrevia(fila.previa());
        }
        return tabla;
    }

    /** Detalle de una deuda del usuario autenticado, con todos los derivados. */
    @Transactional(readOnly = true)
    public DeudaResponseDTO obtenerDeuda(Long deudaId) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        DeudaResponseDTO dto = deudaRepository.findDeudaDTOById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));
        return enriquecer(dto);
    }

    /**
     * Historial de pagos reales de una deuda, paginado y en orden descendente
     * por fecha. El desglose de cada pago se calcula en memoria con
     * {@link ImputacionPagos}: imputación acumulada tras ese pago menos la del
     * pago anterior (regla 40). No se persiste y se usa una sola consulta.
     *
     * @throws IllegalArgumentException si page o size no son válidos (400)
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<PagoDeudaResponseDTO> obtenerPagos(Long deudaId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page debe ser >= 0 y size >= 1.");
        }

        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        List<Movimiento> pagos = movimientoRepository
                .findByDeudaIdAndTipoMovimientoOrderByFechaAscIdAsc(deudaId, TipoMovimiento.PAGO_DEUDA);

        List<PagoDeudaResponseDTO> historial = desglosarPagos(
                tablaAmortizacion(deuda), deuda.getCuotasPagadasPrevias(), pagos);
        Collections.reverse(historial); // la API expone lo más reciente primero

        int total = historial.size();

        PageResponseDTO<PagoDeudaResponseDTO> respuesta = new PageResponseDTO<>();
        respuesta.setCurrentPage(page);
        respuesta.setTotalItems(total);
        respuesta.setTotalPages((total + size - 1) / size);

        // (long) evita el desbordamiento de int con page grandes: si el offset cae
        // fuera del historial devolvemos una página vacía en lugar de un 500.
        long offset = (long) page * size;
        if (offset >= total) {
            respuesta.setContent(List.of());
            return respuesta;
        }

        respuesta.setContent(historial.subList((int) offset, (int) Math.min(offset + size, total)));
        return respuesta;
    }

    /**
     * Calcula el desglose capital/interés/costos de cada pago real, en orden de
     * imputación, como la imputación acumulada tras ese pago menos la del pago
     * anterior. La primera imputación de referencia es la de las cuotas previas.
     */
    private List<PagoDeudaResponseDTO> desglosarPagos(List<CuotaAmortizacion> tabla, int cuotasPagadasPrevias,
                                                      List<Movimiento> pagos) {
        List<PagoDeudaResponseDTO> salida = new ArrayList<>(pagos.size());
        List<ImputacionPagos.ImputacionCuota> anterior =
                ImputacionPagos.imputar(tabla, cuotasPagadasPrevias, BigDecimal.ZERO);
        BigDecimal acumulado = BigDecimal.ZERO;

        for (Movimiento pago : pagos) {
            BigDecimal monto = (pago.getMonto() != null ? pago.getMonto() : BigDecimal.ZERO)
                    .abs().setScale(ESCALA_DINERO, REDONDEO);
            acumulado = acumulado.add(monto);

            List<ImputacionPagos.ImputacionCuota> actual =
                    ImputacionPagos.imputar(tabla, cuotasPagadasPrevias, acumulado);

            salida.add(new PagoDeudaResponseDTO(
                    pago.getId(),
                    pago.getFecha(),
                    monto,
                    desglose(actual, anterior, ImputacionPagos.ImputacionCuota::capital),
                    desglose(actual, anterior, ImputacionPagos.ImputacionCuota::interes),
                    desglose(actual, anterior, ImputacionPagos.ImputacionCuota::costos),
                    pago.getDescripcion()));

            anterior = actual;
        }
        return salida;
    }

    /** Diferencia de un componente entre dos imputaciones acumuladas. */
    private BigDecimal desglose(List<ImputacionPagos.ImputacionCuota> actual,
                                List<ImputacionPagos.ImputacionCuota> anterior,
                                Function<ImputacionPagos.ImputacionCuota, BigDecimal> componente) {
        return totalComponente(actual, componente)
                .subtract(totalComponente(anterior, componente))
                .setScale(ESCALA_DINERO, REDONDEO);
    }

    /** Suma un componente (capital, interés o costos) de una imputación completa. */
    private BigDecimal totalComponente(List<ImputacionPagos.ImputacionCuota> imputacion,
                                       Function<ImputacionPagos.ImputacionCuota, BigDecimal> componente) {
        BigDecimal total = BigDecimal.ZERO;
        for (ImputacionPagos.ImputacionCuota fila : imputacion) {
            total = total.add(componente.apply(fila));
        }
        return total.setScale(ESCALA_DINERO, REDONDEO);
    }

    private void aplicarCalculoGuardar(Deuda deuda, DeudaCreacionDTO dto) {
        deuda.setNombre(dto.getNombre());
        deuda.setDescripcion(dto.getDescripcion());
        deuda.setTipoDeuda(dto.getTipoDeuda() != null ? dto.getTipoDeuda() : TipoDeuda.OTRO);

        if (dto.getCuotasPagadasPrevias() != null && dto.getCuotasPagadasPrevias() >= 0) {
            deuda.setCuotasPagadasPrevias(dto.getCuotasPagadasPrevias());
        }

        CondicionesCreditoInput condDto = dto.getCondiciones();
        ResolutorCondiciones.ResultadoResolucion res;

        if (condDto != null) {
            res = motorFinanciero.resolver(condDto);
        } else {
            // Compatibilidad
            if (dto.getMontoTotal() == null || dto.getCantidadCuotas() == null) {
                throw new IllegalArgumentException("Si no hay bloque condiciones, debe proporcionar montoTotal y cantidadCuotas");
            }
            CondicionesCreditoInput compDto = CondicionesCreditoInput.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(dto.getMontoTotal())
                .cantidadCuotas(dto.getCantidadCuotas())
                .build();
            res = motorFinanciero.resolver(compDto);
        }

        if (deuda.getCuotasPagadasPrevias() >= res.cantidadCuotas()) {
            throw new IllegalArgumentException("cuotasPagadasPrevias (" + deuda.getCuotasPagadasPrevias() +
                ") no puede ser mayor o igual a cantidadCuotas (" + res.cantidadCuotas() + ")");
        }

        CondicionesCredito condiciones = new CondicionesCredito(
                res.modalidad(), res.montoCapital(), res.cantidadCuotas(),
                res.tasaMensualFraccion(), res.valorCuotaInput(),
                res.gastosIniciales(), res.costoAdicionalPorCuota()
        );

        ResultadoCalculo resultado = motorFinanciero.calcular(condiciones);

        deuda.setModalidadCalculo(res.modalidad());
        deuda.setMontoCapital(res.montoCapital());
        deuda.setCantidadCuotas(res.cantidadCuotas());
        deuda.setFechaPrimeraCuota(res.fechaPrimeraCuota());
        deuda.setTasaMensual(resultado.tasaMensual()); // fracción
        deuda.setValorCuota(resultado.valorCuota());
        deuda.setGastosIniciales(res.gastosIniciales());
        deuda.setCostoAdicionalPorCuota(res.costoAdicionalPorCuota());
        deuda.setMontoTotal(resultado.montoTotal());
        deuda.setCostoTotalCredito(resultado.costoTotalCredito());
        deuda.setCargaAnualEquivalente(resultado.cargaAnualEquivalente());

        BigDecimal montoPagadoPrevio = BigDecimal.ZERO;
        for (int i = 0; i < deuda.getCuotasPagadasPrevias(); i++) {
            CuotaAmortizacion c = resultado.tabla().get(i);
            montoPagadoPrevio = montoPagadoPrevio.add(c.cuota()).add(c.costoAdicional());
        }
        deuda.setMontoPagadoPrevio(montoPagadoPrevio);
    }

    // ──────────────────────── Pagos: monto y saldos ────────────────────────

    private ModoPago modoPago(PagoDeudaDTO pagoDTO) {
        ModoPago modo = pagoDTO.getModoPago();
        // Un cliente que manda "modoPago": null se comporta como el valor por defecto.
        return modo != null ? modo : ModoPago.MONTO_PERSONALIZADO;
    }

    private BigDecimal montoPersonalizado(BigDecimal monto) {
        if (monto == null) {
            throw new IllegalArgumentException("El monto del pago es obligatorio en modo MONTO_PERSONALIZADO.");
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor a 0.");
        }
        return monto.setScale(ESCALA_DINERO, REDONDEO);
    }

    /** Cuota + costo adicional de la próxima cuota impaga, o lo que le falta si quedó parcial. */
    private BigDecimal montoCuotaCalculada(List<CuotaAmortizacion> tabla, Deuda deuda, BigDecimal pagosReales) {
        BigDecimal monto = ImputacionPagos.montoProximaCuotaImpaga(tabla, deuda.getCuotasPagadasPrevias(), pagosReales);
        if (monto.signum() <= 0) {
            throw new IllegalStateException("La deuda no tiene cuotas pendientes de pago.");
        }
        return monto;
    }

    /** Suma de los movimientos PAGO_DEUDA de la deuda, sin las cuotas pagadas previas. */
    private BigDecimal totalPagadoReal(Long deudaId) {
        BigDecimal total = deudaRepository.findTotalPagadoPorDeuda(deudaId);
        BigDecimal pagado = total != null ? total.abs() : BigDecimal.ZERO;
        return pagado.setScale(ESCALA_DINERO, REDONDEO);
    }

    private BigDecimal montoPagadoPrevio(Deuda deuda) {
        BigDecimal previo = deuda.getMontoPagadoPrevio();
        return (previo != null ? previo : BigDecimal.ZERO).setScale(ESCALA_DINERO, REDONDEO);
    }

    /** totalPorPagar = montoTotal − (previas + pagos reales); incluye los intereses futuros. */
    private BigDecimal totalPorPagar(Deuda deuda, BigDecimal pagosReales) {
        BigDecimal pagado = pagosReales.add(montoPagadoPrevio(deuda));
        return deuda.getMontoTotal().subtract(pagado).max(BigDecimal.ZERO).setScale(ESCALA_DINERO, REDONDEO);
    }

    // ──────────────────────── Cálculos en memoria sobre el DTO ────────────────────────

    private DeudaResponseDTO convertirADeudaResponseDTO(Deuda deuda) {
        DeudaResponseDTO dto = deudaRepository.findDeudaDTOById(deuda.getId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la deuda guardada"));
        return enriquecer(dto);
    }

    /**
     * Completa en memoria los campos derivados de un DTO de deuda (regla 40),
     * con el motor y el calendario: porcentajes de tasa, valor de cuota, fecha
     * de la última cuota y saldos. Si una deuda concreta no se puede recalcular
     * (dato histórico inválido) se registra una advertencia y esa deuda queda
     * sin campos derivados: el listado no se cae.
     */
    private DeudaResponseDTO enriquecer(DeudaResponseDTO dto) {
        if (dto == null) {
            return null;
        }
        try {
            List<CuotaAmortizacion> tabla = tablaAmortizacion(dto);
            aplicarDerivadosTasa(dto);
            aplicarDerivadosCalendario(dto);
            aplicarSaldos(dto, tabla);
        } catch (CalculoFinancieroException e) {
            LOG.warn("No se pudieron calcular los derivados de la deuda {}: {}", dto.getId(), e.getMessage());
        }
        return dto;
    }

    /** Tasa mensual en porcentaje y tasa anual efectiva = (1+m)^12 − 1, en porcentaje. */
    private void aplicarDerivadosTasa(DeudaResponseDTO dto) {
        BigDecimal fraccion = dto.getTasaMensualFraccion();
        dto.setTasaMensualPorcentaje(fraccion != null
                ? fraccion.multiply(CIEN).setScale(ESCALA_PORCENTAJE_TASA, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_PORCENTAJE_TASA, REDONDEO));

        dto.setTasaAnualEfectiva(fraccion != null && fraccion.compareTo(BigDecimal.ZERO) > 0
                ? motorFinanciero.calculoService().tasaMensualAAnual(fraccion)
                        .multiply(CIEN).setScale(ESCALA_TASA_ANUAL, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_TASA_ANUAL, REDONDEO));
    }

    /** Valor de cuota persistido (o montoTotal/cantidadCuotas) y fecha de la última cuota. */
    private void aplicarDerivadosCalendario(DeudaResponseDTO dto) {
        BigDecimal persistido = dto.getValorCuota();
        dto.setValorCuota(persistido != null && persistido.signum() > 0
                ? persistido.setScale(ESCALA_DINERO, REDONDEO)
                : divisionCuota(dto.getMontoTotal(), dto.getCantidadCuotas()));

        if (dto.getFechaPrimeraCuota() != null && dto.getCantidadCuotas() > 0) {
            List<LocalDate> fechas = motorFinanciero.calendarioCuotas()
                    .fechasVencimiento(dto.getFechaPrimeraCuota(), dto.getCantidadCuotas());
            dto.setFechaUltimaCuota(fechas.getLast());
        }
    }

    /** Saldos por imputación: pagado, restante, cuotas pagadas, total por pagar y saldo de capital. */
    private void aplicarSaldos(DeudaResponseDTO dto, List<CuotaAmortizacion> tabla) {
        BigDecimal previo = dto.getMontoPagadoPrevio() != null
                ? dto.getMontoPagadoPrevio().setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        BigDecimal reales = dto.getMontoPagadoReal() != null
                ? dto.getMontoPagadoReal().setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);

        List<ImputacionPagos.ImputacionCuota> imputacion =
                ImputacionPagos.imputar(tabla, dto.getCuotasPagadasPrevias(), reales);

        BigDecimal montoPagado = previo.add(reales).setScale(ESCALA_DINERO, REDONDEO);
        BigDecimal totalPorPagar = dto.getMontoTotal().subtract(montoPagado)
                .max(BigDecimal.ZERO).setScale(ESCALA_DINERO, REDONDEO);
        BigDecimal capitalImputado = ImputacionPagos.capitalImputado(imputacion);

        dto.setMontoPagado(montoPagado);
        dto.setMontoRestante(totalPorPagar);
        dto.setTotalPorPagar(totalPorPagar);
        dto.setSaldoCapital(dto.getMontoCapital().subtract(capitalImputado)
                .max(BigDecimal.ZERO).setScale(ESCALA_DINERO, REDONDEO));
        dto.setCuotasPagadas(ImputacionPagos.contarPagadas(imputacion));
        dto.setInteresesPagados(totalComponente(imputacion, ImputacionPagos.ImputacionCuota::interes));
        aplicarProximaCuota(dto, tabla, imputacion);

        if (dto.getEstado() == EstadoDeuda.PENDIENTE && totalPorPagar.signum() == 0) {
            dto.setEstado(EstadoDeuda.PAGADA);
        }
    }

    /** Próxima cuota impaga (número, lo pendiente y su vencimiento) desde la imputación. */
    private void aplicarProximaCuota(DeudaResponseDTO dto, List<CuotaAmortizacion> tabla,
                                     List<ImputacionPagos.ImputacionCuota> imputacion) {
        for (int i = 0; i < imputacion.size(); i++) {
            ImputacionPagos.ImputacionCuota fila = imputacion.get(i);
            if (fila.estado() != ImputacionPagos.EstadoCuota.PAGADA) {
                dto.setProximaCuotaNumero(fila.numero());
                // El monto pendiente lo reutiliza de la clase pura: no se re-deriva aquí.
                dto.setProximaCuotaMontoPendiente(ImputacionPagos.montoProximaCuotaImpaga(
                        tabla, dto.getCuotasPagadasPrevias(), dto.getMontoPagadoReal()));
                dto.setProximaFechaVencimiento(fechaVencimiento(dto, i));
                return;
            }
        }
    }

    /** Fecha de vencimiento de la cuota en la posición indicada (null si no hay calendario). */
    private LocalDate fechaVencimiento(DeudaResponseDTO dto, int indice) {
        if (dto.getFechaPrimeraCuota() == null || dto.getCantidadCuotas() <= 0) {
            return null;
        }
        return motorFinanciero.calendarioCuotas()
                .fechasVencimiento(dto.getFechaPrimeraCuota(), dto.getCantidadCuotas())
                .get(indice);
    }

    private BigDecimal divisionCuota(BigDecimal montoTotal, int cantidadCuotas) {
        if (cantidadCuotas <= 0 || montoTotal == null) {
            return BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        }
        return montoTotal.divide(BigDecimal.valueOf(cantidadCuotas), ESCALA_DINERO, REDONDEO);
    }

    /**
     * Reconstruye el cronograma desde las condiciones persistidas. En
     * CUOTA_CONOCIDA la tasa va null al motor: la tasa implícita se resuelve
     * desde la cuota publicada.
     */
    private List<CuotaAmortizacion> tablaAmortizacion(Deuda deuda) {
        return tablaAmortizacion(
                deuda.getModalidadCalculo(), deuda.getMontoCapital(), deuda.getCantidadCuotas(),
                deuda.getTasaMensual(), deuda.getValorCuota(),
                deuda.getGastosIniciales(), deuda.getCostoAdicionalPorCuota());
    }

    private List<CuotaAmortizacion> tablaAmortizacion(DeudaResponseDTO dto) {
        return tablaAmortizacion(
                dto.getModalidadCalculo(), dto.getMontoCapital(), dto.getCantidadCuotas(),
                dto.getTasaMensualFraccion(), dto.getValorCuota(),
                dto.getGastosIniciales(), dto.getCostoAdicionalPorCuota());
    }

    private List<CuotaAmortizacion> tablaAmortizacion(
            ModalidadCalculo modalidad, BigDecimal montoCapital, int cantidadCuotas,
            BigDecimal tasaMensualFraccion, BigDecimal valorCuota,
            BigDecimal gastosIniciales, BigDecimal costoAdicionalPorCuota) {

        ModalidadCalculo modalidadEfectiva = modalidad != null ? modalidad : ModalidadCalculo.SIN_INTERES;
        boolean cuotaConocida = modalidadEfectiva == ModalidadCalculo.CUOTA_CONOCIDA;

        CondicionesCredito condiciones = new CondicionesCredito(
                modalidadEfectiva,
                montoCapital,
                cantidadCuotas,
                cuotaConocida ? null : tasaMensualFraccion,
                cuotaConocida ? valorCuota : null,
                gastosIniciales,
                costoAdicionalPorCuota);

        return motorFinanciero.calcular(condiciones).tabla();
    }
}
