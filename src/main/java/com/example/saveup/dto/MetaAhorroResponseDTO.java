package com.example.saveup.dto;

import lombok.Data;
import java.util.Date;

@Data
public class MetaAhorroResponseDTO {
    private Long id;
    private String nombre;
    private Double montoObjetivo;
    private Date fechaLimite;

    // Campo calculado en el servicio (ahora persistido)
    private double montoActual;
}