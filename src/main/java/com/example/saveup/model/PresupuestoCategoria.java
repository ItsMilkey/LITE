package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Límite de gasto mensual recurrente de un usuario sobre una categoría (T5).
 * El monto se reutiliza mes a mes: no está atado a un período concreto, sino a
 * la categoría. {@code limiteMensual} es siempre mayor a 0 (también en la BD).
 */
@Entity
@Table(name = "PRESUPUESTO_CATEGORIA")
@Data
public class PresupuestoCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PRESUPUESTO_CATEGORIA")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CATEGORIA_ID", nullable = false)
    private Categoria categoria;

    @Column(name = "LIMITE_MENSUAL", nullable = false, precision = 19, scale = 2)
    private BigDecimal limiteMensual;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public PresupuestoCategoria() {
    }

    public PresupuestoCategoria(Long id, Usuario usuario, Categoria categoria, BigDecimal limiteMensual) {
        this.id = id;
        this.usuario = usuario;
        this.categoria = categoria;
        this.limiteMensual = limiteMensual;
    }
}
