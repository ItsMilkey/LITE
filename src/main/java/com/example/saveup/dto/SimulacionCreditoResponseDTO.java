package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimulacionCreditoResponseDTO {

    private CondicionesSimulacionDTO condiciones;
    private BigDecimal valorCuota;
    private BigDecimal montoTotal;
    private BigDecimal gastosIniciales;
    private BigDecimal costoTotalCredito;
    private BigDecimal interesesYCostos;
    private BigDecimal cargaAnualEquivalente;
    private ResumenEducativoDTO resumenEducativo;
    private List<CuotaSimulacionDTO> tablaAmortizacion;
    private List<ComparacionPlazoDTO> comparacionPlazos;
    private List<String> advertencias;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CondicionesSimulacionDTO {
        private ModalidadCalculo modalidad;
        private BigDecimal montoCapital;
        private Integer cantidadCuotas;
        private LocalDate fechaPrimeraCuota;
        private LocalDate fechaUltimaCuota;
        private BigDecimal tasaMensual;
        private BigDecimal tasaAnualEfectiva;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResumenEducativoDTO {
        private BigDecimal costoPorCada100;
        private BigDecimal porcentajeSobreCapital;
        private BigDecimal porcentajeInteresesYCostos;
        private BigDecimal porcentajeIngresoComprometido;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CuotaSimulacionDTO {
        private Integer numero;
        private LocalDate fechaVencimiento;
        private BigDecimal cuota;
        private BigDecimal capital;
        private BigDecimal interes;
        private BigDecimal costoAdicional;
        private BigDecimal saldo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparacionPlazoDTO {
        private Integer cantidadCuotas;
        private BigDecimal valorCuota;
        private BigDecimal costoTotalCredito;
        private BigDecimal interesesYCostos;
        private BigDecimal cargaAnualEquivalente;
    }
}
