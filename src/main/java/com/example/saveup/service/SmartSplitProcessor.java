package com.example.saveup.service;

import com.example.saveup.model.AsignacionMetaPresupuesto;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servicio de dominio especializado en ejecutar la lógica de distribución de ahorros
 * hacia metas financieras (algoritmo Smart-Split).
 *
 * Modelo de Flexibilidad y Desacoplamiento:
 * - Si automatizarAhorroEnMetas está activo (true), distribuye el porcentaje de ahorro
 *   hacia las metas asignadas mediante sub-movimientos ABONO_META y actualiza sus saldos.
 * - Si automatizarAhorroEnMetas está inactivo (false o null), el presupuesto actúa solo
 *   como directriz de visualización y control (50/30/20); no se debita saldo ni se alteran
 *   metas, manteniendo la liquidez íntegra en el saldo principal del usuario.
 */
@Service
public class SmartSplitProcessor {

    @Autowired
    private ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;

    @Autowired
    private AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;

    @Autowired
    private MetaAhorroRepository metaAhorroRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    /**
     * Procesa la distribución de un ingreso general entre las metas activas del usuario,
     * respetando la bandera de automatización opcional de metas.
     *
     * @param usuario Usuario titular del ingreso.
     * @param montoIngreso Monto total del ingreso percibido.
     */
    @Transactional
    public void procesarDistribucion(Usuario usuario, BigDecimal montoIngreso) {
        if (usuario == null || montoIngreso == null || montoIngreso.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        configuracionPresupuestoRepository.findByUsuarioRut(usuario.getRut()).ifPresent(config -> {
            // El motor evalúa conjuntamente:
            // 1. Presupuesto activo
            // 2. Indicador explícito de automatización en metas habilitado
            // 3. Porcentaje de ahorro superior a cero
            if (Boolean.TRUE.equals(config.getActivo())
                    && Boolean.TRUE.equals(config.getAutomatizarAhorroEnMetas())
                    && config.getPorcentajeAhorro() != null
                    && config.getPorcentajeAhorro().compareTo(BigDecimal.ZERO) > 0) {

                // 1. Calcular Monto para Ahorro general según configuración
                BigDecimal divisor = new BigDecimal("100");
                BigDecimal montoAhorro = montoIngreso.multiply(config.getPorcentajeAhorro())
                        .divide(divisor, 2, RoundingMode.HALF_UP);

                if (montoAhorro.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }

                // 2. Obtener las asignaciones porcentuales hacia cada meta
                List<AsignacionMetaPresupuesto> asignaciones = asignacionMetaPresupuestoRepository
                        .findByConfiguracionId(config.getId());

                if (asignaciones == null || asignaciones.isEmpty()) {
                    return;
                }

                List<AsignacionMetaPresupuesto> asignacionesValidas = asignaciones.stream()
                        .filter(a -> a.getMeta() != null && a.getPorcentajeAsignacion() != null
                                && a.getPorcentajeAsignacion().compareTo(BigDecimal.ZERO) > 0)
                        .toList();

                if (asignacionesValidas.isEmpty()) {
                    return;
                }

                // Resolver categoría de ahorro una sola vez
                Categoria catAhorro = resolverCategoriaAhorro();

                BigDecimal sumaPorcentajes = asignacionesValidas.stream()
                        .map(AsignacionMetaPresupuesto::getPorcentajeAsignacion)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalObjetivo = montoAhorro.multiply(sumaPorcentajes)
                        .divide(divisor, 2, RoundingMode.HALF_UP);

                BigDecimal sumaAbonos = BigDecimal.ZERO;
                int total = asignacionesValidas.size();

                for (int i = 0; i < total; i++) {
                    AsignacionMetaPresupuesto asignacion = asignacionesValidas.get(i);
                    BigDecimal montoAbono;

                    if (i == total - 1) {
                        // El residuo de redondeo va a la última asignación para sumar exactamente totalObjetivo
                        montoAbono = totalObjetivo.subtract(sumaAbonos);
                    } else {
                        montoAbono = montoAhorro.multiply(asignacion.getPorcentajeAsignacion())
                                .divide(divisor, 2, RoundingMode.HALF_UP);
                        sumaAbonos = sumaAbonos.add(montoAbono);
                    }

                    if (montoAbono.compareTo(BigDecimal.ZERO) > 0) {
                        MetaAhorro meta = asignacion.getMeta();

                        // Crear Movimiento de Abono a Meta (egreso contable del saldo corriente)
                        Movimiento abonoMovimiento = new Movimiento();
                        abonoMovimiento.setUsuario(usuario);
                        abonoMovimiento.setMonto(montoAbono.negate());
                        abonoMovimiento.setDescripcion("Abono Auto: " + meta.getNombre());
                        abonoMovimiento.setTipoMovimiento(TipoMovimiento.ABONO_META);
                        abonoMovimiento.setMetaAhorro(meta);
                        if (catAhorro != null) {
                            abonoMovimiento.setCategoria(catAhorro);
                        }

                        // Actualizar saldo acumulado en la Meta
                        meta.setMontoActual(meta.getMontoActual().add(montoAbono));
                        metaAhorroRepository.save(meta);

                        // Guardar sub-movimiento
                        movimientoRepository.save(abonoMovimiento);
                    }
                }
            }
            // Si automatizarAhorroEnMetas es false:
            // No se realiza ninguna deducción; el porcentaje de ahorro calculado
            // se conserva disponible como saldo líquido en la cuenta principal.
        });
    }

    private Categoria resolverCategoriaAhorro() {
        return categoriaRepository.findByNombre("Ahorro")
                .orElseGet(() -> {
                    List<Categoria> savings = categoriaRepository.findByTipoPresupuesto(TipoPresupuesto.AHORRO);
                    return savings.isEmpty() ? null : savings.get(0);
                });
    }
}
