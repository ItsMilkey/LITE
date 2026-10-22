package com.example.saveup.model;

import com.example.saveup.model.enums.TipoVerificacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * Token de verificación de un solo uso (T9.3), hoy para el restablecimiento de
 * contraseña. El valor que viaja por correo es un string aleatorio; aquí solo
 * se persiste su hash SHA-256. Un token vale 1 hora y se consume con
 * {@code usado}; pedir otro marca los anteriores como usados.
 */
@Entity
@Table(name = "TOKEN_VERIFICACION", indexes = {
        @Index(name = "idx_token_ver_usuario", columnList = "USUARIO_ID")
})
@Data
public class TokenVerificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_TOKEN_VERIFICACION")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    @ToString.Exclude
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoVerificacion tipo;

    /** Hash SHA-256 del token en hexadecimal (64 caracteres). */
    @Column(name = "TOKEN_HASH", nullable = false, unique = true, length = 64)
    @ToString.Exclude
    private String tokenHash;

    /** Límite de vida del token: 1 hora desde su emisión. */
    @Column(name = "FECHA_EXPIRACION", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaExpiracion;

    /** true = consumido (se usó o fue invalidado por una solicitud posterior). */
    @Column(name = "USADO", nullable = false)
    private Boolean usado = false;

    @Column(name = "FECHA_CREACION", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaCreacion;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public TokenVerificacion() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = new Date();
        }
        if (this.usado == null) {
            this.usado = false;
        }
    }

    public boolean estaUsado() {
        return Boolean.TRUE.equals(usado);
    }

    public boolean estaExpirado(Date ahora) {
        return !fechaExpiracion.after(ahora);
    }
}
