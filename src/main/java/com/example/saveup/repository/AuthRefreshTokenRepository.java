package com.example.saveup.repository;

import com.example.saveup.model.AuthRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

    // Búsqueda por hash: única vía para localizar un token (el secreto no se persiste).
    Optional<AuthRefreshToken> findByTokenHash(String tokenHash);

    // Tokens activos de un usuario (inventario de sesiones para revocación masiva).
    List<AuthRefreshToken> findByUsuario_RutAndFechaRevocacionIsNull(String rut);

    // T9.2: la eliminación de la cuenta borra todas sus sesiones.
    void deleteByUsuario_Rut(String rut);

    /**
     * Marca todas las sesiones activas del usuario como revocadas (no las borra:
     * la fila revocada queda como evidencia de detección de reuso).
     *
     * @return La cantidad de tokens revocados.
     */
    @Modifying
    @Query("UPDATE AuthRefreshToken t SET t.fechaRevocacion = :ahora "
            + "WHERE t.usuario.rut = :rut AND t.fechaRevocacion IS NULL")
    int revocarActivasDeUsuario(@Param("rut") String rut, @Param("ahora") Date ahora);
}
