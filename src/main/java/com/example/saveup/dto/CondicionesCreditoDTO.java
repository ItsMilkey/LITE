package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CondicionesCreditoDTO {

    @NotNull(message = "La modalidad es obligatoria")
    private ModalidadCalculo modalidad;

    @NotNull(message = "El monto de capital es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto de capital debe ser mayor a 0")
    private BigDecimal montoCapital;

    private Integer cantidadCuotas;
    private LocalDate fechaPrimeraCuota;
    private LocalDate fechaUltimaCuota;
    private BigDecimal tasaMensual;
    private BigDecimal tasaAnualEfectiva;
    private BigDecimal valorCuota;
    private BigDecimal gastosIniciales;
    private BigDecimal costoAdicionalPorCuota;
}
