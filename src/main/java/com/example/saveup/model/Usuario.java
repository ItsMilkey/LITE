package com.example.saveup.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

@Entity
@Table(name = "USUARIO")
@Data // Genera getters, setters, toString, etc.
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @Column(name = "ID_USUARIO", unique = true)
    private String rut;

    @Column(name = "NOMBRE", nullable = false)
    private String nombre;

    @Column(name = "APELLIDO", nullable = false)
    private String apellido;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;
    
    @Column(name = "CONTRASENA_HASH", nullable = false, length = 100)
    private String contrasena;

    @Column(name = "FECHA_REGISTRO", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaRegistro;

    @PrePersist
    protected void onCreate() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = new Date();
        }
    }
}
