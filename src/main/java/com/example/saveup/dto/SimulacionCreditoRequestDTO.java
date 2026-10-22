package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "Condiciones de entrada para simular un crédito.")
public class SimulacionCreditoRequestDTO {

    @NotNull(message = "La modalidad es obligatoria")
    @Schema(description = "Modalidad de cálculo del crédito.", requiredMode = Schema.RequiredMode.REQUIRED)
    private ModalidadCalculo modalidad;

    @NotNull(message = "El monto de capital es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto de capital debe ser mayor a 0")
    @Digits(integer = 13, fraction = 2, message = "El monto de capital debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Capital solicitado, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoCapital;

    @Schema(description = "Cantidad de cuotas para modalidades que la requieren.")
    private Integer cantidadCuotas;

    @Schema(description = "Fecha de la primera cuota en formato yyyy-MM-dd.")
    private LocalDate fechaPrimeraCuota;

    @Schema(description = "Fecha de la última cuota en formato yyyy-MM-dd.")
    private LocalDate fechaUltimaCuota;

    /** Tasa mensual en porcentaje (0–100). Difiere de la fracción usada internamente (0–1). */
    @Schema(description = "Tasa mensual en porcentaje (1.5 = 1,5 %), no una fracción.")
    private BigDecimal tasaMensual;

    @Schema(description = "Tasa anual efectiva en porcentaje (19.56 = 19,56 %), no una fracción.")
    private BigDecimal tasaAnualEfectiva;

    @Digits(integer = 13, fraction = 2, message = "El valor de cuota debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Valor de cuota, con escala 2.")
    private BigDecimal valorCuota;

    @Digits(integer = 13, fraction = 2, message = "Los gastos iniciales deben tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Gastos iniciales, con escala 2.")
    private BigDecimal gastosIniciales;

    @Digits(integer = 13, fraction = 2, message = "El costo adicional por cuota debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Costo adicional por cuota, con escala 2.")
    private BigDecimal costoAdicionalPorCuota;

    @Digits(integer = 13, fraction = 2, message = "El ingreso mensual debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Ingreso mensual usado para el resumen educativo, con escala 2.")
    private BigDecimal ingresoMensual;

    @Schema(description = "Plazos alternativos que se comparan en la respuesta.")
    private List<Integer> cantidadCuotasAlternativas;

    @Builder.Default
    @Schema(description = "Si es true, incluye la tabla de amortización.", defaultValue = "true")
    private Boolean incluirTabla = true;

    public SimulacionCreditoRequestDTO() {
    }

    public SimulacionCreditoRequestDTO(ModalidadCalculo modalidad, BigDecimal montoCapital, Integer cantidadCuotas,
                                       LocalDate fechaPrimeraCuota, LocalDate fechaUltimaCuota, BigDecimal tasaMensual,
                                       BigDecimal tasaAnualEfectiva, BigDecimal valorCuota, BigDecimal gastosIniciales,
                                       BigDecimal costoAdicionalPorCuota, BigDecimal ingresoMensual,
                                       List<Integer> cantidadCuotasAlternativas, Boolean incluirTabla) {
        this.modalidad = modalidad;
        this.montoCapital = montoCapital;
        this.cantidadCuotas = cantidadCuotas;
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        this.fechaUltimaCuota = fechaUltimaCuota;
        this.tasaMensual = tasaMensual;
        this.tasaAnualEfectiva = tasaAnualEfectiva;
        this.valorCuota = valorCuota;
        this.gastosIniciales = gastosIniciales;
        this.costoAdicionalPorCuota = costoAdicionalPorCuota;
        this.ingresoMensual = ingresoMensual;
        this.cantidadCuotasAlternativas = cantidadCuotasAlternativas;
        this.incluirTabla = incluirTabla;
    }
}
