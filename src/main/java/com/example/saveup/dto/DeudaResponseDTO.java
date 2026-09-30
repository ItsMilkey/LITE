package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeudaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private double montoTotal;
    private int cantidadCuotas;
    private EstadoDeuda estado;
    private Date fechaCreacion;

    // Campos calculados
    private double montoPagado;
    private double montoRestante;
    private int cuotasPagadas;

    /**
     * Constructor utilizado para proyecciones JPQL con agregaciones directas en BD (evita N+1 queries).
     */
    public DeudaResponseDTO(Long id, String nombre, String descripcion, double montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, Double montoPagado, Long cuotasPagadas) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.montoTotal = montoTotal;
        this.cantidadCuotas = cantidadCuotas;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        double pagado = montoPagado != null ? Math.abs(montoPagado) : 0.0;
        this.montoPagado = pagado;
        this.montoRestante = Math.max(0.0, montoTotal - pagado);
        this.cuotasPagadas = cuotasPagadas != null ? cuotasPagadas.intValue() : 0;
    }

    public DeudaResponseDTO(Long id, String nombre, String descripcion, double montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, Double montoPagado, Integer cuotasPagadas) {
        this(id, nombre, descripcion, montoTotal, cantidadCuotas, estado, fechaCreacion, montoPagado,
                cuotasPagadas != null ? cuotasPagadas.longValue() : 0L);
    }
}