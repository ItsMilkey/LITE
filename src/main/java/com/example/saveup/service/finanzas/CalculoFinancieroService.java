package com.example.saveup.service.finanzas;

import com.example.saveup.model.enums.ModalidadCalculo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor financiero puro (sin BD, sin Spring).
 * Calcula cuotas, tabla de amortización, CAE y costos.
 */
public class CalculoFinancieroService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final int ESCALA_TASA = 8;
    private static final int MAX_BISECCION = 200;
    private static final BigDecimal TOLERANCIA_BISECCION = new BigDecimal("1E-12");
    private static final int MESES_POR_ANIO = 12;

    // ──────────────────────── API pública ────────────────────────

    /** Calcula el resultado financiero completo a partir de las condiciones dadas. */
    public ResultadoCalculo calcular(CondicionesCredito c) {
        validar(c);

        BigDecimal tasaMensual;
        BigDecimal cuotaPura;

        switch (c.modalidad()) {
            case SIN_INTERES -> {
                tasaMensual = BigDecimal.ZERO;
                cuotaPura = c.montoCapital().divide(BigDecimal.valueOf(c.cantidadCuotas()), MC)
                        .setScale(ESCALA_DINERO, REDONDEO);
            }
            case TASA_CONOCIDA -> {
                tasaMensual = c.tasaMensual();
                cuotaPura = cuotaFrancesa(c.montoCapital(), tasaMensual, c.cantidadCuotas());
            }
            case CUOTA_CONOCIDA -> {
                cuotaPura = c.valorCuotaPublicada();
                tasaMensual = calcularTasaImplicita(c.montoCapital(), cuotaPura, c.cantidadCuotas());
            }
            default -> throw new CalculoFinancieroException("Modalidad no soportada: " + c.modalidad());
        }

        List<CuotaAmortizacion> tabla = construirTabla(
                c.montoCapital(), tasaMensual, cuotaPura, c.cantidadCuotas(),
                c.costoAdicionalPorCuota());

        BigDecimal valorCuota = tabla.getFirst().cuota();

        BigDecimal montoTotal = BigDecimal.ZERO;
        for (CuotaAmortizacion fila : tabla) {
            montoTotal = montoTotal.add(fila.cuota()).add(fila.costoAdicional());
        }
        montoTotal = montoTotal.setScale(ESCALA_DINERO, REDONDEO);

        BigDecimal gastos = c.gastosIniciales() != null ? c.gastosIniciales() : BigDecimal.ZERO;
        BigDecimal costoTotalCredito = montoTotal.add(gastos).setScale(ESCALA_DINERO, REDONDEO);
        BigDecimal interesesYCostos = costoTotalCredito.subtract(c.montoCapital()).setScale(ESCALA_DINERO, REDONDEO);

        BigDecimal cae = calcularCAE(tabla, c.montoCapital(), gastos, c.costoAdicionalPorCuota());

        BigDecimal tasaAnualEfectiva = tasaMensualAAnual(tasaMensual);

        return new ResultadoCalculo(
                valorCuota, tasaMensual, tasaAnualEfectiva,
                montoTotal, costoTotalCredito, interesesYCostos,
                cae, tabla);
    }

    /** Convierte tasa anual efectiva (fracción) a tasa mensual (fracción). */
    public BigDecimal tasaAnualAMensual(BigDecimal tasaAnual) {
        // Resolver (1+m)^12 = 1+a  →  bisección
        BigDecimal objetivo = BigDecimal.ONE.add(tasaAnual);
        BigDecimal lo = BigDecimal.ZERO;
        BigDecimal hi = tasaAnual; // la mensual siempre es menor que la anual
        if (hi.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Aseguramos que hi sea suficientemente grande
        if (BigDecimal.ONE.add(hi).pow(MESES_POR_ANIO, MC).compareTo(objetivo) < 0) {
            hi = tasaAnual.multiply(BigDecimal.TWO);
        }

        for (int i = 0; i < MAX_BISECCION; i++) {
            BigDecimal mid = lo.add(hi).divide(BigDecimal.TWO, MC);
            BigDecimal f = BigDecimal.ONE.add(mid).pow(MESES_POR_ANIO, MC).subtract(objetivo);
            if (f.abs().compareTo(TOLERANCIA_BISECCION) < 0) {
                return mid.setScale(ESCALA_TASA, REDONDEO);
            }
            if (f.compareTo(BigDecimal.ZERO) > 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return lo.add(hi).divide(BigDecimal.TWO, MC).setScale(ESCALA_TASA, REDONDEO);
    }

    /** Convierte tasa mensual (fracción) a tasa anual efectiva (fracción). */
    public BigDecimal tasaMensualAAnual(BigDecimal tasaMensual) {
        // (1+m)^12 − 1
        return BigDecimal.ONE.add(tasaMensual).pow(MESES_POR_ANIO, MC)
                .subtract(BigDecimal.ONE)
                .setScale(ESCALA_TASA, REDONDEO);
    }

    // ──────────────────────── Validaciones ────────────────────────

    private void validar(CondicionesCredito c) {
        if (c.modalidad() == null) {
            throw new CalculoFinancieroException("La modalidad es obligatoria");
        }
        if (c.montoCapital() == null || c.montoCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculoFinancieroException("El monto de capital debe ser mayor a 0");
        }
        if (c.cantidadCuotas() < 1 || c.cantidadCuotas() > 360) {
            throw new CalculoFinancieroException("La cantidad de cuotas debe estar entre 1 y 360");
        }

        BigDecimal gastos = c.gastosIniciales() != null ? c.gastosIniciales() : BigDecimal.ZERO;
        BigDecimal costoAdicional = c.costoAdicionalPorCuota() != null ? c.costoAdicionalPorCuota() : BigDecimal.ZERO;

        switch (c.modalidad()) {
            case SIN_INTERES -> {
                if (c.tasaMensual() != null && c.tasaMensual().compareTo(BigDecimal.ZERO) != 0) {
                    throw new CalculoFinancieroException(
                            "SIN_INTERES no admite tasaMensual");
                }
                if (c.valorCuotaPublicada() != null) {
                    throw new CalculoFinancieroException(
                            "SIN_INTERES no admite valorCuotaPublicada");
                }
            }
            case TASA_CONOCIDA -> {
                if (c.tasaMensual() == null) {
                    throw new CalculoFinancieroException(
                            "TASA_CONOCIDA requiere tasaMensual");
                }
                if (c.valorCuotaPublicada() != null) {
                    throw new CalculoFinancieroException(
                            "TASA_CONOCIDA no admite valorCuotaPublicada");
                }
            }
            case CUOTA_CONOCIDA -> {
                if (c.valorCuotaPublicada() == null) {
                    throw new CalculoFinancieroException(
                            "CUOTA_CONOCIDA requiere valorCuotaPublicada");
                }
                if (c.tasaMensual() != null && c.tasaMensual().compareTo(BigDecimal.ZERO) != 0) {
                    throw new CalculoFinancieroException(
                            "CUOTA_CONOCIDA no admite tasaMensual");
                }
                if (gastos.compareTo(BigDecimal.ZERO) != 0) {
                    throw new CalculoFinancieroException(
                            "CUOTA_CONOCIDA no admite gastosIniciales");
                }
                if (costoAdicional.compareTo(BigDecimal.ZERO) != 0) {
                    throw new CalculoFinancieroException(
                            "CUOTA_CONOCIDA no admite costoAdicionalPorCuota");
                }
            }
        }

        // Rangos comunes
        if (c.tasaMensual() != null) {
            if (c.tasaMensual().compareTo(BigDecimal.ZERO) < 0
                    || c.tasaMensual().compareTo(BigDecimal.ONE) > 0) {
                throw new CalculoFinancieroException(
                        "La tasa mensual debe estar entre 0 y 1 (fracción)");
            }
        }
        if (gastos.compareTo(BigDecimal.ZERO) < 0) {
            throw new CalculoFinancieroException("Los gastos iniciales no pueden ser negativos");
        }
        if (gastos.compareTo(c.montoCapital()) >= 0) {
            throw new CalculoFinancieroException(
                    "Los gastos iniciales deben ser menores al monto de capital");
        }
        if (costoAdicional.compareTo(BigDecimal.ZERO) < 0) {
            throw new CalculoFinancieroException(
                    "El costo adicional por cuota no puede ser negativo");
        }
    }

    // ──────────────────────── Cálculos internos ────────────────────────

    /** Cuota francesa: C = P·i / (1 − (1+i)^−n). Si i == 0: C = P / n. */
    private BigDecimal cuotaFrancesa(BigDecimal capital, BigDecimal tasa, int n) {
        if (tasa.compareTo(BigDecimal.ZERO) == 0) {
            return capital.divide(BigDecimal.valueOf(n), MC).setScale(ESCALA_DINERO, REDONDEO);
        }
        BigDecimal unoPlusTasa = BigDecimal.ONE.add(tasa);
        BigDecimal factor = unoPlusTasa.pow(n, MC);
        BigDecimal numerador = capital.multiply(tasa, MC);
        BigDecimal denominador = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(factor, MC), MC);
        return numerador.divide(denominador, MC).setScale(ESCALA_DINERO, REDONDEO);
    }

    /** Construye la tabla de amortización con ajuste de centavos en la última cuota. */
    private List<CuotaAmortizacion> construirTabla(
            BigDecimal capital, BigDecimal tasa, BigDecimal cuotaPura,
            int n, BigDecimal costoAdicionalPorCuota) {

        BigDecimal costo = costoAdicionalPorCuota != null ? costoAdicionalPorCuota : BigDecimal.ZERO;
        costo = costo.setScale(ESCALA_DINERO, REDONDEO);

        List<CuotaAmortizacion> tabla = new ArrayList<>(n);
        BigDecimal saldo = capital.setScale(ESCALA_DINERO, REDONDEO);

        for (int k = 1; k <= n; k++) {
            BigDecimal interes = saldo.multiply(tasa, MC).setScale(ESCALA_DINERO, REDONDEO);
            BigDecimal capitalK = cuotaPura.subtract(interes).setScale(ESCALA_DINERO, REDONDEO);
            BigDecimal cuotaK = cuotaPura;

            if (k == n) {
                // Última cuota: ajustar para cerrar saldo en 0
                capitalK = saldo;
                cuotaK = capitalK.add(interes).setScale(ESCALA_DINERO, REDONDEO);
            }

            saldo = saldo.subtract(capitalK).setScale(ESCALA_DINERO, REDONDEO);

            tabla.add(new CuotaAmortizacion(k, cuotaK, capitalK, interes, costo, saldo));
        }

        return tabla;
    }

    /**
     * Tasa implícita para CUOTA_CONOCIDA: resolver r en [0, 1] tal que
     * capital = Σ cuota / (1+r)^k, por bisección.
     */
    private BigDecimal calcularTasaImplicita(BigDecimal capital, BigDecimal cuota, int n) {
        BigDecimal sumaCuotas = cuota.multiply(BigDecimal.valueOf(n));
        int cmp = sumaCuotas.compareTo(capital);
        if (cmp < 0) {
            throw new CalculoFinancieroException("Los pagos no cubren el capital");
        }
        if (cmp == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal lo = BigDecimal.ZERO;
        BigDecimal hi = BigDecimal.ONE;

        for (int i = 0; i < MAX_BISECCION; i++) {
            BigDecimal mid = lo.add(hi).divide(BigDecimal.TWO, MC);
            BigDecimal vp = valorPresente(cuota, mid, n);
            BigDecimal diff = vp.subtract(capital);

            if (diff.abs().compareTo(TOLERANCIA_BISECCION) < 0) {
                return mid.setScale(ESCALA_TASA, REDONDEO);
            }
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // tasa muy baja → VP muy alto → subir tasa
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo.add(hi).divide(BigDecimal.TWO, MC).setScale(ESCALA_TASA, REDONDEO);
    }

    /**
     * Calcula la Carga Anual Equivalente (CAE) referencial.
     * Sin costos: CAE = (1+i)^12 − 1.
     * Con costos: se resuelve r tal que Σ flujo_k / (1+r)^k = montoNeto, por bisección.
     */
    private BigDecimal calcularCAE(
            List<CuotaAmortizacion> tabla, BigDecimal capital,
            BigDecimal gastosIniciales, BigDecimal costoAdicionalPorCuota) {

        BigDecimal gastos = gastosIniciales != null ? gastosIniciales : BigDecimal.ZERO;
        BigDecimal costoAdicional = costoAdicionalPorCuota != null ? costoAdicionalPorCuota : BigDecimal.ZERO;

        boolean hayCostos = gastos.compareTo(BigDecimal.ZERO) > 0
                || costoAdicional.compareTo(BigDecimal.ZERO) > 0;

        if (!hayCostos) {
            // Sin costos: CAE = (1+i)^12 − 1 usando la tasa de la tabla
            // La tasa se puede recuperar del primer flujo si capital > 0
            BigDecimal tasa = tabla.isEmpty() ? BigDecimal.ZERO :
                    (capital.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                            tabla.getFirst().interes().divide(capital, MC));
            return tasaMensualAAnual(tasa);
        }

        // Con costos: resolver r tal que Σ flujo_k / (1+r)^k = montoNeto
        BigDecimal montoNeto = capital.subtract(gastos);
        if (montoNeto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculoFinancieroException(
                    "El monto neto recibido (capital - gastos iniciales) debe ser mayor a 0");
        }

        // Flujos = cuota + costoAdicional de cada cuota
        List<BigDecimal> flujos = new ArrayList<>(tabla.size());
        for (CuotaAmortizacion fila : tabla) {
            flujos.add(fila.cuota().add(fila.costoAdicional()));
        }

        BigDecimal tasaMensualCAE = resolverTasaPorBiseccion(flujos, montoNeto);
        return tasaMensualAAnual(tasaMensualCAE);
    }

    /** Resuelve r tal que Σ flujo_k / (1+r)^k = objetivo, por bisección en [0, 1]. */
    private BigDecimal resolverTasaPorBiseccion(List<BigDecimal> flujos, BigDecimal objetivo) {
        BigDecimal lo = BigDecimal.ZERO;
        BigDecimal hi = BigDecimal.ONE;

        for (int i = 0; i < MAX_BISECCION; i++) {
            BigDecimal mid = lo.add(hi).divide(BigDecimal.TWO, MC);
            BigDecimal vp = BigDecimal.ZERO;
            for (int k = 0; k < flujos.size(); k++) {
                BigDecimal divisor = BigDecimal.ONE.add(mid).pow(k + 1, MC);
                vp = vp.add(flujos.get(k).divide(divisor, MC));
            }
            BigDecimal diff = vp.subtract(objetivo);
            if (diff.abs().compareTo(TOLERANCIA_BISECCION) < 0) {
                return mid.setScale(ESCALA_TASA, REDONDEO);
            }
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo.add(hi).divide(BigDecimal.TWO, MC).setScale(ESCALA_TASA, REDONDEO);
    }

    /** VP = Σ pago / (1+r)^k para k = 1..n */
    private BigDecimal valorPresente(BigDecimal pago, BigDecimal tasa, int n) {
        BigDecimal vp = BigDecimal.ZERO;
        for (int k = 1; k <= n; k++) {
            BigDecimal divisor = BigDecimal.ONE.add(tasa).pow(k, MC);
            vp = vp.add(pago.divide(divisor, MC));
        }
        return vp;
    }
}
