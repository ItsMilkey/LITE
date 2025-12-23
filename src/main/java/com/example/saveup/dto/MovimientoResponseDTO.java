package com.example.saveup.dto;

import com.example.saveup.model.enums.TipoMovimiento;
import lombok.Data;
import java.util.Date;
import com.example.saveup.dto.CategoriaDTO;

@Data
public class MovimientoResponseDTO {
    private Long id;
    private double monto;
    private String descripcion;
    private Date fecha;
    private TipoMovimiento tipoMovimiento;
    private CategoriaDTO categoria;
}
