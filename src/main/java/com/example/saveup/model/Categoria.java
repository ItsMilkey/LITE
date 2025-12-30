package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CATEGORIA")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CATEGORIA")
    private Long id;

    @Column(name = "NOMBRE", nullable = false, unique = true)
    private String nombre;

    @Column(name = "ICON_ID")
    private String iconId; // Identificador del icono (ej: "ic_food")

    @Column(name = "COLOR_HEX")
    private String colorHex; // Código de color (ej: "#FF5733")

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_PRESUPUESTO")
    private com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto;
}
