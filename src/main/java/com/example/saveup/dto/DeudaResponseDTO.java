package com.example.saveup.dto;

import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Datos de una deuda. Las tasas se exponen en porcentaje, no como fracción.")
public class DeudaResponseDTO {

    private static final int ESCALA_DINERO = 2;
    private static final int ESCALA_PORCENTAJE_TASA = 4;
    private static final int ESCALA_TASA_ANUAL = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    @Schema(description = "Identificador de la deuda.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Nombre de la deuda.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Descripción opcional.")
    private String descripcion;

    @Schema(description = "Tipo de deuda.", requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoDeuda tipoDeuda;

    @Schema(description = "Modalidad de cálculo aplicada.", requiredMode = Schema.RequiredMode.REQUIRED)
    private ModalidadCalculo modalidadCalculo;

    @Schema(description = "Capital prestado, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoCapital;

    /** Tasa mensual en porcentaje (1,5 = 1,5 %). Se calcula desde la fracción en {@link #setTasaMensualDesdeFraccion}. */
    @Schema(description = "Tasa mensual en porcentaje (1.5 = 1,5 %), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal tasaMensual;

    /** Tasa anual efectiva en porcentaje. Se calcula desde la fracción anual en {@link #setTasaAnualEfectivaDesdeFraccion}. */
    @Schema(description = "Tasa anual efectiva en porcentaje (19.56 = 19,56 %), no una fracción.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal tasaAnualEfectiva;

    @Schema(description = "Valor de la cuota, con escala 2. Puede ser null si no pudo calcularse.")
    private BigDecimal valorCuota;

    @Schema(description = "Gastos iniciales, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gastosIniciales;

    @Schema(description = "Costo adicional por cuota, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal costoAdicionalPorCuota;

    @Schema(description = "Costo total del crédito, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal costoTotalCredito;

    @Schema(description = "Carga anual equivalente en porcentaje.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal cargaAnualEquivalente;

    @Schema(description = "Fecha de la primera cuota en formato yyyy-MM-dd.")
    private LocalDate fechaPrimeraCuota;

    @Schema(description = "Fecha de la última cuota en formato yyyy-MM-dd.")
    private LocalDate fechaUltimaCuota;

    @Schema(description = "Cuotas ya pagadas antes de registrar la deuda.", requiredMode = Schema.RequiredMode.REQUIRED)
    private int cuotasPagadasPrevias;

    @Schema(description = "Monto pagado correspondiente a las cuotas previas.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoPagadoPrevio;

    @Schema(description = "Monto total a pagar, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoTotal;

    @Schema(description = "Cantidad total de cuotas.", requiredMode = Schema.RequiredMode.REQUIRED)
    private int cantidadCuotas;

    @Schema(description = "Estado actual de la deuda.", requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoDeuda estado;

    @Schema(description = "Fecha de creación en ISO-8601 UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Date fechaCreacion;

    /** Tasa mensual como fracción (0.015 = 1,5 %): dato crudo del motor, no se expone. */
    @JsonIgnore
    private BigDecimal tasaMensualFraccion;

    /** Suma de los pagos reales, sin las cuotas pagadas previas. */
    @Schema(description = "Suma de los pagos reales, sin las cuotas previas.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoPagadoReal;

    // ──────────────────────── Campos derivados (los calcula DeudaService) ────────────────────────

    /** montoPagadoPrevio + montoPagadoReal. */
    @Schema(description = "Monto total pagado (previas + pagos reales).", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoPagado;

    @Schema(description = "Monto que falta pagar, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal montoRestante;

    /** Lo que falta por pagar, intereses futuros incluidos. */
    @Schema(description = "Total por pagar incluyendo intereses futuros, con escala 2.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalPorPagar;

    /**
     * Capital que aún se debe: montoCapital − capital imputado. Aproxima lo que
     * costaría liquidar hoy y NO incluye el interés devengado del mes en curso.
     */
    @Schema(description = "Capital pendiente de pago (aproximación para liquidar hoy).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal saldoCapital;

    /** Cuotas en estado PAGADA según la imputación (incluye las previas). */
    @Schema(description = "Cantidad de cuotas pagadas según la imputación.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int cuotasPagadas;

    /** Número de la próxima cuota impaga (null si no queda ninguna). */
    @Schema(description = "Número de la próxima cuota impaga; null si no queda ninguna.")
    private Integer proximaCuotaNumero;

    /** Lo que falta para cubrir la próxima cuota impaga (incluye su costo adicional). */
    @Schema(description = "Monto pendiente de la próxima cuota impaga, con escala 2.")
    private BigDecimal proximaCuotaMontoPendiente;

    /** Vencimiento de la próxima cuota impaga (null si no hay fechaPrimeraCuota). */
    @Schema(description = "Vencimiento de la próxima cuota impaga en formato yyyy-MM-dd.")
    private LocalDate proximaFechaVencimiento;

    /** Interés imputado a los pagos reales y a las cuotas previas. */
    @Schema(description = "Intereses pagados hasta ahora, con escala 2.", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal interesesPagados;

    /**
     * Constructor de la proyección JPQL (ver {@code DeudaRepository}). Solo
     * asigna datos: los campos derivados los completa el servicio.
     */
    public DeudaResponseDTO(Long id, String nombre, String descripcion, BigDecimal montoTotal, int cantidadCuotas,
                            EstadoDeuda estado, Date fechaCreacion, BigDecimal montoPagadoRaw, Long cuotasPagadas,
                            TipoDeuda tipoDeuda, ModalidadCalculo modalidadCalculo, BigDecimal montoCapital,
                            BigDecimal tasaMensualFraccion, BigDecimal valorCuota, BigDecimal gastosIniciales,
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
                ? PorcentajeTasa.aPorcentaje(cargaAnualEquivalente, ESCALA_DINERO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        this.cuotasPagadasPrevias = cuotasPagadasPrevias;
        this.montoPagadoPrevio = montoPagadoPrevio != null ? montoPagadoPrevio.setScale(ESCALA_DINERO, REDONDEO) : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        this.tasaMensualFraccion = tasaMensualFraccion;

        this.montoPagadoReal = montoPagadoRaw != null
                ? montoPagadoRaw.abs().setScale(ESCALA_DINERO, REDONDEO)
                : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        // El conteo real de cuotas pagadas sale de la imputación (DeudaService).
        this.cuotasPagadas = 0;
    }

    /**
     * Fija la tasa mensual partiendo de su fracción (0.015). La conversión
     * fracción → porcentaje ocurre aquí, en el DTO (regla 40): el servicio solo
     * entrega el dato crudo. Con fracción nula expone 0,0000.
     */
    @JsonIgnore
    public void setTasaMensualDesdeFraccion(BigDecimal fraccion) {
        this.tasaMensualFraccion = fraccion;
        this.tasaMensual = fraccion != null
                ? PorcentajeTasa.aPorcentaje(fraccion, ESCALA_PORCENTAJE_TASA)
                : BigDecimal.ZERO.setScale(ESCALA_PORCENTAJE_TASA, REDONDEO);
    }

    /**
     * Fija la tasa anual efectiva partiendo de su fracción anual ((1+m)^12 − 1).
     * La conversión fracción → porcentaje ocurre aquí, en el DTO (regla 40). Con
     * fracción nula expone 0,00.
     */
    @JsonIgnore
    public void setTasaAnualEfectivaDesdeFraccion(BigDecimal fraccionAnual) {
        this.tasaAnualEfectiva = fraccionAnual != null
                ? PorcentajeTasa.aPorcentaje(fraccionAnual, ESCALA_TASA_ANUAL)
                : BigDecimal.ZERO.setScale(ESCALA_TASA_ANUAL, REDONDEO);
    }
}
