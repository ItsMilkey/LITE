package com.example.saveup.repository;

import com.example.saveup.model.MetaAhorro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MetaAhorroRepository extends JpaRepository<MetaAhorro, Long> {

    List<MetaAhorro> findByUsuarioRut(String rut);

    @Query("SELECT COALESCE(SUM(m.monto), 0.0) FROM Movimiento m WHERE m.metaAhorro.id = :metaId")
    Double findTotalAhorradoByMetaId(@Param("metaId") Long metaId);
}