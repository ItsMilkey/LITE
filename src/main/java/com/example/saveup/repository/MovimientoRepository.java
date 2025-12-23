package com.example.saveup.repository;import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import org.springframework.data.domain.Page; // <-- ¡CAMBIO!
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut);
    
    // ¡CAMBIO IMPORTANTE! Ahora devuelve un objeto Page que contiene la lista y metadatos.
    Page<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut, Pageable pageable);

    @Query("SELECT SUM(m.monto) FROM Movimiento m WHERE m.usuario.rut = :rut")
    Double findSaldoByUsuarioRut(@Param("rut") String rut);
    List<Movimiento> findByDeuda(Deuda deuda);

}