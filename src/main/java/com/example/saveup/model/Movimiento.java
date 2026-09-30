package com.example.saveup.model;

import com.example.saveup.model.enums.TipoMovimiento;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
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

    /**
     * Monto de la transaccion. Positivo para ingresos/retiros; negativo para
     * gastos, pagos de deudas y abonos a metas. Escala fija de 2 decimales
     * con redondeo HALF_EVEN (bancario) para evitar imprecisiones IEEE 754.
     */
    @Column(name = "MONTO", nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

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