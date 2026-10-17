package com.example.saveup.model;

import com.example.saveup.model.enums.TipoMovimiento;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "MOVIMIENTO")
@Data
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

    /**
     * Movimiento que originó a este (T4). Smart-Split deja aquí el
     * INGRESO_GENERAL cuyos abonos automáticos son este movimiento. Es null en
     * todo movimiento que no provenga de Smart-Split, incluido el histórico.
     * Inmutable tras la inserción: el vínculo no se reasigna.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MOVIMIENTO_ORIGEN_ID", updatable = false)
    private Movimiento movimientoOrigen;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public Movimiento() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.fecha == null) {
            this.fecha = new Date();
        }
    }
}