package com.example.saveup.dto;

import com.example.saveup.model.Movimiento;
import com.example.saveup.model.enums.TipoMovimiento;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "Movimiento registrado. El signo del monto refleja si suma o resta al saldo.")
public class MovimientoResponseDTO {
    @Schema(description = "Identificador del movimiento.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Monto con signo y escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monto;

    @Schema(description = "Descripción del movimiento.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String descripcion;

    @Schema(description = "Fecha del movimiento en ISO-8601 UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Date fecha;

    @Schema(description = "Tipo de movimiento.", requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoMovimiento tipoMovimiento;

    @Schema(description = "Categoría asociada; puede ser null en movimientos históricos.")
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
