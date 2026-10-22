package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoMovimiento;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filtros opcionales de GET /api/v1/movimientos/paginados/me (T4). Todos son
 * opcionales: sin parámetros el endpoint se comporta como hoy.
 *
 * <p>{@code desde} y {@code hasta} son fechas ISO (yyyy-MM-dd) e inclusivas: el
 * rango cubre el día completo. {@code montoMin}/{@code montoMax} comparan contra
 * la magnitud (valor absoluto) del monto, de modo que un ingreso de +5000 y un
 * gasto de -5000 caen en el mismo rango.
 */
@Data
public class MovimientoFiltroDTO {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Fecha inicial inclusiva en formato yyyy-MM-dd.")
    private LocalDate desde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Fecha final inclusiva en formato yyyy-MM-dd.")
    private LocalDate hasta;

    @Schema(description = "Tipo de movimiento a filtrar.")
    private TipoMovimiento tipoMovimiento;

    @Schema(description = "ID de categoría a filtrar.")
    private Long categoriaId;

    @Schema(description = "Monto mínimo comparado contra el valor absoluto.")
    private BigDecimal montoMin;

    @Schema(description = "Monto máximo comparado contra el valor absoluto.")
    private BigDecimal montoMax;

    /** Texto a buscar en la descripción, sin distinguir mayúsculas. */
    @Schema(description = "Texto a buscar en la descripción, sin distinguir mayúsculas.")
    private String q;
}
