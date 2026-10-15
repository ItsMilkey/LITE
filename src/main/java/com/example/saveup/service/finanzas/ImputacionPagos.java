package com.example.saveup.service.finanzas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Imputación acumulada de los pagos de una deuda contra su cronograma.
 *
 * <p>Clase pura: sin BD ni Spring, 100 % testeable (regla 40). Nada se persiste:
 * se recalcula desde el cronograma, las cuotas pagadas previas y la suma de
 * pagos reales.
 *
 * <p>Reglas (regla 40): los pagos se imputan en orden, cuota por cuota; dentro
 * de cada cuota primero el interés, después el costo adicional y por último el
 * capital. Las cuotas pagadas previas cuentan como cuotas completas ya pagadas,
 * así que los pagos reales empiezan en la cuota siguiente. Un pago parcial no
 * suma una cuota completa.
 */
public class ImputacionPagos {

    /** Estado de una cuota frente a lo imputado. */
    public enum EstadoCuota {
        PAGADA, PARCIAL, PENDIENTE
    }

    /**
     * Resultado de la imputación de una cuota.
     *
     * @param numero  número de cuota (1-based)
     * @param pagado  total imputado a la cuota (cuota + costo adicional)
     * @param interes parte imputada a interés
     * @param costos  parte imputada al costo adicional de la cuota
     * @param capital parte imputada a capital
     * @param estado  PAGADA si cubre la cuota completa, PARCIAL si cubre una parte, PENDIENTE si es 0
     * @param previa  true si la cuota viene de cuotasPagadasPrevias
     */
    public record ImputacionCuota(
            int numero,
            BigDecimal pagado,
            BigDecimal interes,
            BigDecimal costos,
            BigDecimal capital,
            EstadoCuota estado,
            boolean previa
    ) {}

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private ImputacionPagos() {
    }

    /**
     * Imputa los pagos reales de una deuda contra su cronograma.
     *
     * @param tabla                cronograma (tabla de amortización) de la deuda
     * @param cuotasPagadasPrevias cuotas ya cubiertas antes de registrar pagos reales
     * @param pagosReales          suma de los pagos reales, sin las previas (positivo; nulo se toma como 0)
     * @return una fila por cuota del cronograma, en orden
     * @throws IllegalArgumentException si el cronograma es vacío, si las previas se salen
     *                                  del rango o si los pagos reales son negativos
     */
    public static List<ImputacionCuota> imputar(List<CuotaAmortizacion> tabla, int cuotasPagadasPrevias,
                                                BigDecimal pagosReales) {
        if (tabla == null || tabla.isEmpty()) {
            throw new IllegalArgumentException("El cronograma es obligatorio");
        }
        if (cuotasPagadasPrevias < 0 || cuotasPagadasPrevias > tabla.size()) {
            throw new IllegalArgumentException(
                    "cuotasPagadasPrevias debe estar entre 0 y " + tabla.size()
                            + " (recibido: " + cuotasPagadasPrevias + ")");
        }
        if (pagosReales != null && pagosReales.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Los pagos reales no pueden ser negativos");
        }

        BigDecimal porImputar = normalizar(pagosReales);
        List<ImputacionCuota> salida = new ArrayList<>(tabla.size());

        for (int i = 0; i < tabla.size(); i++) {
            CuotaAmortizacion cuota = tabla.get(i);
            boolean previa = i < cuotasPagadasPrevias;
            BigDecimal objetivo = cuota.cuota().add(cuota.costoAdicional()).setScale(ESCALA_DINERO, REDONDEO);

            BigDecimal pagado;
            BigDecimal interes;
            BigDecimal costos;
            BigDecimal capital;

            if (previa) {
                pagado = objetivo;
                interes = cuota.interes().setScale(ESCALA_DINERO, REDONDEO);
                costos = cuota.costoAdicional().setScale(ESCALA_DINERO, REDONDEO);
                capital = cuota.capital().setScale(ESCALA_DINERO, REDONDEO);
            } else {
                pagado = porImputar.min(objetivo).setScale(ESCALA_DINERO, REDONDEO);
                porImputar = porImputar.subtract(pagado).setScale(ESCALA_DINERO, REDONDEO);
                BigDecimal restante = pagado;
                interes = cuota.interes().min(restante).setScale(ESCALA_DINERO, REDONDEO);
                restante = restante.subtract(interes).setScale(ESCALA_DINERO, REDONDEO);
                costos = cuota.costoAdicional().min(restante).setScale(ESCALA_DINERO, REDONDEO);
                capital = restante.subtract(costos).setScale(ESCALA_DINERO, REDONDEO);
            }

            salida.add(new ImputacionCuota(
                    cuota.numero(), pagado, interes, costos, capital, estadoDe(pagado, objetivo), previa));
        }
        return salida;
    }

    /**
     * Monto que falta para cubrir la próxima cuota impaga: la cuota completa,
     * o el restante si esa cuota quedó parcialmente pagada. Incluye el costo
     * adicional de la cuota. Cero si no queda nada por pagar.
     */
    public static BigDecimal montoProximaCuotaImpaga(List<CuotaAmortizacion> tabla, int cuotasPagadasPrevias,
                                                     BigDecimal pagosReales) {
        List<ImputacionCuota> imputacion = imputar(tabla, cuotasPagadasPrevias, pagosReales);
        for (int i = 0; i < imputacion.size(); i++) {
            ImputacionCuota fila = imputacion.get(i);
            if (fila.estado() != EstadoCuota.PAGADA) {
                CuotaAmortizacion cuota = tabla.get(i);
                BigDecimal objetivo = cuota.cuota().add(cuota.costoAdicional()).setScale(ESCALA_DINERO, REDONDEO);
                return objetivo.subtract(fila.pagado()).max(BigDecimal.ZERO).setScale(ESCALA_DINERO, REDONDEO);
            }
        }
        return BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
    }

    /** Suma del capital imputado (incluye las cuotas previas). */
    public static BigDecimal capitalImputado(List<ImputacionCuota> imputacion) {
        BigDecimal total = BigDecimal.ZERO;
        for (ImputacionCuota fila : imputacion) {
            total = total.add(fila.capital());
        }
        return total.setScale(ESCALA_DINERO, REDONDEO);
    }

    /** Cantidad de cuotas en estado PAGADA (incluye las previas). */
    public static int contarPagadas(List<ImputacionCuota> imputacion) {
        int contador = 0;
        for (ImputacionCuota fila : imputacion) {
            if (fila.estado() == EstadoCuota.PAGADA) {
                contador++;
            }
        }
        return contador;
    }

    private static EstadoCuota estadoDe(BigDecimal pagado, BigDecimal objetivo) {
        if (pagado.signum() == 0) {
            return EstadoCuota.PENDIENTE;
        }
        return pagado.compareTo(objetivo) >= 0 ? EstadoCuota.PAGADA : EstadoCuota.PARCIAL;
    }

    private static BigDecimal normalizar(BigDecimal pagosReales) {
        if (pagosReales == null) {
            return BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO);
        }
        return pagosReales.abs().setScale(ESCALA_DINERO, REDONDEO);
    }
}
