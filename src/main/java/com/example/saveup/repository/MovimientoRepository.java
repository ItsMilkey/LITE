package com.example.saveup.repository;

import com.example.saveup.model.Movimiento;
import org.springframework.data.domain.Pageable; // <-- ¡NUEVA IMPORTACIÓN!
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut);
    
    // --- ¡NUEVO MÉTODO! ---
    // Este método es igual que el anterior, pero acepta un objeto Pageable
    // que nos permite especificar un límite de resultados.
    List<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut, Pageable pageable);

    @Query("SELECT SUM(m.monto) FROM Movimiento m WHERE m.usuario.rut = :rut")
    Double findSaldoByUsuarioRut(@Param("rut") String rut);

}