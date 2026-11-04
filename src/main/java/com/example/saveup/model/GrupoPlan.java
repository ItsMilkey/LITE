package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Grupo de partidas de un usuario (T10d, sección 6). Agrupa partidas para
 * ordenar y resumir el plan; no altera ninguna regla de dinero.
 *
 * <p>{@code nombreNormalizado} es la forma canónica usada para rechazar
 * duplicados por usuario (minúsculas, sin tildes y con espacios colapsados).
 */
@Entity
@Table(name = "GRUPO_PLAN",
        uniqueConstraints = @UniqueConstraint(name = "uq_grupo_usuario_nombre",
                columnNames = {"USUARIO_ID", "NOMBRE_NORMALIZADO"}))
@Data
public class GrupoPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_GRUPO_PLAN")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @Column(name = "NOMBRE", nullable = false, length = 60)
    private String nombre;

    @Column(name = "NOMBRE_NORMALIZADO", nullable = false, length = 60)
    private String nombreNormalizado;

    @Column(name = "ORDEN", nullable = false)
    private Integer orden = 0;
}
