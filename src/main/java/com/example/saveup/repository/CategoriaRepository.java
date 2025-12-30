package com.example.saveup.repository;

import com.example.saveup.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByNombre(String nombre);

    java.util.List<Categoria> findByTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto);
}