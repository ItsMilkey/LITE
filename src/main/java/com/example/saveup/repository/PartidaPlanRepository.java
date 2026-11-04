package com.example.saveup.repository;

import com.example.saveup.model.PartidaPlan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartidaPlanRepository extends JpaRepository<PartidaPlan, Long> {

    /**
     * Partidas de un plan en orden de presentación. El join fetch evita el
     * N+1 de la categoría: el plan completo se arma con un número fijo de
     * consultas (T10c, C56).
     */
    @Query("""
            SELECT p FROM PartidaPlan p
            JOIN FETCH p.categoria
            LEFT JOIN FETCH p.grupo
            LEFT JOIN FETCH p.deuda
            WHERE p.plan.id = :planId
            ORDER BY p.orden ASC, p.id ASC
            """)
    List<PartidaPlan> buscarDelPlan(@Param("planId") Long planId);

    /**
     * Partida con su plan, el usuario dueño del plan y la categoría cargados:
     * una sola consulta para decidir 403/404 sin viajar de nuevo a la BD.
     */
    @Query("""
            SELECT p FROM PartidaPlan p
            JOIN FETCH p.categoria
            LEFT JOIN FETCH p.grupo
            LEFT JOIN FETCH p.deuda
            JOIN FETCH p.plan pl
            JOIN FETCH pl.usuario
            WHERE p.id = :id
            """)
    Optional<PartidaPlan> buscarConContexto(@Param("id") Long id);

    long countByPlanId(Long planId);

    boolean existsByPlanIdAndNombreNormalizado(Long planId, String nombreNormalizado);

    boolean existsByPlanIdAndNombreNormalizadoAndIdNot(Long planId, String nombreNormalizado, Long id);

    /** T10d: una sola partida por deuda dentro del mismo plan. */
    boolean existsByPlanIdAndDeudaId(Long planId, Long deudaId);

    boolean existsByPlanIdAndDeudaIdAndIdNot(Long planId, Long deudaId, Long id);

    /** Borrar un grupo deja sus partidas sin grupo (ON DELETE SET NULL). */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PartidaPlan p SET p.grupo = NULL WHERE p.grupo.id = :grupoId")
    int desvincularGrupo(@Param("grupoId") Long grupoId);

    @Query("SELECT COALESCE(MAX(p.orden), 0) FROM PartidaPlan p WHERE p.plan.id = :planId")
    int maxOrden(@Param("planId") Long planId);

    /**
     * Historial del sugeridor (T10c, sección 10): la partida más reciente del
     * usuario con ese nombre normalizado. Indexado por idx_partida_nombre.
     */
    @Query("""
            SELECT p FROM PartidaPlan p
            JOIN FETCH p.categoria
            WHERE p.nombreNormalizado = :nombreNormalizado
              AND p.plan.usuario.rut = :rut
            ORDER BY p.actualizadoEn DESC, p.id DESC
            """)
    List<PartidaPlan> buscarHistorial(@Param("rut") String rut,
                                      @Param("nombreNormalizado") String nombreNormalizado,
                                      Pageable pageable);

    /** T9.2/T10c: las partidas se borran antes que sus planes. */
    void deleteByPlanUsuarioRut(String rut);
}
