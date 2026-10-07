package com.example.saveup.service.finanzas;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendario de cuotas (clase pura, sin BD, sin Spring).
 * Solo periodicidad mensual; los períodos son meses completos.
 */
public class CalendarioCuotas {

    private static final int MAX_CUOTAS = 360;

    /**
     * Genera las fechas de vencimiento de las cuotas.
     * El día del mes se ancla en la fecha de la primera cuota;
     * si el mes no tiene ese día, se usa el último día del mes.
     *
     * @param primeraCuota   fecha de la primera cuota
     * @param cantidadCuotas número de cuotas a generar (1–360)
     * @return lista de fechas de vencimiento
     */
    public List<LocalDate> fechasVencimiento(LocalDate primeraCuota, int cantidadCuotas) {
        if (primeraCuota == null) {
            throw new CalculoFinancieroException("La fecha de la primera cuota es obligatoria");
        }
        if (cantidadCuotas < 1 || cantidadCuotas > MAX_CUOTAS) {
            throw new CalculoFinancieroException(
                    "La cantidad de cuotas debe estar entre 1 y " + MAX_CUOTAS);
        }

        int diaAncla = primeraCuota.getDayOfMonth();
        List<LocalDate> fechas = new ArrayList<>(cantidadCuotas);
        fechas.add(primeraCuota);

        for (int i = 1; i < cantidadCuotas; i++) {
            YearMonth ym = YearMonth.from(primeraCuota).plusMonths(i);
            int dia = Math.min(diaAncla, ym.lengthOfMonth());
            fechas.add(ym.atDay(dia));
        }

        return fechas;
    }

    /**
     * Cuenta las cuotas entre la primera y la última fecha (inclusive).
     * La última cuota vence el mes anterior si la fecha de última cuota
     * no alcanza para un vencimiento completo ese mes.
     *
     * @param primeraCuota fecha de la primera cuota
     * @param ultimaCuota  fecha límite (la última cuota vence en o antes de esta fecha)
     * @return número de cuotas
     */
    public int contarCuotas(LocalDate primeraCuota, LocalDate ultimaCuota) {
        if (primeraCuota == null || ultimaCuota == null) {
            throw new CalculoFinancieroException("Ambas fechas son obligatorias");
        }
        if (ultimaCuota.isBefore(primeraCuota)) {
            throw new CalculoFinancieroException(
                    "La fecha de última cuota no puede ser anterior a la primera");
        }

        int diaAncla = primeraCuota.getDayOfMonth();
        int count = 0;

        for (int i = 0; ; i++) {
            YearMonth ym = YearMonth.from(primeraCuota).plusMonths(i);
            int dia = Math.min(diaAncla, ym.lengthOfMonth());
            LocalDate vencimiento = ym.atDay(dia);

            if (vencimiento.isAfter(ultimaCuota)) {
                break;
            }
            count++;

            if (count > MAX_CUOTAS) {
                throw new CalculoFinancieroException(
                        "El resultado excede el máximo de " + MAX_CUOTAS + " cuotas");
            }
        }

        return count;
    }
}
