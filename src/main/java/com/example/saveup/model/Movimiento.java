package com.example.saveup.model;

import com.example.saveup.model.enums.TipoMovimiento;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

@Entity
@Table(name = "MOVIMIENTO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MOVIMIENTO")
    private Long id;

    @Column(name = "MONTO", nullable = false)
    private double monto;

    @Column(name = "DESCRIPCION", nullable = false, length = 100)
    private String descripcion;

    @Column(name = "FECHA", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_MOVIMIENTO", nullable = false)
    private TipoMovimiento tipoMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEUDA_ID")
    private Deuda deuda;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CATEGORIA_ID")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "META_ID")
    private MetaAhorro metaAhorro;

    @PrePersist
    protected void onCreate() {
        if (this.fecha == null) {
            this.fecha = new Date();
        }
    }
}