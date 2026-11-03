package com.example.saveup.service.plan;

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
                                 BigDecimal montoPlanificado, int orden) {
    }

    /** Movimiento del mes, ya convertido a los datos que importan al plan. */
    public record MovimientoCalculo(BigDecimal monto, TipoMovimiento tipo, Long partidaId,
                                    CategoriaRef categoria) {
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
                                   BigDecimal porcentajeUsado, EstadoEjecucion estado) {
    }

    public record CategoriaResultado(Long categoriaId, String codigo, TipoPresupuesto tipo,
                                     BigDecimal planificado, BigDecimal real, BigDecimal diferencia,
                                     BigDecimal porcentajeUsado, EstadoEjecucion estado) {
    }

    public record Resultado(ResumenCalculado resumen, List<PartidaResultado> partidas,
                            List<CategoriaResultado> porCategoria) {
    }

    /**
     * Calcula el plan completo.
     *
     * @param ingresoReferencia ingreso del usuario para el mes; {@code null} si no definió
     * @param partidas          partidas del plan, en orden de presentación
     * @param movimientos       movimientos del mes (la ventana ya la fijó el servicio)
     */
    public Resultado calcular(BigDecimal ingresoReferencia,
                              List<PartidaCalculo> partidas,
                              List<MovimientoCalculo> movimientos) {
        List<PartidaCalculo> partidasSeguras = partidas == null ? List.of() : partidas;
        List<MovimientoCalculo> movimientosSeguros = movimientos == null ? List.of() : movimientos;

        Map<Long, PartidaCalculo> partidaPorId = new HashMap<>();
        Map<Long, List<PartidaCalculo>> partidasPorCategoria = new HashMap<>();
        Map<Long, BigDecimal> realPorPartida = new HashMap<>();
        Map<Long, BigDecimal> realPorCategoria = new HashMap<>();
        Map<Long, CategoriaRef> categorias = new LinkedHashMap<>();

        BigDecimal planificadoTotal = CERO;
        for (PartidaCalculo partida : partidasSeguras) {
            BigDecimal monto = dinero(partida.montoPlanificado());
            planificadoTotal = planificadoTotal.add(monto);
            realPorPartida.put(partida.id(), CERO);
            partidaPorId.put(partida.id(), partida);
            if (partida.categoria() != null) {
                partidasPorCategoria
                        .computeIfAbsent(partida.categoria().id(), id -> new ArrayList<>())
                        .add(partida);
                categorias.putIfAbsent(partida.categoria().id(), partida.categoria());
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
            }

            MotivoSinPlanificar motivo = null;
            PartidaCalculo objetivo = null;
            if (movimiento.partidaId() != null) {
                objetivo = partidaPorId.get(movimiento.partidaId());
                if (objetivo == null) {
                    motivo = MotivoSinPlanificar.SIN_PARTIDA;
                }
            } else if (movimiento.categoria() == null) {
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
                    EstadoEjecucion.desde(real, planificado)));
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

        return new Resultado(resumen, partidasCalculadas, porCategoria);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null ? CERO : valor.setScale(ESCALA_DINERO, REDONDEO_DINERO);
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
