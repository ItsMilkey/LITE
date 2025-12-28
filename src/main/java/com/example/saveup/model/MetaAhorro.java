package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

@Entity
@Table(name = "META_AHORRO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_META")
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    // Nullable para permitir la meta por defecto "Ahorros" que no tiene un objetivo
    // fijo.
    @Column(name = "MONTO_OBJETIVO")
    private Double montoObjetivo;

    @Column(name = "MONTO_ACTUAL") // Nullable to support existing data
    private Double montoActual = 0.0;

    // Nullable para la meta por defecto.
    @Column(name = "FECHA_LIMITE")
    @Temporal(TemporalType.DATE)
    private Date fechaLimite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    public Double getMontoActual() {
        return montoActual != null ? montoActual : 0.0;
    }
}