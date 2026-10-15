package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoMovimiento;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filtros opcionales de GET /api/movimientos/paginados/me (T4). Todos son
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
    private LocalDate desde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hasta;

    private TipoMovimiento tipoMovimiento;

    private Long categoriaId;

    private BigDecimal montoMin;

    private BigDecimal montoMax;

    /** Texto a buscar en la descripción, sin distinguir mayúsculas. */
    private String q;
}
