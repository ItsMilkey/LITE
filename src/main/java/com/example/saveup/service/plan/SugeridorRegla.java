package com.example.saveup.service.plan;

import com.example.saveup.service.plan.CalculadoraPlan.GuiaCalculo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Sugerencia de regla 50/30/20 (T10d, sección 5). Clase pura, sin Spring ni
 * base de datos: entra la serie de meses cerrados ya agregada (ingreso real y
 * gasto por tipo efectivo de cada mes) y la guía del usuario; sale el trío
 * {necesidades, deseos, ahorro} sugerido, redondeado a múltiplos de 5.
 *
 * <p>Algoritmo, con {@code BigDecimal} y 10 decimales internos:
 * <ol>
 * <li>Mediana de cada porcentaje entre los meses (cantidad par: promedio de
 * los dos centrales).</li>
 * <li>Si {@code necesidades + deseos > 100}: escala proporcional para sumar 100
 * y {@code ahorro = 0}, marcando {@code gastoSuperaIngreso}.</li>
 * <li>Redondeo a múltiplos de 5 con restos mayores: base = piso a múltiplo de 5;
 * los pasos de 5 que faltan hasta 100 se reparten a los mayores restos; el
 * empate se resuelve en el orden necesidades, deseos, ahorro.</li>
 * </ol>
 */
public class SugeridorRegla {

    /** Cantidad de decimales internos del cálculo (T10d, sección 5). */
    private static final int DECIMALES_INTERNOS = 10;
    private static final int ESCALA_SALIDA = 2;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CINCO = new BigDecimal("5");
    private static final BigDecimal CERO = BigDecimal.ZERO;

    /** Divisor para promediar los dos valores centrales de una mediana par. */
    private static final BigDecimal DOS = new BigDecimal("2");

    /** Pasos de 5 puntos que suman 100 (100 / 5). */
    private static final int PASOS_TOTALES = 20;

    /** Serie de un mes cerrado ya agregada; porcentajes y medias los calcula esta clase. */
    public record MesSerie(String mes, BigDecimal ingresoReal,
                           BigDecimal gastoNecesidades, BigDecimal gastoDeseos) {
    }

    /** Tría {necesidades, deseos, ahorro}; la salida usa escala 2 (actual) o 0 (sugerencia). */
    public record Porcentajes(BigDecimal necesidades, BigDecimal deseos, BigDecimal ahorro) {
    }

    public record Resultado(List<String> mesesAnalizados, Porcentajes actual, Porcentajes sugerencia,
                            boolean gastoSuperaIngreso, boolean igualALaGuia, String motivo) {
    }

    /** Motivo cuando no alcanzan los datos (menos de 2 meses con ingreso). */
    public static final String MOTIVO_DATOS_INSUFICIENTES = "DATOS_INSUFICIENTES";

    /**
     * Calcula la sugerencia.
     *
     * @param meses meses cerrados ya agregados; el orden de entrada es irrelevante
     * @param guia  guía del usuario; null si no tiene configuración
     */
    public Resultado sugerir(List<MesSerie> meses, GuiaCalculo guia) {
        List<MesSerie> ordenados = new ArrayList<>(meses == null ? List.of() : meses);
        ordenados.removeIf(Objects::isNull);
        ordenados.sort(Comparator.comparing(MesSerie::mes));

        BigDecimal n;
        BigDecimal d;
        boolean superaIngreso = false;
        if (ordenados.isEmpty()) {
            n = null;
            d = null;
        } else {
            n = mediana(ordenados, MesSerie::gastoNecesidades);
            d = mediana(ordenados, MesSerie::gastoDeseos);
            if (n.add(d).compareTo(CIEN) > 0) {
                superaIngreso = true;
                BigDecimal factor = CIEN.divide(n.add(d), DECIMALES_INTERNOS, RoundingMode.HALF_UP);
                n = n.multiply(factor).setScale(DECIMALES_INTERNOS, RoundingMode.HALF_UP);
                d = d.multiply(factor).setScale(DECIMALES_INTERNOS, RoundingMode.HALF_UP);
            }
        }
        BigDecimal a = (n == null) ? null : CIEN.subtract(n).subtract(d);
        if (a != null && a.signum() < 0) {
            // Imposible con el paso de escalamiento; por defenses del dominio se acota.
            a = CERO;
        }

        Porcentajes actual = new Porcentajes(escalaSalida(n), escalaSalida(d), escalaSalida(a));
        Porcentajes sugerencia = (n == null || d == null || a == null)
                ? null
                : (ordenados.size() >= 2 ? redondeoMultiplosDeCinco(n, d, a) : null);

        return new Resultado(
                ordenados.stream().map(MesSerie::mes).toList(),
                actual,
                sugerencia,
                superaIngreso,
                igualALaGuia(sugerencia, guia),
                sugerencia == null ? MOTIVO_DATOS_INSUFICIENTES : null);
    }

    // ─────────────────────────── pasos del algoritmo ───────────────────────────

    /** Mediana de los porcentajes de la serie; promedio de los centrales si la cantidad es par. */
    private BigDecimal mediana(List<MesSerie> meses,
                               java.util.function.Function<MesSerie, BigDecimal> gasto) {
        List<BigDecimal> porcentajes = new ArrayList<>(meses.size());
        for (MesSerie mes : meses) {
            BigDecimal ingreso = mes.ingresoReal();
            if (ingreso == null || ingreso.signum() <= 0) {
                continue;
            }
            porcentajes.add(gasto.apply(mes)
                    .abs()
                    .multiply(CIEN)
                    .divide(ingreso, DECIMALES_INTERNOS, RoundingMode.HALF_UP));
        }
        porcentajes.sort(Comparator.naturalOrder());
        int tamano = porcentajes.size();
        if (tamano == 0) {
            return null;
        }
        int centro = tamano / 2;
        if (tamano % 2 == 1) {
            return porcentajes.get(centro);
        }
        return porcentajes.get(centro - 1).add(porcentajes.get(centro))
                .divide(DOS, DECIMALES_INTERNOS, RoundingMode.HALF_UP);
    }

    /**
     * Base = piso a múltiplo de 5 de cada valor; los pasos de 5 que faltan hasta
     * 100 se reparten a los mayores restos; el empate se resuelve en el orden
     * necesidades, deseos, ahorro (D19).
     */
    private Porcentajes redondeoMultiplosDeCinco(BigDecimal n, BigDecimal d, BigDecimal a) {
        BigDecimal[] restos = new BigDecimal[3];
        BigDecimal[] pasos = new BigDecimal[3];
        BigDecimal[] valores = {n, d, a};
        BigDecimal totalPasos = CERO;
        for (int i = 0; i < 3; i++) {
            BigDecimal pasosValor = valores[i].divide(CINCO, 0, RoundingMode.FLOOR);
            pasos[i] = pasosValor;
            totalPasos = totalPasos.add(pasosValor);
            restos[i] = valores[i].subtract(pasosValor.multiply(CINCO)); // fracción de paso, en 0..5
        }
        int pasosQueFaltan = PASOS_TOTALES - totalPasos.intValueExact();
        for (int i = 0; i < pasosQueFaltan; i++) {
            int elegido = indiceMayorResto(restos);
            pasos[elegido] = pasos[elegido].add(BigDecimal.ONE);
            restos[elegido] = restos[elegido].subtract(CINCO);
        }
        return new Porcentajes(
                pasos[0].multiply(CINCO).setScale(0),
                pasos[1].multiply(CINCO).setScale(0),
                pasos[2].multiply(CINCO).setScale(0));
    }

    /** Mayor resto; en empate gana el orden necesidades, deseos, ahorro. */
    private int indiceMayorResto(BigDecimal[] restos) {
        int mejor = 0;
        for (int i = 1; i < restos.length; i++) {
            if (restos[i].compareTo(restos[mejor]) > 0) {
                mejor = i;
            }
        }
        return mejor;
    }

    private boolean igualALaGuia(Porcentajes sugerencia, GuiaCalculo guia) {
        if (sugerencia == null || guia == null || guia.necesidades() == null
                || guia.deseos() == null || guia.ahorro() == null) {
            return false;
        }
        return sugerencia.necesidades().compareTo(guia.necesidades()) == 0
                && sugerencia.deseos().compareTo(guia.deseos()) == 0
                && sugerencia.ahorro().compareTo(guia.ahorro()) == 0;
    }

    private static BigDecimal escalaSalida(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA_SALIDA, RoundingMode.HALF_UP);
    }
}
