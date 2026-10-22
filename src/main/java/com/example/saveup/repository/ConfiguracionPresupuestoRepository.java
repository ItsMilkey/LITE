package com.example.saveup.repository;

import com.example.saveup.model.ConfiguracionPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ConfiguracionPresupuestoRepository extends JpaRepository<ConfiguracionPresupuesto, Long> {
    Optional<ConfiguracionPresupuesto> findByUsuarioRut(String usuarioRut);

    // T9.2: el presupuesto 50/30/20 muere con la cuenta.
    void deleteByUsuarioRut(String usuarioRut);
}
