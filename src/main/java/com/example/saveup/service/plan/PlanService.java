package com.example.saveup.service.plan;

import com.example.saveup.dto.CambioMontoDTO;
import com.example.saveup.dto.CambiosMontosDTO;
import com.example.saveup.dto.CategoriaPlanDTO;
import com.example.saveup.dto.IngresoReferenciaDTO;
import com.example.saveup.dto.MotivoSinPlanificarDTO;
import com.example.saveup.dto.PartidaCreacionDTO;
import com.example.saveup.dto.PartidaEdicionDTO;
import com.example.saveup.dto.PartidaPlanDTO;
import com.example.saveup.dto.PlanMensualResponseDTO;
import com.example.saveup.dto.ResumenPlanDTO;
import com.example.saveup.dto.SinPlanificarDTO;
import com.example.saveup.error.CodigoError;
import com.example.saveup.error.DatosInvalidosException;
import com.example.saveup.error.ErrorNegocio;
import com.example.saveup.error.ValidacionFallidaException;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.model.PlanMensual;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.PlanMensualRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.movimiento.FechasMovimiento;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaRef;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.MotivoCalculado;
import com.example.saveup.service.plan.CalculadoraPlan.MovimientoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.Resultado;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Servicio del plan mensual (T10c): valida propiedad y reglas de negocio,
 * carga los datos con un número fijo de consultas, delega el cálculo en
 * {@link CalculadoraPlan} y arma el DTO completo. No hace cálculos de dinero.
 */
@Service
public class PlanService {

    static final int MAX_PARTIDAS = 100;
    static final int MAX_CARACTERES_NOMBRE = 80;
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
    private final SecurityUtils securityUtils;
    private final CalculadoraPlan calculadora = new CalculadoraPlan();

    public PlanService(PlanMensualRepository planMensualRepository,
                       PartidaPlanRepository partidaPlanRepository,
                       MovimientoRepository movimientoRepository,
                       CategoriaRepository categoriaRepository,
                       UsuarioRepository usuarioRepository,
                       SecurityUtils securityUtils) {
        this.planMensualRepository = planMensualRepository;
        this.partidaPlanRepository = partidaPlanRepository;
        this.movimientoRepository = movimientoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.securityUtils = securityUtils;
    }

    /** Plan completo del mes; si no existe responde {@code existe: false} (200). */
    @Transactional(readOnly = true)
    public PlanMensualResponseDTO obtener(String mesTexto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();
        PlanMensual plan = planMensualRepository
                .findByUsuarioRutAndMes(rut, MesPlan.primerDia(mes))
                .orElse(null);
        return armarPlan(rut, mes, plan);
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
        plan.setActualizadoEn(new Date());
        plan = planMensualRepository.save(plan);
        return armarPlan(rut, mes, plan);
    }

    /** Crea una partida; si el mes no tenía plan, lo crea en el mismo paso. */
    @Transactional
    public PlanMensualResponseDTO crearPartida(String mesTexto, PartidaCreacionDTO dto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();

        Categoria categoria = buscarCategoria(dto.getCategoriaId());
        validarCategoriaDePartida(categoria);
        String nombre = validarNombre(dto.getNombre());
        String normalizado = NombrePartida.normalizar(nombre);

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
            orden = partidaPlanRepository.maxOrden(plan.getId()) + 1;
        }

        PartidaPlan partida = new PartidaPlan();
        partida.setPlan(plan);
        partida.setCategoria(categoria);
        partida.setNombre(nombre);
        partida.setNombreNormalizado(normalizado);
        partida.setMontoPlanificado(dinero(dto.getMontoPlanificado()));
        partida.setOrden(orden);
        partidaPlanRepository.save(partida);

        plan.setActualizadoEn(new Date());
        planMensualRepository.save(plan);
        return armarPlan(rut, mes, plan);
    }

    /** Cambia nombre y/o categoría; cambiar la categoría con movimientos vinculados es 409. */
    @Transactional
    public PlanMensualResponseDTO editarPartida(String mesTexto, Long partidaId, PartidaEdicionDTO dto) {
        YearMonth mes = MesPlan.parsear(mesTexto);
        String rut = securityUtils.getAuthenticatedRut();
        PartidaPlan partida = resolverPartidaDelMes(rut, mes, partidaId);

        if (dto.getNombre() == null && dto.getCategoriaId() == null) {
            throw new ValidacionFallidaException("partida",
                    "Debes enviar al menos uno de los campos: nombre o categoriaId.");
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
            if (!nueva.getId().equals(partida.getCategoria().getId())) {
                if (movimientoRepository.countByPartidaId(partida.getId()) > 0) {
                    throw new ErrorNegocio(CodigoError.CONFLICTO_REGLA_NEGOCIO,
                            "No puedes cambiar la categoría de una partida que ya tiene movimientos vinculados.");
                }
                partida.setCategoria(nueva);
                cambio = true;
            }
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
            objetivos.add(partida);
        }

        Date ahora = new Date();
        for (int i = 0; i < cambios.size(); i++) {
            PartidaPlan partida = objetivos.get(i);
            BigDecimal nuevo = dinero(cambios.get(i).getMonto());
            if (partida.getMontoPlanificado().compareTo(nuevo) != 0) {
                partida.setMontoPlanificado(nuevo);
                partida.setActualizadoEn(ahora);
                partidaPlanRepository.save(partida);
            }
            // Si el monto no cambia no se toca actualizadoEn (T10c, C42).
        }
        plan.setActualizadoEn(ahora);
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
        Date ahora = new Date();
        planDestino.setActualizadoEn(ahora);
        planDestino = planMensualRepository.save(planDestino);

        List<PartidaPlan> copias = new ArrayList<>(partidasOrigen.size());
        for (PartidaPlan origen : partidasOrigen) {
            PartidaPlan copia = new PartidaPlan();
            copia.setPlan(planDestino);
            copia.setCategoria(origen.getCategoria());
            copia.setNombre(origen.getNombre());
            copia.setNombreNormalizado(origen.getNombreNormalizado());
            copia.setMontoPlanificado(origen.getMontoPlanificado());
            copia.setOrden(origen.getOrden());
            copias.add(copia);
        }
        partidaPlanRepository.saveAll(copias);

        return armarPlan(rut, mes, planDestino);
    }

    // ──────────────────────── armado del DTO ────────────────────────

    private PlanMensualResponseDTO armarPlan(String rut, YearMonth mes, PlanMensual plan) {
        List<PartidaPlan> partidas = plan == null
                ? List.of()
                : partidaPlanRepository.buscarDelPlan(plan.getId());

        List<Movimiento> movimientos = movimientoRepository.findDelMesConCategoria(
                rut,
                FechasMovimiento.inicioDeMes(mes),
                FechasMovimiento.inicioDeMesSiguiente(mes));

        List<PartidaCalculo> partidasCalculo = new ArrayList<>(partidas.size());
        Map<Long, PartidaPlan> entidadesPorId = new HashMap<>();
        for (PartidaPlan partida : partidas) {
            entidadesPorId.put(partida.getId(), partida);
            partidasCalculo.add(new PartidaCalculo(
                    partida.getId(),
                    partida.getNombre(),
                    categoriaRef(partida.getCategoria()),
                    partida.getMontoPlanificado(),
                    partida.getOrden()));
        }

        List<MovimientoCalculo> movimientosCalculo = new ArrayList<>(movimientos.size());
        for (Movimiento movimiento : movimientos) {
            movimientosCalculo.add(new MovimientoCalculo(
                    movimiento.getMonto(),
                    movimiento.getTipoMovimiento(),
                    movimiento.getPartida() == null ? null : movimiento.getPartida().getId(),
                    categoriaRef(movimiento.getCategoria())));
        }

        Resultado resultado = calculadora.calcular(
                plan == null ? null : plan.getIngresoReferencia(),
                partidasCalculo,
                movimientosCalculo);

        PlanMensualResponseDTO dto = new PlanMensualResponseDTO();
        dto.setMes(mes.toString());
        dto.setExiste(plan != null);
        dto.setIngresoReferencia(plan == null ? null : plan.getIngresoReferencia());
        dto.setResumen(aResumen(resultado.resumen()));
        dto.setPartidas(aPartidas(resultado.partidas(), entidadesPorId));
        dto.setPorCategoria(aCategorias(resultado.porCategoria()));
        return dto;
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
                                           Map<Long, PartidaPlan> entidadesPorId) {
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
            dto.setOrden(calculada.partida().orden());
            dto.setActualizadoEn(entidad == null ? null : entidad.getActualizadoEn());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<CategoriaPlanDTO> aCategorias(List<CategoriaResultado> calculadas) {
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

    /** SUELDO y AHORRO no se usan en partidas (DC14/DC15). */
    private void validarCategoriaDePartida(Categoria categoria) {
        String codigo = categoria.getCodigo();
        if ("SUELDO".equals(codigo) || "AHORRO".equals(codigo)) {
            throw new DatosInvalidosException(
                    "La categoría " + categoria.getNombre() + " no se puede usar en una partida.");
        }
    }

    /** Recorta y valida el nombre (1 a 80, sin caracteres de control). */
    private String validarNombre(String nombre) {
        if (nombre == null) {
            throw new ValidacionFallidaException("nombre", "El nombre de la partida es obligatorio.");
        }
        String recortado = nombre.trim();
        if (recortado.isEmpty()) {
            throw new ValidacionFallidaException("nombre", "El nombre de la partida es obligatorio.");
        }
        if (recortado.length() > MAX_CARACTERES_NOMBRE) {
            throw new ValidacionFallidaException("nombre",
                    "El nombre no puede superar los " + MAX_CARACTERES_NOMBRE + " caracteres.");
        }
        if (CARACTERES_CONTROL.matcher(recortado).find()) {
            throw new ValidacionFallidaException("nombre",
                    "El nombre no puede contener caracteres de control.");
        }
        return recortado;
    }

    private void marcarPlanActualizado(PlanMensual plan) {
        plan.setActualizadoEn(new Date());
        planMensualRepository.save(plan);
    }

    private static CategoriaRef categoriaRef(Categoria categoria) {
        if (categoria == null) {
            return null;
        }
        return new CategoriaRef(categoria.getId(), categoria.getCodigo(), categoria.getTipoPresupuesto());
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null
                ? BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO)
                : valor.setScale(ESCALA_DINERO, REDONDEO);
    }
}
