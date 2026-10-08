package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeudaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private TipoDeuda tipoDeuda;
    private ModalidadCalculo modalidadCalculo;
    private BigDecimal montoCapital;
    private BigDecimal tasaMensual;
    private BigDecimal tasaAnualEfectiva;
    private BigDecimal valorCuota;
    private BigDecimal gastosIniciales;
    private BigDecimal costoAdicionalPorCuota;
    private BigDecimal costoTotalCredito;
    private BigDecimal cargaAnualEquivalente;
    private LocalDate fechaPrimeraCuota;
    private LocalDate fechaUltimaCuota;
    private int cuotasPagadasPrevias;
    private BigDecimal montoTotal;
    private int cantidadCuotas;
    private EstadoDeuda estado;
    private Date fechaCreacion;

    // Campos calculados
    private BigDecimal montoPagado;
    private BigDecimal montoRestante;
    private int cuotasPagadas;

    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Long cuotasPagadas) {
        this(id, nombre, descripcion, montoTotal, cantidadCuotas, estado, fechaCreacion, montoPagadoRaw, cuotasPagadas,
             TipoDeuda.OTRO, ModalidadCalculo.SIN_INTERES, montoTotal, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, montoTotal, BigDecimal.ZERO, null, 0, BigDecimal.ZERO);
    }
    
    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Integer cuotasPagadas) {
        this(id, nombre, descripcion, montoTotal, cantidadCuotas, estado, fechaCreacion, montoPagadoRaw, 
             cuotasPagadas != null ? cuotasPagadas.longValue() : 0L);
    }

    /**
     * Constructor utilizado para proyecciones JPQL con agregaciones directas en BD (evita N+1 queries).
     */
    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Long cuotasPagadas,
                            TipoDeuda tipoDeuda, ModalidadCalculo modalidadCalculo, BigDecimal montoCapital,
                            BigDecimal tasaMensual, BigDecimal valorCuota, BigDecimal gastosIniciales,
                            BigDecimal costoAdicionalPorCuota, BigDecimal costoTotalCredito,
                            BigDecimal cargaAnualEquivalente, LocalDate fechaPrimeraCuota,
                            int cuotasPagadasPrevias, BigDecimal montoPagadoPrevio) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.montoTotal = montoTotal != null ? montoTotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.cantidadCuotas = cantidadCuotas;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        
        this.tipoDeuda = tipoDeuda != null ? tipoDeuda : TipoDeuda.OTRO;
        this.modalidadCalculo = modalidadCalculo != null ? modalidadCalculo : ModalidadCalculo.SIN_INTERES;
        this.montoCapital = montoCapital != null ? montoCapital.setScale(2, RoundingMode.HALF_UP) : this.montoTotal;
        this.tasaMensual = tasaMensual != null ? tasaMensual.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        // Tasa Anual Efectiva: calculada desde la tasa mensual fraccionaria, en caso de ser > 0.
        BigDecimal tasaFraccion = tasaMensual != null ? tasaMensual : BigDecimal.ZERO;
        this.tasaAnualEfectiva = tasaFraccion.compareTo(BigDecimal.ZERO) > 0 ? 
            BigDecimal.ONE.add(tasaFraccion).pow(12).subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            
        this.valorCuota = valorCuota != null ? valorCuota.setScale(2, RoundingMode.HALF_UP) : 
            (cantidadCuotas > 0 ? this.montoTotal.divide(BigDecimal.valueOf(cantidadCuotas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        this.gastosIniciales = gastosIniciales != null ? gastosIniciales.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.costoAdicionalPorCuota = costoAdicionalPorCuota != null ? costoAdicionalPorCuota.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.costoTotalCredito = costoTotalCredito != null ? costoTotalCredito.setScale(2, RoundingMode.HALF_UP) : this.montoTotal;
        this.cargaAnualEquivalente = cargaAnualEquivalente != null ? cargaAnualEquivalente.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        if (fechaPrimeraCuota != null && cantidadCuotas > 0) {
            this.fechaUltimaCuota = fechaPrimeraCuota.plusMonths(cantidadCuotas - 1);
        }
        this.cuotasPagadasPrevias = cuotasPagadasPrevias;

        BigDecimal pagado = montoPagadoRaw != null
                ? montoPagadoRaw.abs().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal previo = montoPagadoPrevio != null ? montoPagadoPrevio.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.montoPagado = pagado.add(previo);
        this.montoRestante = this.montoTotal.subtract(this.montoPagado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.cuotasPagadas = (cuotasPagadas != null ? cuotasPagadas.intValue() : 0) + cuotasPagadasPrevias;
    }


}