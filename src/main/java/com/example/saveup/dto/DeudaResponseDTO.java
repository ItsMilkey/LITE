package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeudaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal montoTotal;
    private int cantidadCuotas;
    private EstadoDeuda estado;
    private Date fechaCreacion;

    // Campos calculados
    private BigDecimal montoPagado;
    private BigDecimal montoRestante;
    private int cuotasPagadas;

    /**
     * Constructor utilizado para proyecciones JPQL con agregaciones directas en BD (evita N+1 queries).
     */
    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Long cuotasPagadas) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.montoTotal = montoTotal != null ? montoTotal.setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.cantidadCuotas = cantidadCuotas;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;

        BigDecimal pagado = montoPagadoRaw != null
                ? montoPagadoRaw.abs().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO;
        this.montoPagado = pagado;
        this.montoRestante = this.montoTotal.subtract(pagado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
        this.cuotasPagadas = cuotasPagadas != null ? cuotasPagadas.intValue() : 0;
    }

    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Integer cuotasPagadas) {
        this(id, nombre, descripcion, montoTotal, cantidadCuotas, estado, fechaCreacion, montoPagadoRaw,
                cuotasPagadas != null ? cuotasPagadas.longValue() : 0L);
    }
}