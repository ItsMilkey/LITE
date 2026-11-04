package com.example.saveup.service.plan;

import com.example.saveup.model.enums.EstadoCuota;
import com.example.saveup.model.enums.EstadoEjecucion;
import com.example.saveup.model.enums.MotivoSinPlanificar;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica pura del plan del mes (T10c, secciones 5 y 6). No conoce Spring ni la
 * base de datos: recibe el ingreso de referencia, las partidas y los
 * movimientos del mes, y devuelve el resumen, las partidas calculadas, el
 * desglose por categoría y lo que quedó sin planificar.
 *
 * <p>Reglas de dinero: {@link BigDecimal}, escala 2 y HALF_UP. La única
 * excepción es {@code porcentajeUsado}, que se trunca (ROUND_DOWN) para que el
 * valor mostrado nunca alcance un umbral antes de que el estado lo haga.
 */
public final class CalculadoraPlan {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO_DINERO = RoundingMode.HALF_UP;
    private static final RoundingMode REDONDEO_PORCENTAJE = RoundingMode.DOWN;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO_DINERO);

    /** Datos mínimos de una categoría del catálogo. */
    public record CategoriaRef(Long id, String codigo, TipoPresupuesto tipo) {
    }

    /** Partida del plan tal como entra al cálculo. */
    public record PartidaCalculo(Long id, String nombre, CategoriaRef categoria,
                                 BigDecimal montoPlanificado, int orden,
                                 Long grupoId, Long deudaId) {
        /** Constructor compatible con los vectores de T10c (sin grupo ni deuda). */
        public PartidaCalculo(Long id, String nombre, CategoriaRef categoria,
                              BigDecimal montoPlanificado, int orden) {
            this(id, nombre, categoria, montoPlanificado, orden, null, null);
        }
    }

    /** Movimiento del mes, ya convertido a los datos que importan al plan. */
    public record MovimientoCalculo(BigDecimal monto, TipoMovimiento tipo, Long partidaId,
                                    Long deudaId, CategoriaRef categoria) {
        /** Constructor compatible con los vectores de T10c (sin deuda). */
        public MovimientoCalculo(BigDecimal monto, TipoMovimiento tipo, Long partidaId,
                                 CategoriaRef categoria) {
            this(monto, tipo, partidaId, null, categoria);
        }
    }

    /** Grupo del usuario tal como entra al cálculo. */
    public record GrupoCalculo(Long id, String nombre, Integer orden) {
    }

    public record GrupoResultado(Long id, String nombre, Integer orden, BigDecimal planificado,
                                 BigDecimal porcentajeDelPlanificado, BigDecimal real,
                                 List<Long> partidaIds) {
    }

    public record MotivoCalculado(MotivoSinPlanificar motivo, BigDecimal monto, int movimientos) {
    }

    public record SinPlanificarCalculado(BigDecimal monto, int movimientos,
                                         List<MotivoCalculado> porMotivo) {
    }

    public record ResumenCalculado(BigDecimal planificadoTotal, BigDecimal remanenteLibre,
                                   BigDecimal margenSeguridadPct, boolean excedeIngreso,
                                   BigDecimal ingresoReal, BigDecimal gastoRealTotal,
                                   BigDecimal disponibleReal, BigDecimal ahorroRegistrado,
                                   SinPlanificarCalculado sinPlanificar) {
    }

    public record PartidaResultado(PartidaCalculo partida, BigDecimal real, BigDecimal diferencia,
                                   BigDecimal porcentajeUsado, EstadoEjecucion estado,
                                   EstadoCuota estadoCuota) {
    }

    public record CategoriaResultado(Long categoriaId, String codigo, TipoPresupuesto tipo,
                                     BigDecimal planificado, BigDecimal real, BigDecimal diferencia,
                                     BigDecimal porcentajeUsado, EstadoEjecucion estado) {
    }

    /** Guía 50/30/20 del usuario; nula si no tiene configuración (T10d, sección 4). */
    public record GuiaCalculo(BigDecimal necesidades, BigDecimal deseos, BigDecimal ahorro) {
    }

    /** Reparto informativo por tipo, con su diferencia de puntos frente a la guía. */
    public record RepartoTipoCalculado(TipoPresupuesto tipo, BigDecimal planificado,
                                       BigDecimal porcentajeDelIngreso, BigDecimal real,
                                       BigDecimal porcentajeRealDelIngresoReal,
                                       BigDecimal diferenciaPuntos) {
    }

    public record Resultado(ResumenCalculado resumen, List<PartidaResultado> partidas,
                            List<CategoriaResultado> porCategoria,
                            List<RepartoTipoCalculado> repartoPorTipo,
                            GuiaCalculo guia,
                            List<GrupoResultado> grupos) {
    }

    /**
     * Variante sin guía: equivale a {@code guia == null}. La conserva el plan
     * sin configuración del usuario; el reparto por tipo sigue calculándose.
     */
    public Resultado calcular(BigDecimal ingresoReferencia,
                              List<PartidaCalculo> partidas,
                              List<MovimientoCalculo> movimientos) {
        return calcular(ingresoReferencia, null, List.of(), partidas, movimientos);
    }

    /**
     * Calcula el plan completo.
     *
     * @param ingresoReferencia ingreso del usuario para el mes; {@code null} si no definió
     * @param guia              guía 50/30/20 del usuario; {@code null} si no tiene configuración
     * @param partidas          partidas del plan, en orden de presentación
     * @param movimientos       movimientos del mes (la ventana ya la fijó el servicio)
     */
    public Resultado calcular(BigDecimal ingresoReferencia,
                              GuiaCalculo guia,
                              List<PartidaCalculo> partidas,
                              List<MovimientoCalculo> movimientos) {
        return calcular(ingresoReferencia, guia, List.of(), partidas, movimientos);
    }

    /**
     * Calcula el plan completo incluyendo los grupos del usuario (T10d).
     *
     * @param grupos grupos del usuario, en orden de presentación; puede ir vacío
     */
    public Resultado calcular(BigDecimal ingresoReferencia,
                              GuiaCalculo guia,
                              List<GrupoCalculo> grupos,
                              List<PartidaCalculo> partidas,
                              List<MovimientoCalculo> movimientos) {
        List<PartidaCalculo> partidasSeguras = partidas == null ? List.of() : partidas;
        List<MovimientoCalculo> movimientosSeguros = movimientos == null ? List.of() : movimientos;
        List<GrupoCalculo> gruposSeguros = grupos == null ? List.of() : grupos;

        Map<Long, PartidaCalculo> partidaPorId = new HashMap<>();
        Map<Long, PartidaCalculo> partidaPorDeuda = new HashMap<>();
        Map<Long, List<PartidaCalculo>> partidasPorCategoria = new HashMap<>();
        Map<Long, List<PartidaCalculo>> partidasPorGrupo = new HashMap<>();
        Map<TipoPresupuesto, BigDecimal> planificadoPorTipo = new EnumMap<>(TipoPresupuesto.class);
        Map<Long, BigDecimal> realPorPartida = new HashMap<>();
        Map<Long, BigDecimal> realPorCategoria = new HashMap<>();
        Map<TipoPresupuesto, BigDecimal> realPorTipo = new EnumMap<>(TipoPresupuesto.class);
        Map<Long, CategoriaRef> categorias = new LinkedHashMap<>();
        Map<Long, GrupoCalculo> gruposPorId = new HashMap<>();

        for (GrupoCalculo grupo : gruposSeguros) {
            gruposPorId.put(grupo.id(), grupo);
        }

        BigDecimal planificadoTotal = CERO;
        for (PartidaCalculo partida : partidasSeguras) {
            BigDecimal monto = dinero(partida.montoPlanificado());
            planificadoTotal = planificadoTotal.add(monto);
            realPorPartida.put(partida.id(), CERO);
            partidaPorId.put(partida.id(), partida);
            if (partida.deudaId() != null) {
                partidaPorDeuda.putIfAbsent(partida.deudaId(), partida);
            }
            if (grupoConocido(partida, gruposPorId)) {
                partidasPorGrupo
                        .computeIfAbsent(partida.grupoId(), id -> new ArrayList<>())
                        .add(partida);
            }
            if (partida.categoria() != null) {
                partidasPorCategoria
                        .computeIfAbsent(partida.categoria().id(), id -> new ArrayList<>())
                        .add(partida);
                categorias.putIfAbsent(partida.categoria().id(), partida.categoria());
                planificadoPorTipo.merge(partida.categoria().tipo(), monto, BigDecimal::add);
            }
        }
        planificadoTotal = dinero(planificadoTotal);

        BigDecimal ingresoReal = CERO;
        BigDecimal gastoRealTotal = CERO;
        BigDecimal ahorroRegistrado = CERO;

        BigDecimal montoSinPlanificar = CERO;
        int movimientosSinPlanificar = 0;
        Map<MotivoSinPlanificar, BigDecimal> montoPorMotivo = new EnumMap<>(MotivoSinPlanificar.class);
        Map<MotivoSinPlanificar, Integer> movimientosPorMotivo = new EnumMap<>(MotivoSinPlanificar.class);

        for (MovimientoCalculo movimiento : movimientosSeguros) {
            BigDecimal monto = dinero(movimiento.monto());
            TipoMovimiento tipo = movimiento.tipo();

            if (tipo == TipoMovimiento.INGRESO_GENERAL) {
                ingresoReal = ingresoReal.add(monto);
                continue;
            }
            if (tipo == TipoMovimiento.ABONO_META) {
                // Informativo: el ahorro no se imputa a partidas (DC14).
                ahorroRegistrado = ahorroRegistrado.add(monto.abs());
                continue;
            }
            if (tipo != TipoMovimiento.GASTO_GENERAL && tipo != TipoMovimiento.PAGO_DEUDA) {
                // RETIRO_META (y cualquier tipo futuro) no participa del plan.
                continue;
            }

            BigDecimal gasto = monto.abs();
            gastoRealTotal = gastoRealTotal.add(gasto);

            if (movimiento.categoria() != null) {
                CategoriaRef categoria = movimiento.categoria();
                categorias.putIfAbsent(categoria.id(), categoria);
                realPorCategoria.merge(categoria.id(), gasto, BigDecimal::add);
                realPorTipo.merge(categoria.tipo(), gasto, BigDecimal::add);
            }

            MotivoSinPlanificar motivo = null;
            PartidaCalculo objetivo = null;
            if (movimiento.partidaId() != null) {
                objetivo = partidaPorId.get(movimiento.partidaId());
                if (objetivo == null) {
                    motivo = MotivoSinPlanificar.SIN_PARTIDA;
                }
            } else {
                // T10d: un PAGO_DEUDA cuya deuda tiene partida vinculada va a
                // esa partida. Va después del partida_id explícito y antes del
                // cálculo por categoría.
                if (tipo == TipoMovimiento.PAGO_DEUDA && movimiento.deudaId() != null) {
                    objetivo = partidaPorDeuda.get(movimiento.deudaId());
                }
                if (objetivo == null) {
                    if (movimiento.categoria() == null) {
                        motivo = MotivoSinPlanificar.SIN_PARTIDA;
                    } else {
                        List<PartidaCalculo> candidatas =
                                partidasPorCategoria.getOrDefault(movimiento.categoria().id(), List.of());
                        if (candidatas.size() == 1) {
                            objetivo = candidatas.get(0);
                        } else if (candidatas.size() >= 2) {
                            motivo = MotivoSinPlanificar.CATEGORIA_AMBIGUA;
                        } else {
                            motivo = MotivoSinPlanificar.SIN_PARTIDA;
                        }
                    }
                }
            }

            if (objetivo != null) {
                realPorPartida.merge(objetivo.id(), gasto, BigDecimal::add);
            } else {
                montoSinPlanificar = montoSinPlanificar.add(gasto);
                movimientosSinPlanificar++;
                montoPorMotivo.merge(motivo, gasto, BigDecimal::add);
                movimientosPorMotivo.merge(motivo, 1, Integer::sum);
            }
        }

        List<PartidaResultado> partidasCalculadas = new ArrayList<>(partidasSeguras.size());
        for (PartidaCalculo partida : partidasSeguras) {
            BigDecimal real = dinero(realPorPartida.getOrDefault(partida.id(), CERO));
            BigDecimal planificado = dinero(partida.montoPlanificado());
            partidasCalculadas.add(new PartidaResultado(
                    partida,
                    real,
                    diferencia(planificado, real),
                    porcentajeUsado(real, planificado),
                    EstadoEjecucion.desde(real, planificado),
                    partida.deudaId() == null
                            ? null
                            : EstadoCuota.desde(real, planificado)));
        }

        List<CategoriaResultado> porCategoria = new ArrayList<>();
        for (Map.Entry<Long, CategoriaRef> entrada : categorias.entrySet()) {
            Long categoriaId = entrada.getKey();
            CategoriaRef categoria = entrada.getValue();
            BigDecimal planificado = CERO;
            for (PartidaCalculo partida : partidasPorCategoria.getOrDefault(categoriaId, List.of())) {
                planificado = planificado.add(dinero(partida.montoPlanificado()));
            }
            planificado = dinero(planificado);
            BigDecimal real = dinero(realPorCategoria.getOrDefault(categoriaId, CERO));
            porCategoria.add(new CategoriaResultado(
                    categoriaId,
                    categoria.codigo(),
                    categoria.tipo(),
                    planificado,
                    real,
                    diferencia(planificado, real),
                    porcentajeUsado(real, planificado),
                    EstadoEjecucion.desde(real, planificado)));
        }
        porCategoria.sort(Comparator.comparing(CategoriaResultado::categoriaId,
                Comparator.nullsLast(Long::compareTo)));

        List<MotivoCalculado> motivos = new ArrayList<>();
        for (MotivoSinPlanificar motivo : MotivoSinPlanificar.values()) {
            int cantidad = movimientosPorMotivo.getOrDefault(motivo, 0);
            if (cantidad > 0) {
                motivos.add(new MotivoCalculado(
                        motivo,
                        dinero(montoPorMotivo.getOrDefault(motivo, CERO)),
                        cantidad));
            }
        }

        BigDecimal remanenteLibre = ingresoReferencia == null
                ? null
                : dinero(ingresoReferencia.subtract(planificadoTotal));
        BigDecimal margenSeguridadPct = ingresoReferencia == null || ingresoReferencia.signum() == 0
                ? null
                : porcentaje(remanenteLibre, ingresoReferencia);
        boolean excedeIngreso = ingresoReferencia != null
                && planificadoTotal.compareTo(ingresoReferencia) > 0;

        ResumenCalculado resumen = new ResumenCalculado(
                planificadoTotal,
                remanenteLibre,
                margenSeguridadPct,
                excedeIngreso,
                dinero(ingresoReal),
                dinero(gastoRealTotal),
                dinero(ingresoReal.subtract(gastoRealTotal)),
                dinero(ahorroRegistrado),
                new SinPlanificarCalculado(dinero(montoSinPlanificar), movimientosSinPlanificar, motivos));

        List<RepartoTipoCalculado> repartoPorTipo = new ArrayList<>(3);
        for (TipoPresupuesto tipo : List.of(TipoPresupuesto.NECESIDAD,
                TipoPresupuesto.DESEO, TipoPresupuesto.OTROS)) {
            BigDecimal planificadoTipo = dinero(planificadoPorTipo.getOrDefault(tipo, CERO));
            BigDecimal realTipo = dinero(realPorTipo.getOrDefault(tipo, CERO));
            BigDecimal porcentajePlanificado = porcentajeSobreIngreso(planificadoTipo, ingresoReferencia);
            BigDecimal porcentajeReal = porcentajeSobreIngreso(realTipo, dinero(ingresoReal));
            repartoPorTipo.add(new RepartoTipoCalculado(
                    tipo,
                    planificadoTipo,
                    porcentajePlanificado,
                    realTipo,
                    porcentajeReal,
                    diferenciaPuntos(tipo, porcentajePlanificado, guia)));
        }

        return new Resultado(resumen, partidasCalculadas, porCategoria, repartoPorTipo, guia,
                grupos(gruposSeguros, partidasSeguras, gruposPorId, realPorPartida,
                        planificadoTotal));
    }

    /** Grupos del usuario en su orden y, al final, "Sin grupo" si hace falta. */
    private static List<GrupoResultado> grupos(List<GrupoCalculo> grupos,
                                               List<PartidaCalculo> partidas,
                                               Map<Long, GrupoCalculo> gruposPorId,
                                               Map<Long, BigDecimal> realPorPartida,
                                               BigDecimal planificadoTotal) {
        List<GrupoResultado> resultados = new ArrayList<>(grupos.size() + 1);
        for (GrupoCalculo grupo : grupos) {
            resultados.add(grupoResultado(grupo, partidas, realPorPartida, planificadoTotal,
                    true));
        }

        List<PartidaCalculo> sinGrupo = new ArrayList<>();
        for (PartidaCalculo partida : partidas) {
            if (!grupoConocido(partida, gruposPorId)) {
                sinGrupo.add(partida);
            }
        }
        if (!sinGrupo.isEmpty()) {
            resultados.add(grupoResultado(
                    new GrupoCalculo(null, "Sin grupo", null),
                    sinGrupo, realPorPartida, planificadoTotal, false));
        }
        return resultados;
    }

    private static GrupoResultado grupoResultado(GrupoCalculo grupo,
                                                 List<PartidaCalculo> partidas,
                                                 Map<Long, BigDecimal> realPorPartida,
                                                 BigDecimal planificadoTotal,
                                                 boolean filtrarPorGrupo) {
        BigDecimal planificado = CERO;
        BigDecimal real = CERO;
        List<Long> ids = new ArrayList<>();
        for (PartidaCalculo partida : partidas) {
            if (filtrarPorGrupo && !grupo.id().equals(partida.grupoId())) {
                continue;
            }
            planificado = planificado.add(dinero(partida.montoPlanificado()));
            real = real.add(dinero(realPorPartida.getOrDefault(partida.id(), CERO)));
            ids.add(partida.id());
        }
        planificado = dinero(planificado);
        return new GrupoResultado(
                grupo.id(),
                grupo.nombre(),
                grupo.orden(),
                planificado,
                porcentajeSobreIngreso(planificado, planificadoTotal),
                dinero(real),
                ids);
    }

    private static boolean grupoConocido(PartidaCalculo partida,
                                         Map<Long, GrupoCalculo> gruposPorId) {
        return partida.grupoId() != null && gruposPorId.containsKey(partida.grupoId());
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null ? CERO : valor.setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }

    /** Porcentaje sobre un total, HALF_UP a 2 decimales; null si el total es nulo o 0. */
    private static BigDecimal porcentajeSobreIngreso(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return null;
        }
        return parte.multiply(CIEN).divide(total, ESCALA_DINERO, REDONDEO_DINERO);
    }

    /**
     * Diferencia de puntos frente a la guía, 2 decimales. Solo información:
     * OTROS no tiene guía, y sin ingreso de referencia o sin guía es nulo. La
     * guía es un dato del plan: ninguna diferencia es un error.
     */
    private static BigDecimal diferenciaPuntos(TipoPresupuesto tipo, BigDecimal porcentajeDelIngreso,
                                               GuiaCalculo guia) {
        if (tipo == TipoPresupuesto.OTROS || porcentajeDelIngreso == null || guia == null) {
            return null;
        }
        BigDecimal referencia = tipo == TipoPresupuesto.NECESIDAD
                ? guia.necesidades()
                : guia.deseos();
        if (referencia == null) {
            return null;
        }
        return porcentajeDelIngreso.subtract(referencia).setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }

    private static BigDecimal diferencia(BigDecimal planificado, BigDecimal real) {
        return dinero(planificado.subtract(real));
    }

    /** Porcentaje truncado a 2 decimales; null si no hay monto planificado. */
    private static BigDecimal porcentajeUsado(BigDecimal real, BigDecimal planificado) {
        if (planificado == null || planificado.signum() == 0) {
            return null;
        }
        return real.multiply(CIEN).divide(planificado, ESCALA_DINERO, REDONDEO_PORCENTAJE);
    }

    /** Porcentaje redondeado HALF_UP a 2 decimales (margen y métricas informativas). */
    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return parte.multiply(CIEN).divide(total, ESCALA_DINERO, REDONDEO_DINERO);
    }
}
