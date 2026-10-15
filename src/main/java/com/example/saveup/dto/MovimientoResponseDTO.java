package com.example.saveup.dto;

import com.example.saveup.model.Movimiento;
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

    /** Construye el DTO a partir de la entidad guardada (compartido por todos los productores de movimientos). */
    public static MovimientoResponseDTO from(Movimiento movimiento) {
        MovimientoResponseDTO dto = new MovimientoResponseDTO();
        dto.setId(movimiento.getId());
        dto.setMonto(movimiento.getMonto());
        dto.setDescripcion(movimiento.getDescripcion());
        dto.setFecha(movimiento.getFecha());
        dto.setTipoMovimiento(movimiento.getTipoMovimiento());

        if (movimiento.getCategoria() != null) {
            CategoriaDTO categoriaDTO = new CategoriaDTO();
            categoriaDTO.setId(movimiento.getCategoria().getId());
            categoriaDTO.setNombre(movimiento.getCategoria().getNombre());
            categoriaDTO.setIconId(movimiento.getCategoria().getIconId());
            categoriaDTO.setColorHex(movimiento.getCategoria().getColorHex());
            categoriaDTO.setTipoPresupuesto(movimiento.getCategoria().getTipoPresupuesto());
            dto.setCategoria(categoriaDTO);
        }

        return dto;
    }
}
