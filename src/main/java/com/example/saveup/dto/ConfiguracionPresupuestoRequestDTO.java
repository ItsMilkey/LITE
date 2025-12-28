package com.example.saveup.dto;

import lombok.Data;
import java.util.List;

@Data
public class ConfiguracionPresupuestoRequestDTO {
    private Double porcentajeNecesidades;
    private Double porcentajeDeseos;
    private Double porcentajeAhorro;
    private Boolean activo;
    private List<AsignacionPresupuestoDTO> asignaciones;
}
