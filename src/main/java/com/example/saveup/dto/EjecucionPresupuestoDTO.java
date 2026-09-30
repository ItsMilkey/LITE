package com.example.saveup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EjecucionPresupuestoDTO {
    private BigDecimal presupuestoNecesidades; // (Ingresos * % Necesidades)
    private BigDecimal gastoNecesidades; // Suma gastos tipo NECESIDAD

    private BigDecimal presupuestoDeseos; // (Ingresos * % Deseos)
    private BigDecimal gastoDeseos; // Suma gastos tipo DESEO

    private BigDecimal presupuestoAhorro; // (Ingresos * % Ahorro)
    private BigDecimal ahorroRealizado; // Suma de egresos hacia metas (ABONO_META)

    // Metadata
    private BigDecimal totalIngresos;
    private BigDecimal porcentajeNecesidadesConfigurado;
    private BigDecimal porcentajeDeseosConfigurado;
    private BigDecimal porcentajeAhorroConfigurado;
    private Boolean automatizarAhorroEnMetas;
}
