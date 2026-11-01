package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "CATEGORIA")
@Data
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CATEGORIA")
    private Long id;

    /** Código estable del catálogo (T10a). El código de la app busca por aquí, nunca por nombre. */
    @Column(name = "CODIGO", nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, unique = true)
    private String nombre;

    @Column(name = "ICON_ID")
    private String iconId; // Identificador del icono (ej: "ic_food")

    @Column(name = "COLOR_HEX")
    private String colorHex; // Código de color (ej: "#FF5733")

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_PRESUPUESTO")
    private com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public Categoria() {
    }

    public Categoria(Long id, String nombre, String iconId, String colorHex,
                     com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto) {
        this(id, null, nombre, iconId, colorHex, tipoPresupuesto);
    }

    public Categoria(Long id, String codigo, String nombre, String iconId, String colorHex,
                     com.example.saveup.model.enums.TipoPresupuesto tipoPresupuesto) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
        this.iconId = iconId;
        this.colorHex = colorHex;
        this.tipoPresupuesto = tipoPresupuesto;
    }
}
