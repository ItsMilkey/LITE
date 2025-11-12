package com.example.saveup.repository;

import com.example.saveup.model.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    /**
     * Busca todos los movimientos asociados a un RUT de usuario específico.
     * Spring Data JPA infiere la consulta a partir del nombre del método.
     * Ordena los resultados por fecha en orden descendente para mostrar los más recientes primero.
     *
     * @param rut El RUT del usuario.
     * @return Una lista de movimientos.
     */
    List<Movimiento> findByUsuarioRutOrderByFechaDesc(String rut);

    /**
     * Calcula la suma de todos los montos de los movimientos para un usuario específico.
     * Esta es la consulta clave para obtener el saldo actual del usuario.
     * Usamos una consulta JPQL personalizada con la anotación @Query.
     *
     * @param rut El RUT del usuario.
     * @return Un Double que representa el saldo total. Puede ser null si no hay movimientos.
     */
    @Query("SELECT SUM(m.monto) FROM Movimiento m WHERE m.usuario.rut = :rut")
    Double findSaldoByUsuarioRut(@Param("rut") String rut);

}