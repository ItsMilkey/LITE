package com.example.saveup.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class MetaAhorroResponseDTO {
    private Long id;
    private String nombre;
    private BigDecimal montoObjetivo;
    private Date fechaLimite;

    // Campo calculado en el servicio (ahora persistido)
    private BigDecimal montoActual;
}