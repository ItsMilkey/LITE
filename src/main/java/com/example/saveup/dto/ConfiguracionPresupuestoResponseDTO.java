package com.example.saveup.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Configuración de presupuesto 50/30/20 expuesta por la API (T5). Sustituye a
 * la entidad JPA en la respuesta para no filtrar el modelo de persistencia.
 */
@Data
@NoArgsConstructor
public class ConfiguracionPresupuestoResponseDTO {
    private Long id;
    private String usuarioRut;
    private BigDecimal porcentajeNecesidades;
    private BigDecimal porcentajeDeseos;
    private BigDecimal porcentajeAhorro;
    private Boolean activo;
    private Boolean automatizarAhorroEnMetas;

    public ConfiguracionPresupuestoResponseDTO(Long id, String usuarioRut, BigDecimal porcentajeNecesidades,
                                               BigDecimal porcentajeDeseos, BigDecimal porcentajeAhorro,
                                               Boolean activo, Boolean automatizarAhorroEnMetas) {
        this.id = id;
        this.usuarioRut = usuarioRut;
        this.porcentajeNecesidades = porcentajeNecesidades;
        this.porcentajeDeseos = porcentajeDeseos;
        this.porcentajeAhorro = porcentajeAhorro;
        this.activo = activo;
        this.automatizarAhorroEnMetas = automatizarAhorroEnMetas;
    }
}
