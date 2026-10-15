package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.service.finanzas.CuotaAmortizacion;
import com.example.saveup.service.finanzas.ImputacionPagos;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    public SimulacionCreditoResponseDTO(CondicionesSimulacionDTO condiciones, BigDecimal valorCuota, BigDecimal montoTotal,
                                        BigDecimal gastosIniciales, BigDecimal costoTotalCredito,
                                        BigDecimal interesesYCostos, BigDecimal cargaAnualEquivalente,
                                        ResumenEducativoDTO resumenEducativo,
                                        List<CuotaSimulacionDTO> tablaAmortizacion,
                                        List<ComparacionPlazoDTO> comparacionPlazos, List<String> advertencias) {
        this.condiciones = condiciones;
        this.valorCuota = valorCuota;
        this.montoTotal = montoTotal;
        this.gastosIniciales = gastosIniciales;
        this.costoTotalCredito = costoTotalCredito;
        this.interesesYCostos = interesesYCostos;
        this.cargaAnualEquivalente = cargaAnualEquivalente;
        this.resumenEducativo = resumenEducativo;
        this.tablaAmortizacion = tablaAmortizacion;
        this.comparacionPlazos = comparacionPlazos;
        this.advertencias = advertencias;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CondicionesSimulacionDTO {
        private ModalidadCalculo modalidad;
        private BigDecimal montoCapital;
        private Integer cantidadCuotas;
        private LocalDate fechaPrimeraCuota;
        private LocalDate fechaUltimaCuota;
        private BigDecimal tasaMensual;
        private BigDecimal tasaAnualEfectiva;

        public CondicionesSimulacionDTO(ModalidadCalculo modalidad, BigDecimal montoCapital, Integer cantidadCuotas,
                                        LocalDate fechaPrimeraCuota, LocalDate fechaUltimaCuota, BigDecimal tasaMensual,
                                        BigDecimal tasaAnualEfectiva) {
            this.modalidad = modalidad;
            this.montoCapital = montoCapital;
            this.cantidadCuotas = cantidadCuotas;
            this.fechaPrimeraCuota = fechaPrimeraCuota;
            this.fechaUltimaCuota = fechaUltimaCuota;
            this.tasaMensual = tasaMensual;
            this.tasaAnualEfectiva = tasaAnualEfectiva;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResumenEducativoDTO {
        private BigDecimal costoPorCada100;
        private BigDecimal porcentajeSobreCapital;
        private BigDecimal porcentajeInteresesYCostos;
        private BigDecimal porcentajeIngresoComprometido;

        public ResumenEducativoDTO(BigDecimal costoPorCada100, BigDecimal porcentajeSobreCapital,
                                   BigDecimal porcentajeInteresesYCostos, BigDecimal porcentajeIngresoComprometido) {
            this.costoPorCada100 = costoPorCada100;
            this.porcentajeSobreCapital = porcentajeSobreCapital;
            this.porcentajeInteresesYCostos = porcentajeInteresesYCostos;
            this.porcentajeIngresoComprometido = porcentajeIngresoComprometido;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
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

        public CuotaSimulacionDTO(Integer numero, LocalDate fechaVencimiento, BigDecimal cuota, BigDecimal capital,
                                  BigDecimal interes, BigDecimal costoAdicional, BigDecimal saldo,
                                  ImputacionPagos.EstadoCuota estado, BigDecimal montoPagado, Boolean previa) {
            this.numero = numero;
            this.fechaVencimiento = fechaVencimiento;
            this.cuota = cuota;
            this.capital = capital;
            this.interes = interes;
            this.costoAdicional = costoAdicional;
            this.saldo = saldo;
            this.estado = estado;
            this.montoPagado = montoPagado;
            this.previa = previa;
        }

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
    public static class ComparacionPlazoDTO {
        private Integer cantidadCuotas;
        private BigDecimal valorCuota;
        private BigDecimal costoTotalCredito;
        private BigDecimal interesesYCostos;
        private BigDecimal cargaAnualEquivalente;

        public ComparacionPlazoDTO(Integer cantidadCuotas, BigDecimal valorCuota, BigDecimal costoTotalCredito,
                                   BigDecimal interesesYCostos, BigDecimal cargaAnualEquivalente) {
            this.cantidadCuotas = cantidadCuotas;
            this.valorCuota = valorCuota;
            this.costoTotalCredito = costoTotalCredito;
            this.interesesYCostos = interesesYCostos;
            this.cargaAnualEquivalente = cargaAnualEquivalente;
        }
    }
}
