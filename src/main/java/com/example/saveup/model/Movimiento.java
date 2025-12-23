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
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Ideal para IDs numéricos autoincrementales
    @Column(name = "ID_MOVIMIENTO")
    private Long id;

    @Column(name = "MONTO", nullable = false)
    private double monto; // Positivo para ingresos, negativo para gastos

    @Column(name = "DESCRIPCION", nullable = false, length = 100)
    private String descripcion;

    @Column(name = "FECHA", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fecha;

    @Enumerated(EnumType.STRING) // Le dice a JPA que guarde el nombre del enum ("GASTO_GENERAL")
    @Column(name = "TIPO_MOVIMIENTO", nullable = false)
    private TipoMovimiento tipoMovimiento;

    // --- Relación Clave ---
    // Muchos movimientos pueden pertenecer a Un usuario.
    @ManyToOne(fetch = FetchType.LAZY) // LAZY para no cargar el usuario a menos que se necesite
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    // Relación Deuda
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEUDA_ID") // Será FK a la tabla DEUDA
    private Deuda deuda;

    // Relación Categoría
    @ManyToOne(fetch = FetchType.EAGER) // EAGER para que venga con los datos básicos
    @JoinColumn(name = "CATEGORIA_ID")
    private Categoria categoria;

    /*
    // --- Relaciones Futuras (Descomentar cuando creemos Deuda y MetaAhorro) ---
     @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "META_ID") // Será FK a la tabla META_AHORRO
    private MetaAhorro metaAhorro;
    */

    @PrePersist
    protected void onCreate() {
        if (this.fecha == null) {
            this.fecha = new Date();
        }
    }
}