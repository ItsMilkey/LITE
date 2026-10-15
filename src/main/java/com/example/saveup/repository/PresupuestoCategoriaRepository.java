package com.example.saveup.repository;

import com.example.saveup.model.PresupuestoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PresupuestoCategoriaRepository extends JpaRepository<PresupuestoCategoria, Long> {

    List<PresupuestoCategoria> findByUsuarioRut(String rut);

    /**
     * Borra todos los límites del usuario. El PUT reemplaza el conjunto completo,
     * así que primero se limpian los actuales y luego se insertan los nuevos.
     */
    void deleteByUsuarioRut(String rut);
}
