package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;

/**
 * Datos de una deuda tal como se exponen en la API.
 *
 * <p>Es un DTO de datos: la matemática financiera (tasa anual efectiva, valor de
 * cuota por defecto, fecha de la última cuota y los saldos) vive en
 * {@link com.example.saveup.service.DeudaService}, que la calcula con
 * {@link com.example.saveup.service.finanzas.MotorFinanciero},
 * {@link com.example.saveup.service.finanzas.CalendarioCuotas} e
 * {@link com.example.saveup.service.finanzas.ImputacionPagos}. Los campos
 * derivados quedan en null si el servicio no pudo calcularlos.
 */
@Data
@NoArgsConstructor
public class DeudaResponseDTO {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private Long id;
    private String nombre;
    private String descripcion;
    private TipoDeuda tipoDeuda;
    private ModalidadCalculo modalidadCalculo;
    private BigDecimal montoCapital;
    private BigDecimal tasaMensualPorcentaje;
    private BigDecimal tasaAnualEfectiva;
    private BigDecimal valorCuota;
    private BigDecimal gastosIniciales;
    private BigDecimal costoAdicionalPorCuota;
    private BigDecimal costoTotalCredito;
    private BigDecimal cargaAnualEquivalente;
    private LocalDate fechaPrimeraCuota;
    private LocalDate fechaUltimaCuota;
    private int cuotasPagadasPrevias;
    private BigDecimal montoPagadoPrevio;
    private BigDecimal montoTotal;
    private int cantidadCuotas;
    private EstadoDeuda estado;
    private Date fechaCreacion;

    /** Tasa mensual como fracción (0.015 = 1,5 %): dato crudo del motor, no se expone. */
    @JsonIgnore
    private BigDecimal tasaMensualFraccion;

    /** Suma de los pagos reales, sin las cuotas pagadas previas. */
    private BigDecimal montoPagadoReal;

    // ──────────────────────── Campos derivados (los calcula DeudaService) ────────────────────────

    /** montoPagadoPrevio + montoPagadoReal. */
    private BigDecimal montoPagado;
    private BigDecimal montoRestante;
    /** Lo que falta por pagar, intereses futuros incluidos. */
    private BigDecimal totalPorPagar;
    /**
     * Capital que aún se debe: montoCapital − capital imputado. Aproxima lo que
     * costaría liquidar hoy y NO incluye el interés devengado del mes en curso.
     */
    private BigDecimal saldoCapital;
    /** Cuotas en estado PAGADA según la imputación (incluye las previas). */
    private int cuotasPagadas;
    /** Número de la próxima cuota impaga (null si no queda ninguna). */
    private Integer proximaCuotaNumero;
    /** Lo que falta para cubrir la próxima cuota impaga (incluye su costo adicional). */
    private BigDecimal proximaCuotaMontoPendiente;
    /** Vencimiento de la próxima cuota impaga (null si no hay fechaPrimeraCuota). */
    private LocalDate proximaFechaVencimiento;
    /** Interés imputado a los pagos reales y a las cuotas previas. */
    private BigDecimal interesesPagados;

    /**
     * Constructor de la proyección JPQL (ver {@code DeudaRepository}). Solo
     * asigna datos: los campos derivados los completa el servicio.
     */
    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Long cuotasPagadas,
                            TipoDeuda tipoDeuda, ModalidadCalculo modalidadCalculo, BigDecimal montoCapital,
                            BigDecimal tasaMensual, BigDecimal valorCuota, BigDecimal gastosIniciales,
                            BigDecimal costoAdicionalPorCuota, BigDecimal costoTotalCredito,
                            BigDecimal cargaAnualEquivalente, LocalDate fechaPrimeraCuota,
                            int cuotasPagadasPrevias, BigDecimal montoPagadoPrevio) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.montoTotal = montoTotal != null ? montoTotal.setScale(ESCALA_DINERO, REDONDEO) : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.cantidadCuotas = cantidadCuotas;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;

        this.tipoDeuda = tipoDeuda != null ? tipoDeuda : TipoDeuda.OTRO;
        this.modalidadCalculo = modalidadCalculo != null ? modalidadCalculo : ModalidadCalculo.SIN_INTERES;
        this.montoCapital = montoCapital != null ? montoCapital.setScale(ESCALA_DINERO, REDONDEO) : this.montoTotal;
        this.valorCuota = valorCuota != null ? valorCuota.setScale(ESCALA_DINERO, REDONDEO) : null;
        this.gastosIniciales = gastosIniciales != null ? gastosIniciales.setScale(ESCALA_DINERO, REDONDEO) : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.costoAdicionalPorCuota = costoAdicionalPorCuota != null ? costoAdicionalPorCuota.setScale(ESCALA_DINERO, REDONDEO) : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.costoTotalCredito = costoTotalCredito != null ? costoTotalCredito.setScale(ESCALA_DINERO, REDONDEO) : this.montoTotal;
        this.cargaAnualEquivalente = cargaAnualEquivalente != null
                ? cargaAnualEquivalente.multiply(CIEN).setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        this.cuotasPagadasPrevias = cuotasPagadasPrevias;
        this.montoPagadoPrevio = montoPagadoPrevio != null ? montoPagadoPrevio.setScale(ESCALA_DINERO, REDONDEO) : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.tasaMensualFraccion = tasaMensual;

        this.montoPagadoReal = montoPagadoRaw != null
                ? montoPagadoRaw.abs().setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        // El conteo real de cuotas pagadas sale de la imputación (DeudaService).
        this.cuotasPagadas = 0;
    }
}
