package com.example.saveup.model;

import com.example.saveup.model.enums.Plataforma;
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
 * Teléfono registrado para recibir notificaciones push (T9.6). El token es el
 * identificador que entrega Firebase Cloud Messaging; se guarda completo porque
 * es necesario para enviar, pero nunca se loguea ni se devuelve al cliente sin
 * enmascarar (ver {@code TokensPush}).
 *
 * <p>El {@code token} es único a nivel global: un mismo teléfono puede cambiar
 * de cuenta y el servicio lo reasigna al usuario actual en vez de crear un
 * duplicado. {@code activo=false} marca un token que FCM rechazó; no se vuelve
 * a intentar.
 */
@Entity
@Table(name = "DISPOSITIVO_PUSH", indexes = {
        @Index(name = "idx_disp_push_usuario", columnList = "USUARIO_ID")
})
@Data
public class DispositivoPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    @ToString.Exclude
    private Usuario usuario;

    /** Token de FCM. Credencial de envío: no se registra en logs. */
    @Column(name = "TOKEN", nullable = false, unique = true, length = 255)
    @ToString.Exclude
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "PLATAFORMA", nullable = false, length = 10)
    private Plataforma plataforma;

    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo = true;

    @Column(name = "FECHA_REGISTRO", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaRegistro;

    @Column(name = "FECHA_ULTIMO_USO", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaUltimoUso;

    /** Constructor sin argumentos requerido por JPA/Hibernate. */
    public DispositivoPush() {
    }

    @PrePersist
    protected void onCreate() {
        Date ahora = new Date();
        if (this.fechaRegistro == null) {
            this.fechaRegistro = ahora;
        }
        if (this.fechaUltimoUso == null) {
            this.fechaUltimoUso = ahora;
        }
        if (this.activo == null) {
            this.activo = true;
        }
    }

    public boolean estaActivo() {
        return Boolean.TRUE.equals(activo);
    }
}
