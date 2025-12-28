package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "CONFIGURACION_PRESUPUESTO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionPresupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CONFIGURACION")
    private Long id;

    @OneToOne
    @JoinColumn(name = "USUARIO_ID", referencedColumnName = "ID_USUARIO", nullable = false)
    private Usuario usuario;

    @Column(name = "PORCENTAJE_NECESIDADES", nullable = false)
    private Double porcentajeNecesidades;

    @Column(name = "PORCENTAJE_DESEOS", nullable = false)
    private Double porcentajeDeseos;

    @Column(name = "PORCENTAJE_AHORRO", nullable = false)
    private Double porcentajeAhorro;

    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo = true;
}
