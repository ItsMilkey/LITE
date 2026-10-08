package com.example.saveup.repository;

import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.model.Deuda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeudaRepository extends JpaRepository<Deuda, Long> {

    // Encuentra todas las deudas de un usuario
    List<Deuda> findByUsuarioRut(String rut);

    // Consulta optimizada con proyección JPQL para evitar N+1 queries al listar deudas con agregaciones
    @Query("SELECT new com.example.saveup.dto.DeudaResponseDTO(" +
           "d.id, d.nombre, d.descripcion, d.montoTotal, d.cantidadCuotas, d.estado, d.fechaCreacion, " +
           "COALESCE(SUM(m.monto), 0), COUNT(m), " +
           "d.tipoDeuda, d.modalidadCalculo, d.montoCapital, d.tasaMensual, d.valorCuota, d.gastosIniciales, d.costoAdicionalPorCuota, " +
           "d.costoTotalCredito, d.cargaAnualEquivalente, d.fechaPrimeraCuota, d.cuotasPagadasPrevias, d.montoPagadoPrevio) " +
           "FROM Deuda d LEFT JOIN Movimiento m ON m.deuda.id = d.id AND m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.PAGO_DEUDA " +
           "WHERE d.usuario.rut = :rut " +
           "GROUP BY d.id, d.nombre, d.descripcion, d.montoTotal, d.cantidadCuotas, d.estado, d.fechaCreacion, " +
           "d.tipoDeuda, d.modalidadCalculo, d.montoCapital, d.tasaMensual, d.valorCuota, d.gastosIniciales, d.costoAdicionalPorCuota, " +
           "d.costoTotalCredito, d.cargaAnualEquivalente, d.fechaPrimeraCuota, d.cuotasPagadasPrevias, d.montoPagadoPrevio")
    List<DeudaResponseDTO> findDeudasDTOByUsuarioRut(@Param("rut") String rut);

    // Consulta optimizada para una deuda específica
    @Query("SELECT new com.example.saveup.dto.DeudaResponseDTO(" +
           "d.id, d.nombre, d.descripcion, d.montoTotal, d.cantidadCuotas, d.estado, d.fechaCreacion, " +
           "COALESCE(SUM(m.monto), 0), COUNT(m), " +
           "d.tipoDeuda, d.modalidadCalculo, d.montoCapital, d.tasaMensual, d.valorCuota, d.gastosIniciales, d.costoAdicionalPorCuota, " +
           "d.costoTotalCredito, d.cargaAnualEquivalente, d.fechaPrimeraCuota, d.cuotasPagadasPrevias, d.montoPagadoPrevio) " +
           "FROM Deuda d LEFT JOIN Movimiento m ON m.deuda.id = d.id AND m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.PAGO_DEUDA " +
           "WHERE d.id = :deudaId " +
           "GROUP BY d.id, d.nombre, d.descripcion, d.montoTotal, d.cantidadCuotas, d.estado, d.fechaCreacion, " +
           "d.tipoDeuda, d.modalidadCalculo, d.montoCapital, d.tasaMensual, d.valorCuota, d.gastosIniciales, d.costoAdicionalPorCuota, " +
           "d.costoTotalCredito, d.cargaAnualEquivalente, d.fechaPrimeraCuota, d.cuotasPagadasPrevias, d.montoPagadoPrevio")
    Optional<DeudaResponseDTO> findDeudaDTOById(@Param("deudaId") Long deudaId);

    // Consulta para calcular el total pagado por una deuda específica
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.deuda.id = :deudaId AND m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.PAGO_DEUDA")
    java.math.BigDecimal findTotalPagadoPorDeuda(@Param("deudaId") Long deudaId);

    // Consulta para contar las cuotas pagadas
    @Query("SELECT COUNT(m) FROM Movimiento m WHERE m.deuda.id = :deudaId AND m.tipoMovimiento = com.example.saveup.model.enums.TipoMovimiento.PAGO_DEUDA")
    Integer countPagosPorDeuda(@Param("deudaId") Long deudaId);
}