package com.example.saveup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Configuración de presupuesto 50/30/20 expuesta por la API (T5). Sustituye a
 * la entidad JPA en la respuesta para no filtrar el modelo de persistencia.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionPresupuestoResponseDTO {
    private Long id;
    private String usuarioRut;
    private BigDecimal porcentajeNecesidades;
    private BigDecimal porcentajeDeseos;
    private BigDecimal porcentajeAhorro;
    private Boolean activo;
    private Boolean automatizarAhorroEnMetas;
}
