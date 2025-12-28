package com.example.saveup.repository;

import com.example.saveup.model.AsignacionMetaPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AsignacionMetaPresupuestoRepository extends JpaRepository<AsignacionMetaPresupuesto, Long> {
    List<AsignacionMetaPresupuesto> findByConfiguracionId(Long configuracionId);
}
