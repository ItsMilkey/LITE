package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ASIGNACION_META_PRESUPUESTO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionMetaPresupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ASIGNACION")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONFIGURACION_ID", nullable = false)
    private ConfiguracionPresupuesto configuracion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "META_ID", nullable = false)
    private MetaAhorro meta;

    /**
     * Fraccion porcentual del total de ahorro a derivar a esta meta (0.0 - 100.0).
     * Precision 5, escala 2 (ej: 99.99%).
     */
    @Column(name = "PORCENTAJE_ASIGNACION", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeAsignacion;
}
