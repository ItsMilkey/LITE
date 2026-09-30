package com.example.saveup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EjecucionPresupuestoDTO {
    private Double presupuestoNecesidades; // (Ingresos * % Necesidades)
    private Double gastoNecesidades; // Suma gastos tipo NECESIDAD

    private Double presupuestoDeseos; // (Ingresos * % Deseos)
    private Double gastoDeseos; // Suma gastos tipo DESEO

    private Double presupuestoAhorro; // (Ingresos * % Ahorro)
    private Double ahorroRealizado; // Suma de egresos hacia metas (ABONO_META)

    // Metadata
    private Double totalIngresos;
    private Double porcentajeNecesidadesConfigurado;
    private Double porcentajeDeseosConfigurado;
    private Double porcentajeAhorroConfigurado;
    private Boolean automatizarAhorroEnMetas;
}
