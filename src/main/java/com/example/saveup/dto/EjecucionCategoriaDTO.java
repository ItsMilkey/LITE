package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoEjecucion;
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
public class EjecucionCategoriaDTO {
    private Long categoriaId;
    private String categoria;
    private BigDecimal limite;
    private BigDecimal gastado;
    private BigDecimal porcentajeUsado;
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
