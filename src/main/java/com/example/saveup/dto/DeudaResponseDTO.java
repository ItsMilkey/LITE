package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.service.finanzas.DeudaResponseMapper;
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
    private BigDecimal tasaMensualPorcentaje;
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
     * Los cálculos financieros delegan en {@link DeudaResponseMapper}.
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
        this.gastosIniciales = gastosIniciales != null ? gastosIniciales.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.costoAdicionalPorCuota = costoAdicionalPorCuota != null ? costoAdicionalPorCuota.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.costoTotalCredito = costoTotalCredito != null ? costoTotalCredito.setScale(2, RoundingMode.HALF_UP) : this.montoTotal;
        this.cargaAnualEquivalente = cargaAnualEquivalente != null ? cargaAnualEquivalente.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        this.cuotasPagadasPrevias = cuotasPagadasPrevias;

        // Cálculos financieros delegados al mapper (evita feature envy)
        DeudaResponseMapper.populateCalculatedFields(this, tasaMensual, montoTotal, cantidadCuotas,
                valorCuota, fechaPrimeraCuota, montoPagadoRaw, montoPagadoPrevio, cuotasPagadas, cuotasPagadasPrevias);
    }


}