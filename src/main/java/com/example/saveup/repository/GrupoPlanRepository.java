package com.example.saveup.repository;

import com.example.saveup.model.GrupoPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrupoPlanRepository extends JpaRepository<GrupoPlan, Long> {

    /** Grupos del usuario en orden de presentación (orden y luego id). */
    List<GrupoPlan> findByUsuarioRutOrderByOrdenAscIdAsc(String rut);

    long countByUsuarioRut(String rut);

    boolean existsByUsuarioRutAndNombreNormalizado(String rut, String nombreNormalizado);

    boolean existsByUsuarioRutAndNombreNormalizadoAndIdNot(String rut, String nombreNormalizado, Long id);

    /** T9.2/T10d: los grupos se borran con la cuenta. */
    void deleteByUsuarioRut(String rut);
}
