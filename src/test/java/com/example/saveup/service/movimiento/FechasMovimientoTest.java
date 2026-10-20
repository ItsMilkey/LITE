package com.example.saveup.service.movimiento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T8a — La zona del negocio (America/Santiago) es una constante: los límites de
 * día y de mes no dependen de la zona de la máquina que corre los tests.
 */
class FechasMovimientoTest {

    private static Date instante(String iso) {
        return Date.from(Instant.parse(iso));
    }

    @Test
    @DisplayName("La zona fijada es America/Santiago")
    void zonaFijaEsSantiago() {
        assertEquals("America/Santiago", FechasMovimiento.zona().getId());
    }

    @Test
    @DisplayName("Inicio de día en horario de verano (UTC−3): 31-oct 00:00 = 31-oct 03:00Z")
    void inicioDelDiaHorarioDeVerano() {
        assertEquals(instante("2026-10-31T03:00:00Z"),
                FechasMovimiento.inicioDelDia(LocalDate.of(2026, 10, 31)));
    }

    @Test
    @DisplayName("Inicio del mes siguiente es el límite exclusivo: 1-nov 00:00 = 1-nov 03:00Z")
    void inicioDeMesSiguienteEsSemiabierto() {
        assertEquals(instante("2026-11-01T03:00:00Z"),
                FechasMovimiento.inicioDeMesSiguiente(YearMonth.of(2026, 10)));
    }

    @Test
    @DisplayName("Inicio de día en horario de invierno (UTC−4): 1-jul 00:00 = 1-jul 04:00Z")
    void inicioDelDiaHorarioDeInvierno() {
        assertEquals(instante("2026-07-01T04:00:00Z"),
                FechasMovimiento.inicioDeMes(YearMonth.of(2026, 7)));
    }

    @Test
    @DisplayName("23:30 del 31-oct en Santiago sigue siendo 31-oct aunque en UTC sea 1-nov")
    void aFechaLocalAtribuyeElDiaDelNegocio() {
        assertEquals(LocalDate.of(2026, 10, 31),
                FechasMovimiento.aFechaLocal(instante("2026-11-01T02:30:00Z")));
        assertEquals(LocalDate.of(2026, 11, 1),
                FechasMovimiento.aFechaLocal(instante("2026-11-01T03:30:00Z")));
    }
}
