package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * Plan de un mes de calendario (T10c). Un usuario tiene como máximo un plan
 * por mes. El plan se crea con el primer cambio (ingreso de referencia o
 * partida) y no se cierra nunca: todos los meses son editables (DC11).
 *
 * <p>{@code mes} siempre es el día 1; el CHECK {@code chk_plan_mes_dia1} de la
 * migración V14 lo garantiza.
 */
@Entity
@Table(name = "PLAN_MENSUAL",
        uniqueConstraints = @UniqueConstraint(name = "uq_plan_usuario_mes",
                columnNames = {"USUARIO_ID", "MES"}))
@Data
public class PlanMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PLAN_MENSUAL")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    /** Primer día del mes que representa el plan. */
    @Column(name = "MES", nullable = false)
    private LocalDate mes;

    /** Ingreso de referencia opcional; null significa "no definido". */
    @Column(name = "INGRESO_REFERENCIA", precision = 19, scale = 2)
    private BigDecimal ingresoReferencia;

    @Column(name = "CREADO_EN", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date creadoEn;

    @Column(name = "ACTUALIZADO_EN", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date actualizadoEn;

    /** Constructor sin argumentos requerido por JPA/Hibernate. */
    public PlanMensual() {
    }

    @PrePersist
    protected void onCreate() {
        Date ahora = new Date();
        if (this.creadoEn == null) {
            this.creadoEn = ahora;
        }
        if (this.actualizadoEn == null) {
            this.actualizadoEn = ahora;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = new Date();
    }
}
