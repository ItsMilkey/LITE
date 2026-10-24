package com.example.saveup.repository;

import com.example.saveup.model.DispositivoPush;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Dispositivos push del usuario (T9.6). La búsqueda por token es global a
 * propósito: un teléfono puede cambiar de cuenta y el alta lo reasigna.
 */
public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    Optional<DispositivoPush> findByToken(String token);

    List<DispositivoPush> findByUsuarioRutAndActivoTrueOrderByFechaUltimoUsoDesc(String rut);

    /**
     * Usuarios con al menos un dispositivo activo. Es el punto de partida del
     * job de recordatorios: solo tiene sentido evaluar deudas de quien puede
     * recibir la notificación.
     */
    @Query("SELECT DISTINCT d.usuario.rut FROM DispositivoPush d WHERE d.activo = true")
    List<String> findRutsConDispositivosActivos();

    void deleteByToken(String token);
}
