package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Plan de un mes de calendario (T10c). Un usuario tiene como máximo un plan
 * por mes. El plan se crea con el primer cambio (ingreso de referencia o
 * partida) y no se cierra nunca: todos los meses son editables (DC11).
 *
 * <p>{@code mes} siempre es el día 1; el CHECK {@code chk_plan_mes_dia1} de la
 * migración V14 lo garantiza.
 *
 * <p>Los timestamps son {@link Instant} y se guardan en columnas
 * {@code TIMESTAMP} en UTC ({@code hibernate.jdbc.time_zone=UTC}), igual que el
 * resto de la base: la entidad no mezcla {@code java.util.Date} con
 * {@code java.time} (regla 20-java-spring).
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

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "CREADO_EN", nullable = false, updatable = false)
    private Instant creadoEn;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "ACTUALIZADO_EN", nullable = false)
    private Instant actualizadoEn;

    /** Constructor sin argumentos requerido por JPA/Hibernate. */
    public PlanMensual() {
    }

    @PrePersist
    protected void onCreate() {
        Instant ahora = Instant.now();
        if (this.creadoEn == null) {
            this.creadoEn = ahora;
        }
        if (this.actualizadoEn == null) {
            this.actualizadoEn = ahora;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = Instant.now();
    }
}
