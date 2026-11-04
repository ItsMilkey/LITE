package com.example.saveup.service.plan;

import com.example.saveup.dto.PorcentajesReglaDTO;
import com.example.saveup.dto.SugerenciaReglaResponseDTO;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.service.categoria.PreferenciaTipoService;
import com.example.saveup.service.movimiento.FechasMovimiento;
import com.example.saveup.service.plan.CalculadoraPlan.GuiaCalculo;
import com.example.saveup.service.plan.SugeridorRegla.MesSerie;
import com.example.saveup.service.plan.SugeridorRegla.Resultado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carga la serie de meses cerrados y la guía del usuario y delega el cálculo
 * en {@link SugeridorRegla} (T10d, sección 5). El puro no sabe de base de
 * datos, pero la carga sí vive aquí.
 *
 * <p>Ventana: hasta los 3 meses cerrados más recientes anteriores al mes en
 * curso (zona America/Santiago) con {@code ingresoReal > 0}; el recorrido se
 * detiene a los 24 meses. Un mes sin ingresos no aporta: ni entra a la serie.
 *
 * <p>Consultas: las preferencias se cargan una vez; por cada mes con ingresos
 * van dos consultas agregadas (ingreso real y gasto por categoría).
 */
@Service
public class SugerenciaReglaService {

    /** Meses con ingresos que aporta la serie (T10d, sección 5). */
    private static final int MESES_OBJETIVO = 3;

    /** Tope del recorrido hacia atrás (24 meses, como el historial de T5). */
    private static final int MESES_RECORRIDO_MAXIMO = 24;

    private final PreferenciaTipoService preferenciaTipoService;
    private final ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;
    private final MovimientoRepository movimientoRepository;
    private final SugeridorRegla sugeridor = new SugeridorRegla();

    public SugerenciaReglaService(PreferenciaTipoService preferenciaTipoService,
                                  ConfiguracionPresupuestoRepository configuracionPresupuestoRepository,
                                  MovimientoRepository movimientoRepository) {
        this.preferenciaTipoService = preferenciaTipoService;
        this.configuracionPresupuestoRepository = configuracionPresupuestoRepository;
        this.movimientoRepository = movimientoRepository;
    }

    /**
     * Sugerencia para el usuario autenticado. Solo lectura: no persiste nada.
     */
    @Transactional(readOnly = true)
    public SugerenciaReglaResponseDTO sugerir(String rut) {
        Map<Long, TipoPresupuesto> preferencias = preferenciaTipoService.cargarPreferencias(rut);
        ConfiguracionPresupuesto config = configuracionPresupuestoRepository
                .findByUsuarioRut(rut)
                .orElse(null);
        GuiaCalculo guia = config == null ? null : new GuiaCalculo(
                config.getPorcentajeNecesidades(),
                config.getPorcentajeDeseos(),
                config.getPorcentajeAhorro());

        Resultado resultado = sugeridor.sugerir(serie(rut, preferencias), guia);
        return aDTO(resultado);
    }

    // ────────────────────────────── helpers ──────────────────────────────

    /** Serie: hasta 3 meses cerrados con ingresos, recorriendo hasta 24 meses atrás. */
    private List<MesSerie> serie(String rut, Map<Long, TipoPresupuesto> preferencias) {
        YearMonth actual = FechasMovimiento.mesActual();
        List<MesSerie> serie = new ArrayList<>(MESES_OBJETIVO);
        for (int i = 1; i <= MESES_RECORRIDO_MAXIMO && serie.size() < MESES_OBJETIVO; i++) {
            YearMonth mes = actual.minusMonths(i);
            BigDecimal ingresoReal = movimientoRepository.ingresoRealDelMes(
                    rut,
                    FechasMovimiento.inicioDeMes(mes),
                    FechasMovimiento.inicioDeMesSiguiente(mes));
            if (ingresoReal == null || ingresoReal.signum() <= 0) {
                continue;
            }
            serie.add(new MesSerie(
                    mes.toString(),
                    ingresoReal,
                    gastoDelTipo(rut, mes, TipoPresupuesto.NECESIDAD, preferencias),
                    gastoDelTipo(rut, mes, TipoPresupuesto.DESEO, preferencias)));
        }
        return serie;
    }

    /** Gasto en el mes del tipo efectivo pedido; OTROS no se cuenta (criterio de T5). */
    private BigDecimal gastoDelTipo(String rut, YearMonth mes, TipoPresupuesto tipoBuscado,
                                    Map<Long, TipoPresupuesto> preferencias) {
        List<Object[]> filas = movimientoRepository.gastoPorCategoriaDelMes(
                rut,
                FechasMovimiento.inicioDeMes(mes),
                FechasMovimiento.inicioDeMesSiguiente(mes));
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] fila : filas) {
            Long categoriaId = (Long) fila[0];
            TipoPresupuesto tipoPorDefecto = (TipoPresupuesto) fila[1];
            BigDecimal monto = (BigDecimal) fila[2];
            TipoPresupuesto efectivo = preferencias.getOrDefault(categoriaId, tipoPorDefecto);
            if (efectivo == tipoBuscado) {
                total = total.add(monto);
            }
        }
        return total;
    }

    private static SugerenciaReglaResponseDTO aDTO(Resultado resultado) {
        SugerenciaReglaResponseDTO dto = new SugerenciaReglaResponseDTO();
        dto.setMesesAnalizados(resultado.mesesAnalizados());
        dto.setActual(aPorcentajes(resultado.actual()));
        dto.setSugerencia(aPorcentajes(resultado.sugerencia()));
        dto.setGastoSuperaIngreso(resultado.gastoSuperaIngreso());
        dto.setIgualALaGuia(resultado.igualALaGuia());
        dto.setMotivo(resultado.motivo());
        return dto;
    }

    private static PorcentajesReglaDTO aPorcentajes(SugeridorRegla.Porcentajes origen) {
        if (origen == null) {
            return null;
        }
        return new PorcentajesReglaDTO(origen.necesidades(), origen.deseos(), origen.ahorro());
    }
}
