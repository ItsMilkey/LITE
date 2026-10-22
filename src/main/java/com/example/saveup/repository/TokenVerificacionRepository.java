package com.example.saveup.repository;

import com.example.saveup.model.TokenVerificacion;
import com.example.saveup.model.enums.TipoVerificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, Long> {

    // Búsqueda por hash: única vía para localizar un token (el secreto no se persiste).
    Optional<TokenVerificacion> findByTokenHash(String tokenHash);

    // Tokens vigentes de un usuario de un tipo dado: al pedir uno nuevo, estos anteriores se invalidan.
    List<TokenVerificacion> findByUsuario_RutAndTipoAndUsadoFalse(String rut, TipoVerificacion tipo);

    // Inventario completo de tokens de un usuario (soporte / tests de integración).
    List<TokenVerificacion> findByUsuario_Rut(String rut);
}
