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
 * <p>La zona es la del negocio (Chile), no la del servidor: un movimiento hecho
 * el 31-oct a las 23:30 en America/Santiago pertenece a octubre aunque en UTC ya
 * sea 1-nov. Al ser una constante, la ventana no cambia si la JVM arranca en UTC
 * (Render) ni si se altera la zona por defecto durante la ejecución.
 */
public final class FechasMovimiento {

    /** Zona única del negocio con la que se interpretan fechas, meses y reportes. */
    private static final ZoneId ZONA = ZoneId.of("America/Santiago");

    private FechasMovimiento() {
    }

    /** Instante de inicio (00:00:00.000) del día indicado, en la zona del negocio. */
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

    /**
     * Día de calendario del negocio en el que ocurrió un instante. Se usa para
     * mostrar fechas en reportes sin depender de la zona del servidor.
     */
    public static LocalDate aFechaLocal(Date instante) {
        return instante.toInstant().atZone(ZONA).toLocalDate();
    }

    /** Fecha de hoy en la zona del negocio. */
    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    /** Mes actual en la zona del negocio (para los endpoints que omiten mes/año). */
    public static YearMonth mesActual() {
        return YearMonth.now(ZONA);
    }

    /** Zona fijada (expuesta para tests y diagnóstico). */
    public static ZoneId zona() {
        return ZONA;
    }
}
