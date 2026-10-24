package com.example.saveup.model;

import com.example.saveup.model.enums.TipoRecordatorio;
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
 * Bitácora de avisos ya enviados (T9.6). Su única razón de ser es la
 * deduplicación: la clave (deuda, cuota, tipo) es UNIQUE, así que el job puede
 * ejecutarse varias veces el mismo día sin repetir la notificación.
 */
@Entity
@Table(name = "RECORDATORIO_ENVIADO", indexes = {
        @Index(name = "idx_rec_env_usuario", columnList = "USUARIO_ID")
})
@Data
public class RecordatorioEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    @ToString.Exclude
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEUDA_ID", nullable = false)
    @ToString.Exclude
    private Deuda deuda;

    @Column(name = "CUOTA_NUMERO", nullable = false)
    private int cuotaNumero;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    private TipoRecordatorio tipo;

    @Column(name = "FECHA_ENVIO", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaEnvio;

    /** Constructor sin argumentos requerido por JPA/Hibernate. */
    public RecordatorioEnviado() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.fechaEnvio == null) {
            this.fechaEnvio = new Date();
        }
    }
}
