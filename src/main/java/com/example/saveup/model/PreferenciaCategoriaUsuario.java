package com.example.saveup.model;

import com.example.saveup.model.enums.TipoPresupuesto;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import lombok.Data;

/**
 * Preferencia de tipo de una categoría para un usuario (T10d, sección 3).
 * <p>
 * El tipo efectivo de una categoría es esta preferencia si existe; si no,
 * el tipo por defecto de la categoría. Solo se aceptan NECESIDAD y DESEO:
 * las categorías con tipo por defecto SUELDO, AHORRO u OTROS no son
 * reclasificables, y el valor guardado tampoco puede ser AHORRO ni OTROS.
 */
@Entity
@Table(name = "PREFERENCIA_CATEGORIA_USUARIO",
        uniqueConstraints = @UniqueConstraint(name = "uq_pref_usuario_categoria",
                columnNames = {"USUARIO_ID", "CATEGORIA_ID"}))
@Data
public class PreferenciaCategoriaUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PREFERENCIA")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "CATEGORIA_ID", nullable = false)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_PRESUPUESTO", nullable = false, length = 20)
    private TipoPresupuesto tipoPresupuesto;
}
