package com.example.saveup.dto;

import lombok.Data;

@Data
public class CategoriaDTO {
    private Long id;
    private String nombre;
    private String iconId;
    private String colorHex;
    private com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto;
}
