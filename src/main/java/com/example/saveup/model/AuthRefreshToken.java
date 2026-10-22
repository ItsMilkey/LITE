package com.example.saveup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * Token de sesión (refresh) de la app móvil, introducido en T9.
 * <p>
 * El valor que viaja al cliente es un string aleatorio; esta entidad solo
 * persiste su hash SHA-256, nunca el secreto en claro. La rotación marca
 * {@code fechaUso} y {@code fechaRevocacion} del token consumido y crea una
 * fila nueva; el logout y la revocación masiva solo marcan {@code fechaRevocacion}.
 * Un registro revocado que vuelve a usarse se interpreta como posible robo de
 * token y lleva a revocar todas las sesiones del usuario.
 */
@Entity
@Table(name = "AUTH_REFRESH_TOKEN", indexes = {
        @Index(name = "idx_auth_rt_usuario", columnList = "USUARIO_ID")
})
@Data
public class AuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_AUTH_REFRESH_TOKEN")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    @ToString.Exclude // Evita imprimir el usuario (y posibles proxies lazy) en toString().
    private Usuario usuario;

    /** Hash SHA-256 del token en hexadecimal (64 caracteres). */
    @Column(name = "TOKEN_HASH", nullable = false, unique = true, length = 64)
    @ToString.Exclude
    private String tokenHash;

    /** Límite de vida útil del refresh (90 días desde su emisión). */
    @Column(name = "FECHA_EXPIRACION", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaExpiracion;

    /** Nulo = token activo. Con valor: cerrado por rotación, logout o revocación masiva. */
    @Column(name = "FECHA_REVOCACION")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaRevocacion;

    /** Momento en que este token se usó para rotar (nulo si nunca se usó). */
    @Column(name = "FECHA_USO")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaUso;

    /** Constructor sin argumentos requerido por JPA/Hibernate (explícito, sin Lombok). */
    public AuthRefreshToken() {
    }

    public boolean estaRevocado() {
        return fechaRevocacion != null;
    }

    public boolean estaExpirado(Date ahora) {
        return !fechaExpiracion.after(ahora);
    }
}
