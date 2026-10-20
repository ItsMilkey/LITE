package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "META_AHORRO")
@Data
public class MetaAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_META")
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    // Nullable para permitir la meta por defecto "Ahorros" que no tiene un objetivo
    // fijo.
    @Column(name = "MONTO_OBJETIVO", precision = 19, scale = 2)
    private BigDecimal montoObjetivo;

    @Column(name = "MONTO_ACTUAL", precision = 19, scale = 2) // Nullable to support existing data
    private BigDecimal montoActual = BigDecimal.ZERO;

    // Nullable para la meta por defecto.
    @Column(name = "FECHA_LIMITE")
    private LocalDate fechaLimite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public MetaAhorro() {
    }

    public BigDecimal getMontoActual() {
        return montoActual != null ? montoActual : BigDecimal.ZERO;
    }
}