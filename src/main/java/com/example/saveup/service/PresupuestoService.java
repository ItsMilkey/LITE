package com.example.saveup.service;

import com.example.saveup.dto.AsignacionPresupuestoDTO;
import com.example.saveup.dto.ConfiguracionPresupuestoRequestDTO;
import com.example.saveup.dto.ConfiguracionPresupuestoResponseDTO;
import com.example.saveup.dto.EjecucionCategoriaDTO;
import com.example.saveup.dto.EjecucionPresupuestoDTO;
import com.example.saveup.dto.HistorialPresupuestoDTO;
import com.example.saveup.dto.PresupuestoCategoriaDTO;
import com.example.saveup.dto.PresupuestoCategoriaItemDTO;
import com.example.saveup.model.AsignacionMetaPresupuesto;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PresupuestoCategoria;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoEjecucion;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PresupuestoCategoriaRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.movimiento.FechasMovimiento;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import java.util.Set;

/**
 * Presupuesto del usuario (T5): límites por categoría, su ejecución mensual y el
 * historial. Toda identidad llega como RUT del token; la clase nunca lee el
 * usuario del cuerpo de la petición.
 *
 * <p>El cálculo mensual general (50/30/20) vive en
 * {@link #calcularEjecucionGeneral(String, YearMonth)} y lo reutilizan tanto
 * /ejecucion/me como /historial/me: no se duplica.
 *
 * <p>La conversión de mes a {@code java.util.Date} se delega por completo en
 * {@link FechasMovimiento}: esta clase no mezcla {@code java.time} con
 * {@code java.util.Date}.
 */
@Service
public class PresupuestoService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    /** Tope de meses que puede abarcar el historial. */
    private static final int MAX_MESES_HISTORIAL = 24;

    private static final BigDecimal PORCENTAJE_NECESIDADES_DEFECTO = new BigDecimal("50.00");
    private static final BigDecimal PORCENTAJE_DESEOS_DEFECTO = new BigDecimal("30.00");
    private static final BigDecimal PORCENTAJE_AHORRO_DEFECTO = new BigDecimal("20.00");

    private final PresupuestoCategoriaRepository presupuestoCategoriaRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimientoRepository movimientoRepository;
    private final ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;
    private final MetaAhorroRepository metaAhorroRepository;
    private final SecurityUtils securityUtils;

    public PresupuestoService(PresupuestoCategoriaRepository presupuestoCategoriaRepository,
                              CategoriaRepository categoriaRepository,
                              MovimientoRepository movimientoRepository,
                              ConfiguracionPresupuestoRepository configuracionPresupuestoRepository,
                              UsuarioRepository usuarioRepository,
                              AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository,
                              MetaAhorroRepository metaAhorroRepository,
                              SecurityUtils securityUtils) {
        this.presupuestoCategoriaRepository = presupuestoCategoriaRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoRepository = movimientoRepository;
        this.configuracionPresupuestoRepository = configuracionPresupuestoRepository;
        this.usuarioRepository = usuarioRepository;
        this.asignacionMetaPresupuestoRepository = asignacionMetaPresupuestoRepository;
        this.metaAhorroRepository = metaAhorroRepository;
        this.securityUtils = securityUtils;
    }

    // ─────────────────────────── Configuración 50/30/20 ───────────────────────────

    /** Configuración del usuario; si no existe, responde 404 NO_ENCONTRADO. */
    @Transactional(readOnly = true)
    public ConfiguracionPresupuestoResponseDTO obtenerConfiguracion(String rut) {
        return configuracionPresupuestoRepository.findByUsuarioRut(rut)
                .map(this::aDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No hay configuración de presupuesto para el usuario."));
    }

    /**
     * Crea o actualiza la configuración 50/30/20 y sus asignaciones a metas.
     *
     * @throws IllegalArgumentException si los porcentajes no suman 100.00 o alguno es negativo (400)
     * @throws EntityNotFoundException  si el usuario no existe (404)
     */
    @Transactional
    public ConfiguracionPresupuestoResponseDTO guardarConfiguracion(String rut,
                                                                    ConfiguracionPresupuestoRequestDTO request) {
        validarSumaPorcentajes(request);

        ConfiguracionPresupuesto config = configuracionPresupuestoRepository.findByUsuarioRut(rut)
                .orElseGet(() -> {
                    Usuario usuario = usuarioRepository.findById(rut)
                            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));
                    ConfiguracionPresupuesto nueva = new ConfiguracionPresupuesto();
                    nueva.setUsuario(usuario);
                    return nueva;
                });

        config.setPorcentajeNecesidades(request.getPorcentajeNecesidades());
        config.setPorcentajeDeseos(request.getPorcentajeDeseos());
        config.setPorcentajeAhorro(request.getPorcentajeAhorro());
        config.setActivo(request.getActivo() != null ? request.getActivo() : true);
        config.setAutomatizarAhorroEnMetas(
                request.getAutomatizarAhorroEnMetas() != null ? request.getAutomatizarAhorroEnMetas() : false);

        ConfiguracionPresupuesto guardada = configuracionPresupuestoRepository.save(config);
        reemplazarAsignaciones(guardada, request.getAsignaciones());
        return aDTO(guardada);
    }

    private void reemplazarAsignaciones(ConfiguracionPresupuesto config, List<AsignacionPresupuestoDTO> asignaciones) {
        if (asignaciones == null) {
            return;
        }
        List<AsignacionMetaPresupuesto> actuales =
                asignacionMetaPresupuestoRepository.findByConfiguracionId(config.getId());
        asignacionMetaPresupuestoRepository.deleteAll(actuales);

        for (AsignacionPresupuestoDTO asignacion : asignaciones) {
            if (asignacion.getMetaId() == null || asignacion.getPorcentaje() == null
                    || asignacion.getPorcentaje().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            metaAhorroRepository.findById(asignacion.getMetaId()).ifPresent(meta -> {
                // Anti-IDOR: la meta debe pertenecer al usuario autenticado.
                securityUtils.validarPropietario(meta.getUsuario().getRut());

                AsignacionMetaPresupuesto nueva = new AsignacionMetaPresupuesto();
                nueva.setConfiguracion(config);
                nueva.setMeta(meta);
                nueva.setPorcentajeAsignacion(asignacion.getPorcentaje());
                asignacionMetaPresupuestoRepository.save(nueva);
            });
        }
    }

    private ConfiguracionPresupuestoResponseDTO aDTO(ConfiguracionPresupuesto config) {
        return new ConfiguracionPresupuestoResponseDTO(
                config.getId(),
                config.getUsuario() != null ? config.getUsuario().getRut() : null,
                config.getPorcentajeNecesidades(),
                config.getPorcentajeDeseos(),
                config.getPorcentajeAhorro(),
                config.getActivo(),
                config.getAutomatizarAhorroEnMetas());
    }

    // ─────────────────────────── Límites por categoría ───────────────────────────

    /** Límites por categoría configurados por el usuario. */
    @Transactional(readOnly = true)
    public List<PresupuestoCategoriaDTO> obtenerCategorias(String rut) {
        return presupuestoCategoriaRepository.findByUsuarioRut(rut).stream()
                .map(this::aDTO)
                .toList();
    }

    /**
     * Reemplaza el conjunto completo de límites del usuario.
     *
     * @throws IllegalArgumentException si un límite es ≤ 0, si la categoría no
     *                                  existe o si hay categorías repetidas (400)
     */
    @Transactional
    public List<PresupuestoCategoriaDTO> reemplazarCategorias(String rut, List<PresupuestoCategoriaItemDTO> items) {
        List<PresupuestoCategoria> nuevos = validarYConstruir(rut, items);
        presupuestoCategoriaRepository.deleteByUsuarioRut(rut);
        presupuestoCategoriaRepository.saveAll(nuevos);
        return nuevos.stream().map(this::aDTO).toList();
    }

    private List<PresupuestoCategoria> validarYConstruir(String rut, List<PresupuestoCategoriaItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Set<Long> categoriaIdsVistos = new HashSet<>();
        List<PresupuestoCategoria> resultado = new ArrayList<>();
        Usuario usuario = null;

        for (PresupuestoCategoriaItemDTO item : items) {
            if (item == null || item.getCategoriaId() == null) {
                throw new IllegalArgumentException("Cada límite debe indicar una categoría.");
            }
            if (item.getLimiteMensual() == null) {
                throw new IllegalArgumentException("Cada límite debe indicar un monto mensual.");
            }
            if (item.getLimiteMensual().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El límite mensual debe ser mayor a 0.");
            }
            if (!categoriaIdsVistos.add(item.getCategoriaId())) {
                throw new IllegalArgumentException(
                        "La categoría " + item.getCategoriaId() + " está repetida en la petición.");
            }
            Categoria categoria = categoriaRepository.findById(item.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La categoría " + item.getCategoriaId() + " no existe."));
            if (usuario == null) {
                usuario = usuarioRepository.findById(rut)
                        .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));
            }

            PresupuestoCategoria nuevo = new PresupuestoCategoria();
            nuevo.setUsuario(usuario);
            nuevo.setCategoria(categoria);
            nuevo.setLimiteMensual(item.getLimiteMensual());
            resultado.add(nuevo);
        }
        return resultado;
    }

    // ─────────────────────── Ejecución por categoría (mes) ───────────────────────

    /** Ejecución de cada límite del usuario en el mes indicado (o el actual). */
    @Transactional(readOnly = true)
    public List<EjecucionCategoriaDTO> obtenerEjecucionCategorias(String rut, Integer month, Integer year) {
        YearMonth mes = resolverMes(month, year);
        List<PresupuestoCategoria> limites = presupuestoCategoriaRepository.findByUsuarioRut(rut);
        List<Movimiento> movimientos = cargarMovimientosDelMes(rut, mes);

        return limites.stream().map(limite -> {
            Categoria categoria = limite.getCategoria();
            BigDecimal gastado = gastadoDeCategoria(movimientos, categoria.getId());
            BigDecimal porcentaje = porcentajeUsado(gastado, limite.getLimiteMensual());
            return new EjecucionCategoriaDTO(
                    categoria.getId(),
                    categoria.getNombre(),
                    limite.getLimiteMensual(),
                    gastado,
                    porcentaje,
                    EstadoEjecucion.desdePorcentaje(porcentaje));
        }).toList();
    }

    // ──────────────────────── Ejecución general (reutilizable) ────────────────────

    /**
     * Ejecución general 50/30/20 de un mes. Es el método reutilizable: lo usan
     * /ejecucion/me y, mes a mes, /historial/me. Un mes sin ingresos devuelve
     * ceros sin dividir por cero.
     */
    @Transactional(readOnly = true)
    public EjecucionPresupuestoDTO calcularEjecucionGeneral(String rut, YearMonth mes) {
        ConfiguracionPresupuesto config = configuracionPresupuestoRepository.findByUsuarioRut(rut)
                .orElseGet(ConfiguracionPresupuesto::new);

        List<Movimiento> movs = cargarMovimientosDelMes(rut, mes);

        BigDecimal totalIncome = movs.stream()
                .filter(mv -> mv.getMonto().compareTo(BigDecimal.ZERO) > 0
                        && mv.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL)
                .map(Movimiento::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gastoNecesidad = BigDecimal.ZERO;
        BigDecimal gastoDeseos = BigDecimal.ZERO;
        BigDecimal ahorroRealizado = BigDecimal.ZERO;

        for (Movimiento mv : movs) {
            if (esGastoPresupuestable(mv) && mv.getCategoria() != null) {
                TipoPresupuesto tp = mv.getCategoria().getTipoPresupuesto();
                if (tp == TipoPresupuesto.NECESIDAD) {
                    gastoNecesidad = gastoNecesidad.add(mv.getMonto().abs());
                } else if (tp == TipoPresupuesto.DESEO) {
                    gastoDeseos = gastoDeseos.add(mv.getMonto().abs());
                }
            } else if (mv.getTipoMovimiento() == TipoMovimiento.ABONO_META) {
                ahorroRealizado = ahorroRealizado.add(mv.getMonto().abs());
            }
        }

        BigDecimal pNeed = config.getPorcentajeNecesidades() != null
                ? config.getPorcentajeNecesidades() : PORCENTAJE_NECESIDADES_DEFECTO;
        BigDecimal pWant = config.getPorcentajeDeseos() != null
                ? config.getPorcentajeDeseos() : PORCENTAJE_DESEOS_DEFECTO;
        BigDecimal pSave = config.getPorcentajeAhorro() != null
                ? config.getPorcentajeAhorro() : PORCENTAJE_AHORRO_DEFECTO;

        EjecucionPresupuestoDTO dto = new EjecucionPresupuestoDTO();
        dto.setTotalIngresos(totalIncome);
        dto.setPorcentajeNecesidadesConfigurado(pNeed);
        dto.setPorcentajeDeseosConfigurado(pWant);
        dto.setPorcentajeAhorroConfigurado(pSave);
        dto.setPresupuestoNecesidades(totalIncome.multiply(pNeed).divide(CIEN, 2, RoundingMode.HALF_UP));
        dto.setPresupuestoDeseos(totalIncome.multiply(pWant).divide(CIEN, 2, RoundingMode.HALF_UP));
        dto.setPresupuestoAhorro(totalIncome.multiply(pSave).divide(CIEN, 2, RoundingMode.HALF_UP));
        dto.setGastoNecesidades(gastoNecesidad);
        dto.setGastoDeseos(gastoDeseos);
        dto.setAhorroRealizado(ahorroRealizado);
        dto.setAutomatizarAhorroEnMetas(Boolean.TRUE.equals(config.getAutomatizarAhorroEnMetas()));
        return dto;
    }

    /** Igual que {@link #calcularEjecucionGeneral(String, YearMonth)} pero resuelve el mes actual si faltan params. */
    @Transactional(readOnly = true)
    public EjecucionPresupuestoDTO calcularEjecucionGeneral(String rut, Integer month, Integer year) {
        return calcularEjecucionGeneral(rut, resolverMes(month, year));
    }

    // ───────────────────────────── Historial mensual ─────────────────────────────

    /**
     * Historial de ejecución general entre dos meses inclusivos.
     *
     * @param desde mes inicial en formato YYYY-MM
     * @param hasta mes final en formato YYYY-MM
     * @throws IllegalArgumentException si el formato es inválido, si desde &gt; hasta
     *                                  o si el rango supera 24 meses (400)
     */
    @Transactional(readOnly = true)
    public List<HistorialPresupuestoDTO> obtenerHistorial(String rut, String desde, String hasta) {
        YearMonth inicio = parsearMes(desde, "desde");
        YearMonth fin = parsearMes(hasta, "hasta");

        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("El mes 'desde' no puede ser posterior al mes 'hasta'.");
        }
        long cantidadMeses = ChronoUnit.MONTHS.between(inicio, fin) + 1;
        if (cantidadMeses > MAX_MESES_HISTORIAL) {
            throw new IllegalArgumentException(
                    "El historial admite como máximo " + MAX_MESES_HISTORIAL + " meses; el rango pedido tiene "
                            + cantidadMeses + ".");
        }

        List<HistorialPresupuestoDTO> historial = new ArrayList<>();
        for (YearMonth mes = inicio; !mes.isAfter(fin); mes = mes.plusMonths(1)) {
            historial.add(new HistorialPresupuestoDTO(
                    mes.getYear(),
                    mes.getMonthValue(),
                    calcularEjecucionGeneral(rut, mes)));
        }
        return historial;
    }

    // ─────────────────────────── Validación de porcentajes ───────────────────────────

    /**
     * Exige que necesidades + deseos + ahorro sumen exactamente 100.00 y que
     * ningún porcentaje sea negativo. Usa {@code compareTo}, no {@code equals},
     * para no depender de la escala.
     *
     * @throws IllegalArgumentException si algún porcentaje es negativo o la suma no es 100.00 (400)
     */
    public void validarSumaPorcentajes(ConfiguracionPresupuestoRequestDTO request) {
        if (request == null
                || request.getPorcentajeNecesidades() == null
                || request.getPorcentajeDeseos() == null
                || request.getPorcentajeAhorro() == null) {
            throw new IllegalArgumentException("Los porcentajes de necesidades, deseos y ahorro son obligatorios.");
        }
        if (request.getPorcentajeNecesidades().compareTo(BigDecimal.ZERO) < 0
                || request.getPorcentajeDeseos().compareTo(BigDecimal.ZERO) < 0
                || request.getPorcentajeAhorro().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Ningún porcentaje (necesidades, deseos, ahorro) puede ser negativo.");
        }
        BigDecimal suma = request.getPorcentajeNecesidades()
                .add(request.getPorcentajeDeseos())
                .add(request.getPorcentajeAhorro());
        if (suma.compareTo(CIEN) != 0) {
            throw new IllegalArgumentException(
                    "La suma de los porcentajes (necesidades + deseos + ahorro) debe ser exactamente 100.00; se recibió "
                            + suma + ".");
        }
    }

    // ────────────────────────────────── Apoyo ──────────────────────────────────

    private List<Movimiento> cargarMovimientosDelMes(String rut, YearMonth mes) {
        return movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(
                rut, FechasMovimiento.inicioDeMes(mes), FechasMovimiento.inicioDeMesSiguiente(mes));
    }

    private boolean esGastoPresupuestable(Movimiento movimiento) {
        return movimiento.getTipoMovimiento() == TipoMovimiento.GASTO_GENERAL
                || movimiento.getTipoMovimiento() == TipoMovimiento.PAGO_DEUDA;
    }

    private BigDecimal gastadoDeCategoria(List<Movimiento> movimientos, Long categoriaId) {
        return movimientos.stream()
                .filter(this::esGastoPresupuestable)
                .filter(mv -> mv.getCategoria() != null && categoriaId.equals(mv.getCategoria().getId()))
                .map(mv -> mv.getMonto().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal porcentajeUsado(BigDecimal gastado, BigDecimal limite) {
        if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return gastado.multiply(CIEN).divide(limite, 2, RoundingMode.HALF_UP);
    }

    private PresupuestoCategoriaDTO aDTO(PresupuestoCategoria pc) {
        return new PresupuestoCategoriaDTO(
                pc.getCategoria().getId(),
                pc.getCategoria().getNombre(),
                pc.getLimiteMensual());
    }

    private YearMonth resolverMes(Integer month, Integer year) {
        YearMonth ahora = FechasMovimiento.mesActual();
        int m = month != null ? month : ahora.getMonthValue();
        int y = year != null ? year : ahora.getYear();
        if (m < 1 || m > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
        }
        return YearMonth.of(y, m);
    }

    private YearMonth parsearMes(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El parámetro '" + campo + "' es obligatorio (formato YYYY-MM).");
        }
        try {
            return YearMonth.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El parámetro '" + campo + "' debe tener el formato YYYY-MM.");
        }
    }
}
