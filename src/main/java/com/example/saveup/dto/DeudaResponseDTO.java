package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import lombok.Data;
import java.util.Date;

@Data
public class DeudaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private double montoTotal;
    private int cantidadCuotas;
    private EstadoDeuda estado;
    private Date fechaCreacion;

    // Campos calculados que se llenarán en el Service
    private double montoPagado;
    private double montoRestante;
    private int cuotasPagadas;
}