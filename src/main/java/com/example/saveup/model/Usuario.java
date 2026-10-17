package com.example.saveup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import java.util.Date;

@Entity
@Table(name = "USUARIO")
@Data // Genera getters, setters, toString, etc.
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
    @JsonIgnore // El hash nunca sale en una respuesta JSON.
    @ToString.Exclude // Ni en cualquier log que imprima un Usuario (@Data genera toString()).
    private String contrasena;

    @Column(name = "FECHA_REGISTRO", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaRegistro;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public Usuario() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = new Date();
        }
    }
}
