package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SimulacionCreditoRequestDTO {

    @NotNull(message = "La modalidad es obligatoria")
    private ModalidadCalculo modalidad;

    @NotNull(message = "El monto de capital es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto de capital debe ser mayor a 0")
    private BigDecimal montoCapital;

    private Integer cantidadCuotas;

    private LocalDate fechaPrimeraCuota;

    private LocalDate fechaUltimaCuota;

    /** Tasa mensual en porcentaje (0–100). Difiere de la fracción usada internamente (0–1). */
    private BigDecimal tasaMensual;

    private BigDecimal tasaAnualEfectiva;

    private BigDecimal valorCuota;

    private BigDecimal gastosIniciales;

    private BigDecimal costoAdicionalPorCuota;

    private BigDecimal ingresoMensual;

    private List<Integer> cantidadCuotasAlternativas;

    @Builder.Default
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
