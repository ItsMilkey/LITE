package com.example.saveup.model;

import com.example.saveup.model.enums.EstadoDeuda;
import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Table(name = "DEUDA")
@Data
public class Deuda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DEUDA")
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 255)
    private String descripcion;

    @Column(name = "MONTO_TOTAL", nullable = false)
    private double montoTotal;

    @Column(name = "CANTIDAD_CUOTAS", nullable = false)
    private int cantidadCuotas;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoDeuda estado;

    @Column(name = "FECHA_CREACION", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = new Date();
        }
        if (this.estado == null) {
            this.estado = EstadoDeuda.PENDIENTE;
        }
    }
}