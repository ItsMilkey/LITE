package com.example.saveup.repository;

import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.enums.TipoMovimiento;
import org.springframework.data.domain.Page; // <-- ¡CAMBIO!
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

import java.util.Date;
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

    /**
     * Movimientos del mes con su categoría cargada (T10c). Se usa una sola
     * consulta para el plan: el resumen y la imputación se calculan en memoria.
     */
    @Query("""
            SELECT m FROM Movimiento m
            LEFT JOIN FETCH m.categoria
            LEFT JOIN FETCH m.partida
            LEFT JOIN FETCH m.deuda
            WHERE m.usuario.rut = :rut
              AND m.fecha >= :desde
              AND m.fecha < :hastaExclusivo
            """)
    List<Movimiento> findDelMesConCategoria(@Param("rut") String rut,
                                            @Param("desde") Date desde,
                                            @Param("hastaExclusivo") Date hastaExclusivo);

    /** ¿La partida tiene movimientos vinculados? (T10c: bloquea cambiar la categoría). */
    long countByPartidaId(Long partidaId);

    /** Borrar una partida deja sus movimientos sin partida (SET NULL). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Movimiento m SET m.partida = NULL WHERE m.partida.id = :partidaId")
    int desvincularPartida(@Param("partidaId") Long partidaId);

    /**
     * Ingreso real de un mes (T10d): suma de INGRESO_GENERAL positivo, con el
     * mismo criterio que T5 usa para totalIncome. Una consulta por mes.
     */
    @Query("""
            SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m
            WHERE m.usuario.rut = :rut
              AND m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.INGRESO_GENERAL
              AND m.monto > 0
              AND m.fecha >= :desde
              AND m.fecha < :hastaExclusivo
            """)
    BigDecimal ingresoRealDelMes(@Param("rut") String rut,
                                 @Param("desde") Date desde,
                                 @Param("hastaExclusivo") Date hastaExclusivo);

    /**
     * Gasto por categoría de un mes (T10d): Σ |monto| de GASTO_GENERAL y
     * PAGO_DEUDA agrupado por categoría con su tipo por defecto. El tipo
     * efectivo se resuelve en memoria con las preferencias. Una consulta.
     */
    @Query("""
            SELECT c.id, c.tipoPresupuesto, SUM(ABS(m.monto)) FROM Movimiento m
            JOIN m.categoria c
            WHERE m.usuario.rut = :rut
              AND (m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.GASTO_GENERAL
                   OR m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.PAGO_DEUDA)
              AND m.fecha >= :desde
              AND m.fecha < :hastaExclusivo
            GROUP BY c.id, c.tipoPresupuesto
            """)
    List<Object[]> gastoPorCategoriaDelMes(@Param("rut") String rut,
                                           @Param("desde") Date desde,
                                           @Param("hastaExclusivo") Date hastaExclusivo);

    /** Fecha del primer movimiento del usuario; null si todavía no tiene ninguno. */
    @Query("SELECT MIN(m.fecha) FROM Movimiento m WHERE m.usuario.rut = :rut")
    Date primerMovimiento(@Param("rut") String rut);

    /**
     * Gasto real por categoría y mes para las referencias históricas (T10d,
     * sección 8): Σ |monto| de GASTO_GENERAL y PAGO_DEUDA. Una sola consulta
     * agregada por categoría y mes sobre la ventana pedida.
     */
    @Query(value = """
            SELECT c.id_categoria, EXTRACT(YEAR FROM m.fecha), EXTRACT(MONTH FROM m.fecha),
                   SUM(ABS(m.monto))
            FROM movimiento m
            JOIN categoria c ON c.id_categoria = m.categoria_id
            WHERE m.usuario_id = :rut
              AND m.tipo_movimiento IN ('GASTO_GENERAL', 'PAGO_DEUDA')
              AND m.fecha >= :desde
              AND m.fecha < :hastaExclusivo
            GROUP BY c.id_categoria, EXTRACT(YEAR FROM m.fecha), EXTRACT(MONTH FROM m.fecha)
            """, nativeQuery = true)
    List<Object[]> gastoPorCategoriaYMes(@Param("rut") String rut,
                                         @Param("desde") Date desde,
                                         @Param("hastaExclusivo") Date hastaExclusivo);
}
