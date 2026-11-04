package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Partida del plan de un mes (T10c): nombre libre, categoría del catálogo y
 * monto planificado. Varias partidas pueden compartir categoría (DC3).
 *
 * <p>{@code nombreNormalizado} es la forma canónica usada para rechazar
 * duplicados dentro del plan (minúsculas, sin tildes y con espacios
 * colapsados). La imputación del gasto real se calcula al leer y no se
 * persiste.
 */
@Entity
@Table(name = "PARTIDA_PLAN",
        uniqueConstraints = @UniqueConstraint(name = "uq_partida_plan_nombre",
                columnNames = {"PLAN_ID", "NOMBRE_NORMALIZADO"}))
@Data
public class PartidaPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PARTIDA_PLAN")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLAN_ID", nullable = false)
    private PlanMensual plan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CATEGORIA_ID", nullable = false)
    private Categoria categoria;

    /**
     * Grupo de presentación (T10d). Es opcional y no cambia el cálculo.
     * ON DELETE SET NULL: borrar el grupo deja la partida sin grupo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GRUPO_ID")
    private GrupoPlan grupo;

    /**
     * Deuda vinculada (T10d): si está presente, el monto planificado se toma
     * de la cuota del mes y no se puede editar por PATCH.
     * ON DELETE SET NULL: si la deuda desaparece, la partida queda normal.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEUDA_ID")
    private Deuda deuda;

    @Column(name = "NOMBRE", nullable = false, length = 80)
    private String nombre;

    @Column(name = "NOMBRE_NORMALIZADO", nullable = false, length = 80)
    private String nombreNormalizado;

    @Column(name = "MONTO_PLANIFICADO", nullable = false, precision = 19, scale = 2)
    private BigDecimal montoPlanificado;

    @Column(name = "ORDEN", nullable = false)
    private Integer orden;

    @Column(name = "ACTUALIZADO_EN", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date actualizadoEn;

    /** Constructor sin argumentos requerido por JPA/Hibernate. */
    public PartidaPlan() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.actualizadoEn == null) {
            this.actualizadoEn = new Date();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = new Date();
    }
}
