package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoEjecucion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ejecución del límite de una categoría en un mes (T5).
 * {@code gastado} es la suma en valor absoluto de GASTO_GENERAL y PAGO_DEUDA del
 * mes en la categoría; {@code estado} se deriva de {@code porcentajeUsado}.
 */
@Data
@NoArgsConstructor
@Schema(description = "Ejecución del límite de una categoría en un mes.")
public class EjecucionCategoriaDTO {
    @Schema(description = "ID de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoriaId;

    @Schema(description = "Nombre de la categoría.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoria;

    @Schema(description = "Límite mensual configurado, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal limite;

    @Schema(description = "Gasto del mes en valor absoluto, con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gastado;

    @Schema(description = "Porcentaje usado del límite (0–100 o más si se excede).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeUsado;

    @Schema(description = "Estado derivado del porcentaje usado.", requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoEjecucion estado;

    public EjecucionCategoriaDTO(Long categoriaId, String categoria, BigDecimal limite, BigDecimal gastado,
                                 BigDecimal porcentajeUsado, EstadoEjecucion estado) {
        this.categoriaId = categoriaId;
        this.categoria = categoria;
        this.limite = limite;
        this.gastado = gastado;
        this.porcentajeUsado = porcentajeUsado;
        this.estado = estado;
    }
}
