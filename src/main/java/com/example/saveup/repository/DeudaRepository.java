package com.example.saveup.repository;

import com.example.saveup.model.Deuda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DeudaRepository extends JpaRepository<Deuda, Long> {

    // Encuentra todas las deudas de un usuario
    List<Deuda> findByUsuarioRut(String rut);

    // Consulta clave para calcular el total pagado por una deuda específica
    @Query("SELECT COALESCE(SUM(m.monto), 0.0) FROM Movimiento m WHERE m.deuda.id = :deudaId AND m.tipoMovimiento = 'PAGO_DEUDA'")
    Double findTotalPagadoPorDeuda(@Param("deudaId") Long deudaId);

    // Consulta para contar las cuotas pagadas
    @Query("SELECT COUNT(m) FROM Movimiento m WHERE m.deuda.id = :deudaId AND m.tipoMovimiento = 'PAGO_DEUDA'")
    Integer countPagosPorDeuda(@Param("deudaId") Long deudaId);
}