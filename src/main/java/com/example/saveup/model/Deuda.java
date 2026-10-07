package com.example.saveup.model;

import com.example.saveup.model.enums.EstadoDeuda;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;

@Entity
@Table(name = "DEUDA")
@Data
public class Deuda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DEUDA")
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 255)
    private String descripcion;

    /**
     * Monto total comprometido de la deuda. Precision 19, escala 2, redondeo HALF_EVEN.
     */
    @Column(name = "MONTO_TOTAL", nullable = false, precision = 19, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "CANTIDAD_CUOTAS", nullable = false)
    private int cantidadCuotas;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoDeuda estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_DEUDA", nullable = false)
    private TipoDeuda tipoDeuda = TipoDeuda.OTRO;

    @Enumerated(EnumType.STRING)
    @Column(name = "MODALIDAD_CALCULO", nullable = false)
    private ModalidadCalculo modalidadCalculo = ModalidadCalculo.SIN_INTERES;

    @Column(name = "MONTO_CAPITAL", nullable = false, precision = 19, scale = 2)
    private BigDecimal montoCapital;

    @Column(name = "GASTOS_INICIALES", nullable = false, precision = 19, scale = 2)
    private BigDecimal gastosIniciales = BigDecimal.ZERO;

    @Column(name = "COSTO_ADICIONAL_POR_CUOTA", nullable = false, precision = 19, scale = 2)
    private BigDecimal costoAdicionalPorCuota = BigDecimal.ZERO;

    @Column(name = "TASA_MENSUAL", nullable = false, precision = 12, scale = 8)
    private BigDecimal tasaMensual = BigDecimal.ZERO;

    @Column(name = "VALOR_CUOTA", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorCuota;

    @Column(name = "FECHA_PRIMERA_CUOTA")
    private LocalDate fechaPrimeraCuota;

    @Column(name = "CUOTAS_PAGADAS_PREVIAS", nullable = false)
    private int cuotasPagadasPrevias = 0;

    @Column(name = "MONTO_PAGADO_PREVIO", nullable = false, precision = 19, scale = 2)
    private BigDecimal montoPagadoPrevio = BigDecimal.ZERO;

    @Column(name = "CARGA_ANUAL_EQUIVALENTE", nullable = false, precision = 10, scale = 4)
    private BigDecimal cargaAnualEquivalente = BigDecimal.ZERO;

    @Column(name = "COSTO_TOTAL_CREDITO", nullable = false, precision = 19, scale = 2)
    private BigDecimal costoTotalCredito;

    @Column(name = "FECHA_CREACION", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = new Date();
        }
        if (this.estado == null) {
            this.estado = EstadoDeuda.PENDIENTE;
        }
    }
}