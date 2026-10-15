package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.service.finanzas.CuotaAmortizacion;
import com.example.saveup.service.finanzas.ImputacionPagos;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

        // Campos del cronograma de una deuda real (los completa DeudaService con ImputacionPagos).
        private ImputacionPagos.EstadoCuota estado;
        private BigDecimal montoPagado;
        private Boolean previa;

        /**
         * Construye un CuotaSimulacionDTO a partir de una fila de amortización
         * y su fecha de vencimiento correspondiente.
         */
        public static CuotaSimulacionDTO from(CuotaAmortizacion fila, LocalDate fechaVencimiento) {
            return CuotaSimulacionDTO.builder()
                    .numero(fila.numero())
                    .fechaVencimiento(fechaVencimiento)
                    .cuota(fila.cuota())
                    .capital(fila.capital())
                    .interes(fila.interes())
                    .costoAdicional(fila.costoAdicional())
                    .saldo(fila.saldo())
                    .build();
        }

        /**
         * Mapper compartido por el simulador y el detalle de deuda: convierte la
         * tabla de amortización en DTOs, asociando a cada cuota su fecha de
         * vencimiento (nula si no hay calendario). Evita duplicar el bucle.
         */
        public static List<CuotaSimulacionDTO> from(List<CuotaAmortizacion> tabla, List<LocalDate> fechasVencimiento) {
            List<CuotaSimulacionDTO> salida = new ArrayList<>(tabla.size());
            for (int i = 0; i < tabla.size(); i++) {
                LocalDate fecha = fechasVencimiento != null && i < fechasVencimiento.size()
                        ? fechasVencimiento.get(i)
                        : null;
                salida.add(from(tabla.get(i), fecha));
            }
            return salida;
        }
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
