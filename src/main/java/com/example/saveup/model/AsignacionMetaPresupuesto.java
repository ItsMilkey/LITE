package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    @Column(name = "PORCENTAJE_ASIGNACION", nullable = false)
    private Double porcentajeAsignacion;
}
