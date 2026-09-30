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

    /**
     * Bandera para desacoplar el presupuesto como guía visual vs ejecutor de transacciones reales.
     * - true: Smart-Split genera sub-movimientos ABONO_META automáticos afectando saldo y metas.
     * - false (por defecto): El presupuesto solo actúa como guía visual 50/30/20 y el dinero
     *   permanece 100% líquido y disponible en el saldo principal del usuario.
     */
    @Column(name = "AUTOMATIZAR_AHORRO_EN_METAS", nullable = false)
    private Boolean automatizarAhorroEnMetas = false;

    @PrePersist
    @PreUpdate
    protected void onPersistOrUpdate() {
        if (this.activo == null) {
            this.activo = true;
        }
        if (this.automatizarAhorroEnMetas == null) {
            this.automatizarAhorroEnMetas = false;
        }
    }
}
