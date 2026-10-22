package com.example.saveup.dto;

import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.service.finanzas.CuotaAmortizacion;
import com.example.saveup.service.finanzas.ImputacionPagos;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Resultado de una simulación de crédito.")
public class SimulacionCreditoResponseDTO {

    @Schema(description = "Condiciones normalizadas usadas en el cálculo.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private CondicionesSimulacionDTO condiciones;

    @Schema(description = "Valor de la cuota, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal valorCuota;

    @Schema(description = "Monto total del crédito, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoTotal;

    @Schema(description = "Gastos iniciales, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gastosIniciales;

    @Schema(description = "Costo total del crédito, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal costoTotalCredito;

    @Schema(description = "Intereses y costos totales, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal interesesYCostos;

    @Schema(description = "Carga anual equivalente en porcentaje.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal cargaAnualEquivalente;

    @Schema(description = "Resumen educativo para el usuario.")
    private ResumenEducativoDTO resumenEducativo;

    @Schema(description = "Tabla de amortización; solo si incluirTabla es true.")
    private List<CuotaSimulacionDTO> tablaAmortizacion;

    @Schema(description = "Comparación con plazos alternativos, si se solicitaron.")
    private List<ComparacionPlazoDTO> comparacionPlazos;

    @Schema(description = "Advertencias o supuestos relevantes de la simulación.")
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
    @Schema(description = "Condiciones normalizadas de la simulación.")
    public static class CondicionesSimulacionDTO {
        @Schema(description = "Modalidad aplicada.", requiredMode = Schema.RequiredMode.REQUIRED)
        private ModalidadCalculo modalidad;

        @Schema(description = "Capital simulado, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal montoCapital;

        @Schema(description = "Cantidad de cuotas.", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer cantidadCuotas;

        @Schema(description = "Fecha de la primera cuota en formato yyyy-MM-dd.")
        private LocalDate fechaPrimeraCuota;

        @Schema(description = "Fecha de la última cuota en formato yyyy-MM-dd.")
        private LocalDate fechaUltimaCuota;

        @Schema(description = "Tasa mensual en porcentaje (no fracción).", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal tasaMensual;

        @Schema(description = "Tasa anual efectiva en porcentaje (no fracción).",
                requiredMode = Schema.RequiredMode.REQUIRED)
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
    @Schema(description = "Resumen educativo de la simulación en porcentajes y montos.")
    public static class ResumenEducativoDTO {
        @Schema(description = "Costo en pesos por cada 100 pesos de capital, con escala 2.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal costoPorCada100;

        @Schema(description = "Porcentaje de intereses y costos sobre el capital (0–100).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal porcentajeSobreCapital;

        @Schema(description = "Porcentaje de intereses y costos sobre el total (0–100).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal porcentajeInteresesYCostos;

        @Schema(description = "Porcentaje del ingreso mensual comprometido (0–100).")
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
    @Schema(description = "Fila de la tabla de amortización o del cronograma de una deuda.")
    public static class CuotaSimulacionDTO {
        @Schema(description = "Número de cuota.", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer numero;

        @Schema(description = "Fecha de vencimiento en formato yyyy-MM-dd.")
        private LocalDate fechaVencimiento;

        @Schema(description = "Valor de la cuota, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal cuota;

        @Schema(description = "Parte de capital, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal capital;

        @Schema(description = "Parte de interés, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal interes;

        @Schema(description = "Costo adicional de la cuota, con escala 2.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal costoAdicional;

        @Schema(description = "Saldo de capital después de la cuota, con escala 2.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal saldo;

        // Campos del cronograma de una deuda real (los completa DeudaService con ImputacionPagos).
        @Schema(description = "Estado de la cuota en el cronograma de una deuda real.")
        private ImputacionPagos.EstadoCuota estado;

        @Schema(description = "Monto pagado de la cuota en una deuda real, con escala 2.")
        private BigDecimal montoPagado;

        @Schema(description = "Indica si la cuota ya estaba pagada antes de registrar la deuda.")
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
    @Schema(description = "Comparación de costo para un plazo alternativo.")
    public static class ComparacionPlazoDTO {
        @Schema(description = "Cantidad de cuotas del plazo alternativo.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer cantidadCuotas;

        @Schema(description = "Valor de la cuota, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal valorCuota;

        @Schema(description = "Costo total del crédito, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal costoTotalCredito;

        @Schema(description = "Intereses y costos, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal interesesYCostos;

        @Schema(description = "Carga anual equivalente en porcentaje.", requiredMode = Schema.RequiredMode.REQUIRED)
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
