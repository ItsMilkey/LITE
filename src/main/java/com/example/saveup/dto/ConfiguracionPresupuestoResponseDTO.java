package com.example.saveup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Configuración de presupuesto 50/30/20 expuesta por la API (T5). Sustituye a
 * la entidad JPA en la respuesta para no filtrar el modelo de persistencia.
 */
@Data
@NoArgsConstructor
@Schema(description = "Configuración 50/30/20 guardada por el usuario.")
public class ConfiguracionPresupuestoResponseDTO {
    @Schema(description = "Identificador de la configuración.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "RUT del usuario dueño de la configuración.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String usuarioRut;

    @Schema(description = "Porcentaje para necesidades (0–100).", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeNecesidades;

    @Schema(description = "Porcentaje para deseos (0–100).", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeDeseos;

    @Schema(description = "Porcentaje para ahorro (0–100).", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal porcentajeAhorro;

    @Schema(description = "Indica si la configuración está activa.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean activo;

    @Schema(description = "Indica si el ahorro se ejecuta automáticamente en metas.",
            requiredMode = Schema.RequiredMode.REQUIRED)
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
