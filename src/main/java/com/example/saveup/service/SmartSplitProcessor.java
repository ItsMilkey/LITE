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
    public void procesarDistribucion(Usuario usuario, java.math.BigDecimal montoIngreso) {
        if (usuario == null || montoIngreso.compareTo(java.math.BigDecimal.ZERO) <= 0) {
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
                    && config.getPorcentajeAhorro().compareTo(java.math.BigDecimal.ZERO) > 0) {

                // 1. Calcular Monto para Ahorro general según configuración
                java.math.BigDecimal divisor = new java.math.BigDecimal("100");
                java.math.BigDecimal montoAhorro = montoIngreso.multiply(config.getPorcentajeAhorro()).divide(divisor, 2, java.math.RoundingMode.HALF_EVEN);

                // 2. Obtener las asignaciones porcentuales hacia cada meta
                List<AsignacionMetaPresupuesto> asignaciones = asignacionMetaPresupuestoRepository
                        .findByConfiguracionId(config.getId());

                if (asignaciones == null || asignaciones.isEmpty()) {
                    return;
                }

                // Resolver categoría de ahorro una sola vez
                Categoria catAhorro = resolverCategoriaAhorro();

                for (AsignacionMetaPresupuesto asignacion : asignaciones) {
                    if (asignacion.getPorcentajeAsignacion() != null && asignacion.getPorcentajeAsignacion().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        java.math.BigDecimal montoAbono = montoAhorro.multiply(asignacion.getPorcentajeAsignacion()).divide(divisor, 2, java.math.RoundingMode.HALF_EVEN);
                        if (montoAbono.compareTo(java.math.BigDecimal.ZERO) > 0 && asignacion.getMeta() != null) {
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
