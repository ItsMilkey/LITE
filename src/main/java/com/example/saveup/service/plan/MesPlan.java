package com.example.saveup.service.plan;

import com.example.saveup.error.ParametroInvalidoException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Lectura y validación del mes de ruta ({@code yyyy-MM}) de los endpoints de
 * plan (T10c). El rango permitido es 2000-01 a 2100-12.
 */
public final class MesPlan {

    private static final YearMonth MINIMO = YearMonth.of(2000, 1);
    private static final YearMonth MAXIMO = YearMonth.of(2100, 12);

    private MesPlan() {
    }

    /** @throws ParametroInvalidoException si el formato o el rango no son válidos (400) */
    public static YearMonth parsear(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ParametroInvalidoException("El mes es obligatorio y debe tener el formato yyyy-MM.");
        }
        YearMonth mes;
        try {
            mes = YearMonth.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new ParametroInvalidoException("El mes debe tener el formato yyyy-MM.");
        }
        if (mes.isBefore(MINIMO) || mes.isAfter(MAXIMO)) {
            throw new ParametroInvalidoException("El mes debe estar entre 2000-01 y 2100-12.");
        }
        return mes;
    }

    /** Primer día del mes, que es como se persiste {@code plan_mensual.mes}. */
    public static LocalDate primerDia(YearMonth mes) {
        return mes.atDay(1);
    }
}
