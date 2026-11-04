package com.example.saveup.repository;

import com.example.saveup.model.PreferenciaCategoriaUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenciaCategoriaUsuarioRepository extends JpaRepository<PreferenciaCategoriaUsuario, Long> {

    /**
     * Todas las preferencias del usuario con su categoría cargada: una sola
     * consulta por solicitud; la resolución del tipo efectivo se hace en
     * memoria (T10d, sección 3).
     */
    @Query("""
            SELECT p FROM PreferenciaCategoriaUsuario p
            JOIN FETCH p.categoria
            WHERE p.usuario.rut = :rut
            """)
    List<PreferenciaCategoriaUsuario> buscarDelUsuario(@Param("rut") String rut);

    /** Preferencia del usuario para una categoría concreta (upsert de PUT). */
    Optional<PreferenciaCategoriaUsuario> findByUsuarioRutAndCategoriaId(String rut, Long categoriaId);

    /** T9.2/T10d: las preferencias mueren con la cuenta (D11). */
    void deleteByUsuarioRut(String rut);
}
