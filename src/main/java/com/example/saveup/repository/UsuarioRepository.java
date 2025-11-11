package com.example.saveup.repository;

import com.example.saveup.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    // Método para buscar por email de forma eficiente. Clave para el login.
    Optional<Usuario> findByEmail(String email);

    // Método para verificar si un email ya existe.
    boolean existsByEmail(String email);
}
