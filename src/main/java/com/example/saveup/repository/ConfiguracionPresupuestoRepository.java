package com.example.saveup.repository;

import com.example.saveup.model.ConfiguracionPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ConfiguracionPresupuestoRepository extends JpaRepository<ConfiguracionPresupuesto, Long> {
    Optional<ConfiguracionPresupuesto> findByUsuarioRut(String usuarioRut);
}
