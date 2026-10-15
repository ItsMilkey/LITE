package com.example.saveup.service.movimiento;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

/**
 * Frontera entre las fechas ISO de los filtros ({@link LocalDate}) y el campo
 * {@code fecha} de la entidad MOVIMIENTO ({@link Date}).
 *
 * <p>Es el ÚNICO lugar donde ambos tipos se tocan: así ninguna otra clase mezcla
 * {@code java.time} con {@code java.util.Date} (regla .agents/rules/20-java-spring.md).
 *
 * <p>La zona se fija UNA sola vez ({@link ZoneId#systemDefault()}) y se usa de
 * forma consistente en todos los límites, de modo que la ventana inclusiva de
 * {@code desde}/{@code hasta} no se desplace por resoluciones distintas de la zona.
 * Al capturarla una vez, un cambio de zona del JVM no altera la ventana a mitad
 * de ejecución.
 */
public final class FechasMovimiento {

    /** Zona única con la que se interpretan los rangos de fecha del historial. */
    private static final ZoneId ZONA = ZoneId.systemDefault();

    private FechasMovimiento() {
    }

    /** Instante de inicio (00:00:00.000) del día indicado, en la zona fijada. */
    public static Date inicioDelDia(LocalDate fecha) {
        return Date.from(fecha.atStartOfDay(ZONA).toInstant());
    }

    /**
     * Instante de inicio del día siguiente al indicado. Se usa como límite
     * superior EXCLUSIVO: {@code fecha < inicioDelDiaSiguiente(hasta)} incluye el
     * día completo de {@code hasta}.
     */
    public static Date inicioDelDiaSiguiente(LocalDate fecha) {
        return inicioDelDia(fecha.plusDays(1));
    }

    /** Instante de inicio (00:00:00.000) del primer día del mes. */
    public static Date inicioDeMes(YearMonth mes) {
        return inicioDelDia(mes.atDay(1));
    }

    /**
     * Instante de inicio del primer día del mes siguiente. Se usa como límite
     * superior EXCLUSIVO de un mes completo (incluye el último día a cualquier
     * hora, sin depender de la hora ni de los milisegundos).
     */
    public static Date inicioDeMesSiguiente(YearMonth mes) {
        return inicioDelDia(mes.plusMonths(1).atDay(1));
    }

    /** Zona fijada (expuesta para tests y diagnóstico). */
    public static ZoneId zona() {
        return ZONA;
    }
}
