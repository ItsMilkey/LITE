package com.example.saveup.repository;

import com.example.saveup.dto.MovimientoFiltroDTO;
import com.example.saveup.model.Movimiento;
import com.example.saveup.service.movimiento.FechasMovimiento;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Specifications de {@link Movimiento} para el historial filtrado (T4).
 *
 * <p>Clase pura de armado de consultas: no accede a la base de datos por sí
 * misma. El filtro por RUT es SIEMPRE obligatorio (identidad tomada del JWT),
 * de modo que ningún filtro puede ampliar el alcance a otros usuarios.
 *
 * <p>La conversión de las fechas ISO al {@code java.util.Date} de la entidad se
 * delega en {@link FechasMovimiento}: esta clase no mezcla {@code java.time} con
 * {@code java.util.Date}.
 */
public final class MovimientoSpecifications {

    private MovimientoSpecifications() {
    }

    /**
     * Combina todos los filtros opcionales del DTO con el RUT del usuario
     * autenticado. Los filtros nulos (o un {@code q} en blanco) se ignoran.
     */
    public static Specification<Movimiento> conFiltros(String rut, MovimientoFiltroDTO filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            // Identidad: nunca se puede filtrar por otro usuario.
            predicados.add(cb.equal(root.get("usuario").get("rut"), rut));

            if (filtro != null) {
                if (filtro.getDesde() != null) {
                    predicados.add(cb.greaterThanOrEqualTo(root.get("fecha"), FechasMovimiento.inicioDelDia(filtro.getDesde())));
                }
                if (filtro.getHasta() != null) {
                    // Límite superior exclusivo: así 'hasta' incluye el día completo
                    // sin depender de la hora ni de los milisegundos.
                    predicados.add(cb.lessThan(root.get("fecha"), FechasMovimiento.inicioDelDiaSiguiente(filtro.getHasta())));
                }
                if (filtro.getTipoMovimiento() != null) {
                    predicados.add(cb.equal(root.get("tipoMovimiento"), filtro.getTipoMovimiento()));
                }
                if (filtro.getCategoriaId() != null) {
                    predicados.add(cb.equal(root.get("categoria").get("id"), filtro.getCategoriaId()));
                }
                if (filtro.getMontoMin() != null) {
                    predicados.add(cb.greaterThanOrEqualTo(cb.abs(root.get("monto")), filtro.getMontoMin()));
                }
                if (filtro.getMontoMax() != null) {
                    predicados.add(cb.lessThanOrEqualTo(cb.abs(root.get("monto")), filtro.getMontoMax()));
                }
                if (filtro.getQ() != null && !filtro.getQ().trim().isEmpty()) {
                    // Busca el texto como literal: escapa \, % y _ para que no se
                    // interpreten como comodines de LIKE. El escape se declara con '\'.
                    String texto = filtro.getQ().trim().toLowerCase(Locale.ROOT)
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_");
                    predicados.add(cb.like(cb.lower(root.get("descripcion")), "%" + texto + "%", '\\'));
                }
            }

            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
