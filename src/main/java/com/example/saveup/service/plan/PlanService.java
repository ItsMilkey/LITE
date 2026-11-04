package com.example.saveup.service.plan;

import com.example.saveup.dto.AvisoPlanDTO;
import com.example.saveup.dto.CambioMontoDTO;
import com.example.saveup.dto.CambiosMontosDTO;
import com.example.saveup.dto.CategoriaPlanDTO;
import com.example.saveup.dto.GuiaDTO;
import com.example.saveup.dto.GrupoPlanResumenDTO;
import com.example.saveup.dto.IngresoReferenciaDTO;
import com.example.saveup.dto.MotivoSinPlanificarDTO;
import com.example.saveup.dto.PartidaCreacionDTO;
import com.example.saveup.dto.PartidaEdicionDTO;
import com.example.saveup.dto.PartidaPlanDTO;
import com.example.saveup.dto.PlanMensualResponseDTO;
import com.example.saveup.dto.ReferenciaCategoriaDTO;
import com.example.saveup.dto.RepartoTipoDTO;
import com.example.saveup.dto.ResumenPlanDTO;
import com.example.saveup.dto.SinPlanificarDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.DatosInvalidosException;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.error.ValidacionFallidaException;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.CodigoCategoria;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.GrupoPlan;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.model.PlanMensual;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.GrupoPlanRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.PlanMensualRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.categoria.PreferenciaTipoService;
import com.example.saveup.service.movimiento.FechasMovimiento;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaRef;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.GrupoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.GrupoResultado;
import com.example.saveup.service.plan.CalculadoraPlan.GuiaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.MotivoCalculado;
import com.example.saveup.service.plan.CalculadoraPlan.MovimientoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.Resultado;
import com.example.saveup.service.plan.CuotasMesService.CuotaDelMes;
import com.example.saveup.service.plan.ReferenciasHistoricas.Referencia;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.example.saveup.model.enums.TipoPresupuesto;

/**
 * Servicio del plan mensual (T10c): valida propiedad y reglas de negocio,
 * carga los datos con un número fijo de consultas, delega el cálculo en
 * {@link CalculadoraPlan} y arma el DTO completo. No hace cálculos de dinero.
 */
@Service
public class PlanService {

    static final int MAX_PARTIDAS = 100;
    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final Pattern CARACTERES_CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final String MENSAJE_NOMBRE_DUPLICADO =
            "Ya tienes una partida con ese nombre en este mes.";

    private final PlanMensualRepository planMensualRepository;
    private final PartidaPlanRepository partidaPlanRepository;
    private final MovimientoRepository movimientoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;
    private final GrupoPlanRepository grupoPlanRepository;
    private final DeudaRepository deudaRepository;
    private final PreferenciaTipoService preferenciaTipoService;
    private final CuotasMesService cuotasMesService;
    private final SecurityUtils securityUtils;
    private final CalculadoraPlan calculadora = new CalculadoraPlan();

    public PlanService(PlanMensualRepository planMensualRepository,
                       PartidaPlanRepository partidaPlanRepository,
                       MovimientoRepository movimientoRepository,
                       CategoriaRepository categoriaRepository,
                       UsuarioRepository usuarioRepository,
                       ConfiguracionPresupuestoRepository configuracionPresupuestoRepository,
                       GrupoPlanRepository grupoPlanRepository,
                       DeudaRepository deudaRepository,
                       PreferenciaTipoService preferenciaTipoService,
                       CuotasMesService cuotasMesService,
                       SecurityUtils securityUtils) {
        this.planMensualRepository = planMensualRepository;
        this.partidaPlanRepository = partidaPlanRepository;
        this.movimientoRepository = movimientoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionPresupuestoRepository = configuracionPresupuestoRepository;
        this.grupoPlanRepository = grupoPlanRepository;
        this.deudaRepository = deudaRepository;
        this.preferenciaTipoService = preferenciaTipoService;
        this.cuotasMesService = cuotasMesService;
        this.securityUtils = securityUtils;
    }

    /** Plan completo del mes; si no existe responde {@code existe: false} (200). */
    @Transactional(readOnly = true)
    public PlanMensualResponseDTO obtener(String mesTexto) {
        return obtener(mesTexto, false);
    }

    /**
     * Plan completo del mes. Con {@code incluirReferencias=true} agrega a cada
     * categoría su gasto histórico de referencia (T10d, sección 8).
     */
    @Transactional(readOnly = true)
    public PlanMensualResponseDTO obtener(String mesTexto, boolean incluirReferencias) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();
        PlanMensual plan = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mes))
                .orElse(null);
        return armarPlan(rut, mes, plan, incluirReferencias, List.of());
    }

    /** Define, cambia o limpia ({@code monto: null}) el ingreso de referencia. */
    @Transactional
    public PlanMensualResponseDTO actualizarIngreso(String mesTexto, IngresoReferenciaDTO dto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();

        PlanMensual plan = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mes))
                .orElse(null);

        if (plan == null && dto.getMonto() == null) {
            // Limpiar un ingreso que no existe no crea un plan vacío.
            return armarPlan(rut, mes, null);
        }
        if (plan == null) {
            plan = nuevoPlan(rut, mes);
        }
        plan.setIngresoReferencia(dto.getMonto() == null ? null : dinero(dto.getMonto()));
        plan.setActualizadoEn(Instant.now());
        plan = planMensualRepository.save(plan);
        return armarPlan(rut, mes, plan);
    }

    /** Crea una partida; si el mes no tenía plan, lo crea en el mismo paso. */
    @Transactional
    public PlanMensualResponseDTO crearPartida(String mesTexto, PartidaCreacionDTO dto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();

        String nombre = validarNombre(dto.getNombre());
        String normalizado = NombrePartida.normalizar(nombre);

        Deuda deuda = null;
        Categoria categoria;
        BigDecimal monto;
        if (dto.getDeudaId() != null) {
            if (dto.getMontoPlanificado() != null) {
                throw new DatosInvalidosException(
                        "No puedes enviar montoPlanificado cuando vinculas una deuda.");
            }
            deuda = resolverDeuda(dto.getDeudaId());
            categoria = categoriaDeudas();
            if (dto.getCategoriaId() != null && !dto.getCategoriaId().equals(categoria.getId())) {
                throw new DatosInvalidosException(
                        "Una partida vinculada a una deuda siempre es de la categoría Pago de Deudas.");
            }
            monto = cuotaDelMes(deuda, mes);
        } else {
            if (dto.getCategoriaId() == null) {
                throw new ValidacionFallidaException("categoriaId", "La categoría es obligatoria.");
            }
            if (dto.getMontoPlanificado() == null) {
                throw new ValidacionFallidaException("montoPlanificado",
                        "El monto planificado es obligatorio.");
            }
            categoria = buscarCategoria(dto.getCategoriaId());
            validarCategoriaDePartida(categoria);
            monto = dinero(dto.getMontoPlanificado());
        }
        GrupoPlan grupo = dto.getGrupoId() == null ? null : resolverGrupo(dto.getGrupoId());

        PlanMensual plan = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mes))
                .orElse(null);

        int orden;
        if (plan == null) {
            plan = planMensualRepository.save(nuevoPlan(rut, mes));
            orden = 1;
        } else {
            if (partidaPlanRepository.countByPlanId(plan.getId()) >= MAX_PARTIDAS) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                        "Un plan no puede tener más de " + MAX_PARTIDAS + " partidas.");
            }
            if (partidaPlanRepository.existsByPlanIdAndNombreNormalizado(plan.getId(), normalizado)) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO, MENSAJE_NOMBRE_DUPLICADO);
            }
            if (deuda != null && partidaPlanRepository.existsByPlanIdAndDeudaId(
                    plan.getId(), deuda.getId())) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                        "Ya existe una partida para esta deuda en este mes.");
            }
            orden = partidaPlanRepository.maxOrden(plan.getId()) + 1;
        }

        PartidaPlan partida = new PartidaPlan();
        partida.setPlan(plan);
        partida.setCategoria(categoria);
        partida.setGrupo(grupo);
        partida.setDeuda(deuda);
        partida.setNombre(nombre);
        partida.setNombreNormalizado(normalizado);
        partida.setMontoPlanificado(monto);
        partida.setOrden(orden);
        partidaPlanRepository.save(partida);

        plan.setActualizadoEn(Instant.now());
        planMensualRepository.save(plan);
        return armarPlan(rut, mes, plan);
    }

    /** Cambia nombre, categoría y grupo; el vínculo con una deuda solo se crea al crear la partida. */
    @Transactional
    public PlanMensualResponseDTO editarPartida(String mesTexto, Long partidaId, PartidaEdicionDTO dto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();
        PartidaPlan partida = resolverPartidaDelMes(rut, mes, partidaId);

        boolean quitarGrupo = Boolean.TRUE.equals(dto.getQuitarGrupo());
        if (dto.getGrupoId() != null && quitarGrupo) {
            throw new DatosInvalidosException(
                    "No puedes enviar grupoId y quitarGrupo al mismo tiempo.");
        }
        if (dto.getNombre() == null && dto.getCategoriaId() == null
                && dto.getGrupoId() == null && !quitarGrupo) {
            throw new ValidacionFallidaException("partida",
                    "Debes enviar al menos uno de los campos: nombre, categoriaId, grupoId "
                            + "o quitarGrupo.");
        }

        boolean cambio = false;
        if (dto.getNombre() != null) {
            String nombre = validarNombre(dto.getNombre());
            String normalizado = NombrePartida.normalizar(nombre);
            if (partidaPlanRepository.existsByPlanIdAndNombreNormalizadoAndIdNot(
                    partida.getPlan().getId(), normalizado, partida.getId())) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO, MENSAJE_NOMBRE_DUPLICADO);
            }
            if (!nombre.equals(partida.getNombre())) {
                partida.setNombre(nombre);
                partida.setNombreNormalizado(normalizado);
                cambio = true;
            }
        }

        if (dto.getCategoriaId() != null) {
            Categoria nueva = buscarCategoria(dto.getCategoriaId());
            validarCategoriaDePartida(nueva);
            if (partida.getDeuda() != null
                    && !nueva.getId().equals(partida.getCategoria().getId())) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                        "No puedes cambiar la categoría de una partida vinculada a una deuda.");
            }
            if (!nueva.getId().equals(partida.getCategoria().getId())) {
                if (movimientoRepository.countByPartidaId(partida.getId()) > 0) {
                    throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                            "No puedes cambiar la categoría de una partida que ya tiene movimientos vinculados.");
                }
                partida.setCategoria(nueva);
                cambio = true;
            }
        }

        if (dto.getGrupoId() != null) {
            GrupoPlan grupo = resolverGrupo(dto.getGrupoId());
            if (partida.getGrupo() == null || !grupo.getId().equals(partida.getGrupo().getId())) {
                partida.setGrupo(grupo);
                cambio = true;
            }
        }
        if (quitarGrupo && partida.getGrupo() != null) {
            partida.setGrupo(null);
            cambio = true;
        }

        if (cambio) {
            partida.setActualizadoEn(new Date());
            partidaPlanRepository.save(partida);
            marcarPlanActualizado(partida.getPlan());
        }
        return armarPlan(rut, mes, partida.getPlan());
    }

    /**
     * Aplica un lote atómico de montos absolutos. Si cualquier partida no
     * existe o el lote es inválido, no se cambia ninguna.
     */
    @Transactional
    public PlanMensualResponseDTO actualizarMontos(String mesTexto, CambiosMontosDTO dto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();
        PlanMensual plan = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mes))
                .orElseThrow(() -> new EntityNotFoundException("Plan no encontrado para el mes indicado."));

        List<CambioMontoDTO> cambios = dto.getCambios();
        Set<Long> ids = new HashSet<>();
        Map<Long, PartidaPlan> porId = new HashMap<>();
        for (PartidaPlan partida : partidaPlanRepository.buscarDelPlan(plan.getId())) {
            porId.put(partida.getId(), partida);
        }

        // Primero se valida todo; recién después se aplica (todo o nada).
        List<PartidaPlan> objetivos = new ArrayList<>(cambios.size());
        for (int i = 0; i < cambios.size(); i++) {
            CambioMontoDTO cambio = cambios.get(i);
            if (!ids.add(cambio.getPartidaId())) {
                throw new ValidacionFallidaException("cambios[" + i + "].partidaId",
                        "No puedes repetir una partida dentro del mismo lote.");
            }
            PartidaPlan partida = porId.get(cambio.getPartidaId());
            if (partida == null) {
                // Distingue 403 (otro usuario) de 404 (inexistente u otro mes).
                partidaPlanRepository.buscarConContexto(cambio.getPartidaId())
                        .ifPresent(ajena -> securityUtils.validarPropietario(
                                ajena.getPlan().getUsuario().getRut()));
                throw new EntityNotFoundException(
                        "Partida no encontrada en este plan con ID: " + cambio.getPartidaId());
            }
            if (partida.getDeuda() != null) {
                throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                        "El monto de una partida de deuda lo define la cuota.");
            }
            objetivos.add(partida);
        }

        Date ahoraPartida = new Date();
        for (int i = 0; i < cambios.size(); i++) {
            PartidaPlan partida = objetivos.get(i);
            BigDecimal nuevo = dinero(cambios.get(i).getMonto());
            if (partida.getMontoPlanificado().compareTo(nuevo) != 0) {
                partida.setMontoPlanificado(nuevo);
                partida.setActualizadoEn(ahoraPartida);
                partidaPlanRepository.save(partida);
            }
            // Si el monto no cambia no se toca actualizadoEn (T10c, C42).
        }
        plan.setActualizadoEn(Instant.now());
        planMensualRepository.save(plan);
        return armarPlan(rut, mes, plan);
    }

    /** Borra una partida; sus movimientos quedan sin partida y se reimputan por categoría. */
    @Transactional
    public void eliminarPartida(String mesTexto, Long partidaId) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();
        PartidaPlan partida = resolverPartidaDelMes(rut, mes, partidaId);
        PlanMensual plan = partida.getPlan();

        movimientoRepository.desvincularPartida(partida.getId());
        partidaPlanRepository.deleteById(partida.getId());
        marcarPlanActualizado(plan);
    }

    /**
     * Copia las partidas de otro mes. El destino debe estar sin partidas; el
     * ingreso de referencia solo se copia si el destino no tenía uno.
     */
    @Transactional
    public PlanMensualResponseDTO copiar(String mesTexto, String mesOrigenTexto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        YearMonth mesOrigen = MesPlan.parsear(mesOrigenTexto);
        if (mes.equals(mesOrigen)) {
            throw new com.example.saveup.error.ParametroInvalidoException(
                    "El mes de origen y el mes de destino deben ser distintos.");
        }
        String rut = securityUtils.getAuthenticatedRut();

        PlanMensual planOrigen = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mesOrigen))
                .orElseThrow(() -> new EntityNotFoundException("No existe un plan en el mes de origen."));
        List<PartidaPlan> partidasOrigen = partidaPlanRepository.buscarDelPlan(planOrigen.getId());
        if (partidasOrigen.isEmpty()) {
            throw new EntityNotFoundException("El mes de origen no tiene partidas para copiar.");
        }

        PlanMensual planDestino = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mes))
                .orElse(null);
        if (planDestino != null
                && partidaPlanRepository.countByPlanId(planDestino.getId()) > 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "El mes de destino ya tiene partidas.");
        }
        if (planDestino == null) {
            planDestino = nuevoPlan(rut, mes);
        }
        if (planDestino.getIngresoReferencia() == null) {
            planDestino.setIngresoReferencia(planOrigen.getIngresoReferencia());
        }
        planDestino.setActualizadoEn(Instant.now());
        planDestino = planMensualRepository.save(planDestino);

        // T10d: las partidas vinculadas solo se copian si la deuda tiene una
        // cuota que vence en el mes destino. Las omitidas se informan en avisos.
        List<Deuda> deudas = new ArrayList<>();
        for (PartidaPlan origen : partidasOrigen) {
            if (origen.getDeuda() != null) {
                deudas.add(origen.getDeuda());
            }
        }
        Map<Long, CuotaDelMes> cuotasDestino = cuotasMesService.cuotasDelMes(deudas, mes);
        List<AvisoPlanDTO> avisos = new ArrayList<>();

        List<PartidaPlan> copias = new ArrayList<>(partidasOrigen.size());
        for (PartidaPlan origen : partidasOrigen) {
            Deuda deuda = origen.getDeuda();
            BigDecimal monto = origen.getMontoPlanificado();
            if (deuda != null) {
                CuotaDelMes cuota = cuotasDestino.get(deuda.getId());
                if (cuota == null) {
                    avisos.add(avisoSinCuota(origen));
                    continue;
                }
                monto = cuota.monto();
            }
            PartidaPlan copia = new PartidaPlan();
            copia.setPlan(planDestino);
            copia.setCategoria(origen.getCategoria());
            copia.setGrupo(origen.getGrupo());
            copia.setDeuda(deuda);
            copia.setNombre(origen.getNombre());
            copia.setNombreNormalizado(origen.getNombreNormalizado());
            copia.setMontoPlanificado(monto);
            copia.setOrden(origen.getOrden());
            copias.add(copia);
        }
        partidaPlanRepository.saveAll(copias);

        return armarPlan(rut, mes, planDestino, false, avisos);
    }

    private static AvisoPlanDTO avisoSinCuota(PartidaPlan origen) {
        AvisoPlanDTO aviso = new AvisoPlanDTO();
        aviso.setMotivo("SIN_COUOTA_EN_EL_MES");
        aviso.setPartidaId(origen.getId());
        aviso.setNombre(origen.getNombre());
        aviso.setDeudaId(origen.getDeuda().getId());
        aviso.setMensaje("La deuda no tiene una cuota que venza en el mes de destino.");
        return aviso;
    }

    // ──────────────────────── armado del DTO ────────────────────────

    private PlanMensualResponseDTO armarPlan(String rut, YearMonth mes, PlanMensual plan) {
        return armarPlan(rut, mes, plan, false, List.of());
    }

    private PlanMensualResponseDTO armarPlan(String rut, YearMonth mes, PlanMensual plan,
                                             boolean incluirReferencias,
                                             List<AvisoPlanDTO> avisos) {
        // Tipo efectivo y guía: una consulta de preferencias y una de configuración
        // por solicitud (T10d); la resolución es en memoria.
        Map<Long, TipoPresupuesto> preferencias = preferenciaTipoService.cargarPreferencias(rut);
        ConfiguracionPresupuesto config = configuracionPresupuestoRepository
                .findByUsuarioRut(rut)
                .orElse(null);
        GuiaCalculo guia = guiaDe(config);

        List<GrupoPlan> gruposEntidad = grupoPlanRepository
                .findByUsuarioRutOrderByOrdenAscIdAsc(rut);
        List<GrupoCalculo> gruposCalculo = new ArrayList<>(gruposEntidad.size());
        for (GrupoPlan grupo : gruposEntidad) {
            gruposCalculo.add(new GrupoCalculo(grupo.getId(), grupo.getNombre(), grupo.getOrden()));
        }

        List<PartidaPlan> partidas = plan == null
                ? List.of()
                : partidaPlanRepository.buscarDelPlan(plan.getId());

        List<Movimiento> movimientos = movimientoRepository.findDelMesConCategoria(
                rut,
                FechasMovimiento.inicioDeMes(mes),
                FechasMovimiento.inicioDeMesSiguiente(mes));

        // T10d: la cuota se recalcula en cada lectura y se carga en lote. Si el
        // vínculo ya no calcula, se conserva el último monto guardado.
        List<Deuda> deudasVinculadas = new ArrayList<>();
        for (PartidaPlan partida : partidas) {
            if (partida.getDeuda() != null) {
                deudasVinculadas.add(partida.getDeuda());
            }
        }
        Map<Long, CuotaDelMes> cuotas = cuotasMesService.cuotasDelMes(deudasVinculadas, mes);
        Map<Long, Boolean> vinculosDesactualizados = new HashMap<>();

        List<PartidaCalculo> partidasCalculo = new ArrayList<>(partidas.size());
        Map<Long, PartidaPlan> entidadesPorId = new HashMap<>();
        for (PartidaPlan partida : partidas) {
            entidadesPorId.put(partida.getId(), partida);
            BigDecimal monto = partida.getMontoPlanificado();
            boolean desactualizado = false;
            if (partida.getDeuda() != null) {
                CuotaDelMes cuota = cuotas.get(partida.getDeuda().getId());
                if (cuota != null) {
                    monto = cuota.monto();
                } else {
                    desactualizado = true;
                }
            }
            vinculosDesactualizados.put(partida.getId(), desactualizado);
            partidasCalculo.add(new PartidaCalculo(
                    partida.getId(),
                    partida.getNombre(),
                    categoriaRef(partida.getCategoria(), preferencias),
                    monto,
                    partida.getOrden(),
                    partida.getGrupo() == null ? null : partida.getGrupo().getId(),
                    partida.getDeuda() == null ? null : partida.getDeuda().getId()));
        }

        List<MovimientoCalculo> movimientosCalculo = new ArrayList<>(movimientos.size());
        for (Movimiento movimiento : movimientos) {
            movimientosCalculo.add(new MovimientoCalculo(
                    movimiento.getMonto(),
                    movimiento.getTipoMovimiento(),
                    movimiento.getPartida() == null ? null : movimiento.getPartida().getId(),
                    movimiento.getDeuda() == null ? null : movimiento.getDeuda().getId(),
                    categoriaRef(movimiento.getCategoria(), preferencias)));
        }

        Resultado resultado = calculadora.calcular(
                plan == null ? null : plan.getIngresoReferencia(),
                guia,
                gruposCalculo,
                partidasCalculo,
                movimientosCalculo);

        Map<Long, Referencia> referencias = incluirReferencias
                ? cargarReferencias(rut, mes, resultado.porCategoria())
                : Map.of();

        PlanMensualResponseDTO dto = new PlanMensualResponseDTO();
        dto.setMes(mes.toString());
        dto.setExiste(plan != null);
        dto.setIngresoReferencia(plan == null ? null : plan.getIngresoReferencia());
        dto.setResumen(aResumen(resultado.resumen()));
        dto.setPartidas(aPartidas(resultado.partidas(), entidadesPorId,
                vinculosDesactualizados));
        dto.setPorCategoria(aCategorias(resultado.porCategoria(), referencias));
        dto.setRepartoPorTipo(aReparto(resultado.repartoPorTipo()));
        dto.setGuia(guia == null ? null : aGuia(guia));
        dto.setGrupos(aGrupos(resultado.grupos()));
        dto.setAvisos(avisos == null ? List.of() : avisos);
        return dto;
    }

    /**
     * Series de gasto por categoría y mes para las referencias (T10d, sección
     * 8): una consulta al rango [max(primerMes, mes−3), mes) y agrupación en
     * memoria por mes del negocio. La consulta de {@code primerMovimiento}
     * fija el inicio de la ventana.
     */
    private Map<Long, Referencia> cargarReferencias(String rut, YearMonth mes,
                                                    List<CategoriaResultado> categorias) {
        Date primerMovimiento = movimientoRepository.primerMovimiento(rut);
        if (primerMovimiento == null) {
            return Map.of();
        }
        YearMonth primerMes = FechasMovimiento.mesDe(primerMovimiento);
        if (!primerMes.isBefore(mes)) {
            return Map.of();
        }

        YearMonth desde = mes.minusMonths(3);
        if (desde.isBefore(primerMes)) {
            desde = primerMes;
        }
        List<Movimiento> movimientos = movimientoRepository.movimientosDelRango(
                rut, FechasMovimiento.inicioDeMes(desde), FechasMovimiento.inicioDeMes(mes));

        // Agrupación en memoria: EXTRACT(MONTH ...) en SQL usaría el mes UTC y
        // movería al mes siguiente los gastos de las últimas horas del mes en
        // America/Santiago (T10d, sección 8).
        Map<Long, Map<YearMonth, BigDecimal>> series = new HashMap<>();
        for (Movimiento movimiento : movimientos) {
            if (movimiento.getCategoria() == null
                    || (movimiento.getTipoMovimiento() != TipoMovimiento.GASTO_GENERAL
                    && movimiento.getTipoMovimiento() != TipoMovimiento.PAGO_DEUDA)) {
                continue;
            }
            YearMonth mesMovimiento = FechasMovimiento.mesDe(movimiento.getFecha());
            series.computeIfAbsent(movimiento.getCategoria().getId(), id -> new HashMap<>())
                    .merge(mesMovimiento, movimiento.getMonto().abs(), BigDecimal::add);
        }

        Map<Long, Referencia> referencias = new HashMap<>();
        for (CategoriaResultado categoria : categorias) {
            Map<YearMonth, BigDecimal> serie = series.getOrDefault(
                    categoria.categoriaId(), Map.of());
            referencias.put(categoria.categoriaId(),
                    ReferenciasHistoricas.calcular(serie, mes, primerMes));
        }
        return referencias;
    }

    private ResumenPlanDTO aResumen(CalculadoraPlan.ResumenCalculado calculado) {
        ResumenPlanDTO dto = new ResumenPlanDTO();
        dto.setPlanificadoTotal(calculado.planificadoTotal());
        dto.setRemanenteLibre(calculado.remanenteLibre());
        dto.setMargenSeguridadPct(calculado.margenSeguridadPct());
        dto.setExcedeIngreso(calculado.excedeIngreso());
        dto.setIngresoReal(calculado.ingresoReal());
        dto.setGastoRealTotal(calculado.gastoRealTotal());
        dto.setDisponibleReal(calculado.disponibleReal());
        dto.setAhorroRegistrado(calculado.ahorroRegistrado());
        dto.setSinPlanificar(aSinPlanificar(calculado.sinPlanificar()));
        return dto;
    }

    private SinPlanificarDTO aSinPlanificar(CalculadoraPlan.SinPlanificarCalculado calculado) {
        SinPlanificarDTO dto = new SinPlanificarDTO();
        dto.setMonto(calculado.monto());
        dto.setMovimientos(calculado.movimientos());
        List<MotivoSinPlanificarDTO> motivos = new ArrayList<>(calculado.porMotivo().size());
        for (MotivoCalculado motivo : calculado.porMotivo()) {
            MotivoSinPlanificarDTO item = new MotivoSinPlanificarDTO();
            item.setMotivo(motivo.motivo());
            item.setMonto(motivo.monto());
            item.setMovimientos(motivo.movimientos());
            motivos.add(item);
        }
        dto.setPorMotivo(motivos);
        return dto;
    }

    private List<PartidaPlanDTO> aPartidas(List<PartidaResultado> calculadas,
                                           Map<Long, PartidaPlan> entidadesPorId,
                                           Map<Long, Boolean> vinculosDesactualizados) {
        List<PartidaPlanDTO> dtos = new ArrayList<>(calculadas.size());
        for (PartidaResultado calculada : calculadas) {
            PartidaPlan entidad = entidadesPorId.get(calculada.partida().id());
            PartidaPlanDTO dto = new PartidaPlanDTO();
            dto.setId(calculada.partida().id());
            dto.setNombre(calculada.partida().nombre());
            dto.setCategoriaId(calculada.partida().categoria() == null
                    ? null
                    : calculada.partida().categoria().id());
            dto.setCategoriaCodigo(calculada.partida().categoria() == null
                    ? null
                    : calculada.partida().categoria().codigo());
            dto.setMontoPlanificado(calculada.partida().montoPlanificado());
            dto.setReal(calculada.real());
            dto.setDiferencia(calculada.diferencia());
            dto.setPorcentajeUsado(calculada.porcentajeUsado());
            dto.setEstado(calculada.estado());
            dto.setEstadoCuota(calculada.estadoCuota());
            dto.setGrupoId(calculada.partida().grupoId());
            dto.setDeudaId(calculada.partida().deudaId());
            dto.setVinculoDesactualizado(
                    Boolean.TRUE.equals(vinculosDesactualizados.get(calculada.partida().id())));
            dto.setOrden(calculada.partida().orden());
            dto.setActualizadoEn(entidad == null ? null : entidad.getActualizadoEn());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<CategoriaPlanDTO> aCategorias(List<CategoriaResultado> calculadas,
                                               Map<Long, Referencia> referencias) {
        List<CategoriaPlanDTO> dtos = new ArrayList<>(calculadas.size());
        for (CategoriaResultado calculada : calculadas) {
            CategoriaPlanDTO dto = new CategoriaPlanDTO();
            dto.setCategoriaId(calculada.categoriaId());
            dto.setCodigo(calculada.codigo());
            dto.setTipo(calculada.tipo());
            dto.setPlanificado(calculada.planificado());
            dto.setReal(calculada.real());
            dto.setDiferencia(calculada.diferencia());
            dto.setPorcentajeUsado(calculada.porcentajeUsado());
            dto.setEstado(calculada.estado());
            Referencia referencia = referencias.get(calculada.categoriaId());
            if (referencia != null) {
                ReferenciaCategoriaDTO referenciaDTO = new ReferenciaCategoriaDTO();
                referenciaDTO.setMesAnterior(referencia.mesAnterior());
                referenciaDTO.setPromedio3Meses(referencia.promedio3Meses());
                dto.setReferencia(referenciaDTO);
            }
            dtos.add(dto);
        }
        return dtos;
    }

    private static List<GrupoPlanResumenDTO> aGrupos(List<GrupoResultado> calculados) {
        List<GrupoPlanResumenDTO> dtos = new ArrayList<>(calculados.size());
        for (GrupoResultado calculado : calculados) {
            GrupoPlanResumenDTO dto = new GrupoPlanResumenDTO();
            dto.setId(calculado.id());
            dto.setNombre(calculado.nombre());
            dto.setOrden(calculado.orden());
            dto.setPlanificado(calculado.planificado());
            dto.setPorcentajeDelPlanificado(calculado.porcentajeDelPlanificado());
            dto.setReal(calculado.real());
            dto.setPartidaIds(calculado.partidaIds());
            dtos.add(dto);
        }
        return dtos;
    }

    // ──────────────────────── validaciones y utilidades ────────────────────────

    private PlanMensual nuevoPlan(String rut, YearMonth mes) {
        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));
        PlanMensual plan = new PlanMensual();
        plan.setUsuario(usuario);
        plan.setMes(MesPlan.primerDia(mes));
        return plan;
    }

    private PartidaPlan resolverPartidaDelMes(String rut, YearMonth mes, Long partidaId) {
        PartidaPlan partida = partidaPlanRepository.buscarConContexto(partidaId)
                .orElseThrow(() -> new EntityNotFoundException("Partida no encontrada con ID: " + partidaId));
        securityUtils.validarPropietario(partida.getPlan().getUsuario().getRut());
        if (!partida.getPlan().getMes().equals(MesPlan.primerDia(mes))) {
            throw new EntityNotFoundException("Partida no encontrada en el plan de este mes.");
        }
        return partida;
    }

    private Categoria buscarCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Categoría no encontrada con ID: " + categoriaId));
    }

    private GrupoPlan resolverGrupo(Long grupoId) {
        GrupoPlan grupo = grupoPlanRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Grupo no encontrado con ID: " + grupoId));
        securityUtils.validarPropietario(grupo.getUsuario().getRut());
        return grupo;
    }

    private Deuda resolverDeuda(Long deudaId) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Deuda no encontrada con ID: " + deudaId));
        securityUtils.validarPropietario(deuda.getUsuario().getRut());
        if (deuda.getEstado() == EstadoDeuda.CANCELADA) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "No puedes vincular una deuda cancelada.");
        }
        if (deuda.getFechaPrimeraCuota() == null) {
            throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                    "Esta deuda no tiene fechas de cuotas.");
        }
        return deuda;
    }

    private Categoria categoriaDeudas() {
        return categoriaRepository.findByCodigo(CodigoCategoria.DEUDAS)
                .orElseThrow(() -> new IllegalStateException(
                        "La categoría " + CodigoCategoria.DEUDAS + " no fue encontrada."));
    }

    private BigDecimal cuotaDelMes(Deuda deuda, YearMonth mes) {
        return cuotasMesService.buscar(deuda, mes)
                .map(CuotaDelMes::monto)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                        "No hay una cuota de la deuda que venza en el mes del plan."));
    }

    /** SUELDO y AHORRO no se usan en partidas (DC14/DC15). */
    private void validarCategoriaDePartida(Categoria categoria) {
        String codigo = categoria.getCodigo();
        if (CodigoCategoria.SUELDO.equals(codigo) || CodigoCategoria.AHORRO.equals(codigo)) {
            throw new DatosInvalidosException(
                    "La categoría " + categoria.getNombre() + " no se puede usar en una partida.");
        }
    }

    /** Recorta el nombre y rechaza caracteres de control; el formato lo valida el DTO. */
    private String validarNombre(String nombre) {
        if (nombre == null) {
            throw new ValidacionFallidaException("nombre", "El nombre de la partida es obligatorio.");
        }
        String recortado = nombre.trim();
        if (recortado.isEmpty()) {
            throw new ValidacionFallidaException("nombre", "El nombre de la partida es obligatorio.");
        }
        if (CARACTERES_CONTROL.matcher(recortado).find()) {
            throw new ValidacionFallidaException("nombre",
                    "El nombre no puede contener caracteres de control.");
        }
        return recortado;
    }

    private void marcarPlanActualizado(PlanMensual plan) {
        plan.setActualizadoEn(Instant.now());
        planMensualRepository.save(plan);
    }

    private static CategoriaRef categoriaRef(Categoria categoria,
                                             Map<Long, TipoPresupuesto> preferencias) {
        if (categoria == null) {
            return null;
        }
        return new CategoriaRef(categoria.getId(), categoria.getCodigo(),
                PreferenciaTipoService.tipoEfectivo(preferencias, categoria));
    }

    /** Guía del usuario; null si no tiene configuración (T10d, sección 4). */
    private static GuiaCalculo guiaDe(ConfiguracionPresupuesto config) {
        if (config == null) {
            return null;
        }
        return new GuiaCalculo(config.getPorcentajeNecesidades(),
                config.getPorcentajeDeseos(),
                config.getPorcentajeAhorro());
    }

    private static GuiaDTO aGuia(GuiaCalculo guia) {
        return new GuiaDTO(guia.necesidades(), guia.deseos(), guia.ahorro());
    }

    private static List<RepartoTipoDTO> aReparto(List<CalculadoraPlan.RepartoTipoCalculado> calculado) {
        List<RepartoTipoDTO> dtos = new ArrayList<>(calculado.size());
        for (CalculadoraPlan.RepartoTipoCalculado fila : calculado) {
            RepartoTipoDTO dto = new RepartoTipoDTO();
            dto.setTipo(fila.tipo());
            dto.setPlanificado(fila.planificado());
            dto.setPorcentajeDelIngreso(fila.porcentajeDelIngreso());
            dto.setReal(fila.real());
            dto.setPorcentajeRealDelIngresoReal(fila.porcentajeRealDelIngresoReal());
            dto.setDiferenciaPuntos(fila.diferenciaPuntos());
            dtos.add(dto);
        }
        return dtos;
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null
                ? BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO)
                : valor.setScale(ESCALA_DINERO, REDONDEO);
    }
}
