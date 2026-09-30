package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoMovimiento;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class MovimientoResponseDTO {
    private Long id;
    private BigDecimal monto;
    private String descripcion;
    private Date fecha;
    private TipoMovimiento tipoMovimiento;
    private CategoriaDTO categoria;
}
