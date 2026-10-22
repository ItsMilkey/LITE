package com.example.saveup.repository;

import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.enums.TipoMovimiento;
import org.springframework.data.domain.Page; // <-- ¡CAMBIO!
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long>,
        JpaSpecificationExecutor<Movimiento> {

    List<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut);

    // T9.2: borra todos los movimientos del usuario (deuda, metas y saldos mueren con la cuenta).
    void deleteByUsuarioRut(String rut);

    // ¡CAMBIO IMPORTANTE! Ahora devuelve un objeto Page que contiene la lista y
    // metadatos.
    Page<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut, Pageable pageable);

    @Query("SELECT SUM(m.monto) FROM Movimiento m WHERE m.usuario.rut = :rut")
    java.math.BigDecimal findSaldoByUsuarioRut(@Param("rut") String rut);

    List<Movimiento> findByDeuda(Deuda deuda);

    /**
     * Historial de pagos de una deuda en orden de imputación (fecha ascendente,
     * id como desempate). Una sola consulta por endpoint: nada de N+1.
     */
    List<Movimiento> findByDeudaIdAndTipoMovimientoOrderByFechaAscIdAsc(Long deudaId, TipoMovimiento tipoMovimiento);

    List<Movimiento> findByMetaAhorroId(Long metaId);

    List<Movimiento> findByUsuarioRutAndFechaBetween(String rut, java.util.Date start, java.util.Date end);

    /**
     * Movimientos del usuario en una ventana semiabierta [desde, hastaExclusivo).
     * Se usa para un mes completo: el límite superior es el inicio del mes
     * siguiente, así se incluye el último día a cualquier hora (T5).
     */
    List<Movimiento> findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(
            String rut, java.util.Date desde, java.util.Date hastaExclusivo);

    /**
     * ¿Este movimiento es el origen de abonos automáticos de Smart-Split? (T4).
     * Si devuelve true, el ingreso tiene movimientos derivados y no se puede
     * editar ni borrar.
     */
    boolean existsByMovimientoOrigenId(Long movimientoOrigenId);
}