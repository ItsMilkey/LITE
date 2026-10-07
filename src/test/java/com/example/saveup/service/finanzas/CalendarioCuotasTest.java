package com.example.saveup.service.finanzas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para CalendarioCuotas (JUnit 5, sin Spring).
 */
class CalendarioCuotasTest {

    private CalendarioCuotas calendario;

    @BeforeEach
    void setUp() {
        calendario = new CalendarioCuotas();
    }

    @Test
    @DisplayName("T9a: primera 7-oct-2026, última 8-nov-2027 → 14 cuotas")
    void contarCuotas_catorce() {
        LocalDate primera = LocalDate.of(2026, 10, 7);
        LocalDate ultima = LocalDate.of(2027, 11, 8);

        int cuotas = calendario.contarCuotas(primera, ultima);

        // Vencimientos: 7-oct-2026, 7-nov-2026, ..., 7-oct-2027, 7-nov-2027
        // 7-nov-2027 ≤ 8-nov-2027 → se incluye → 14 cuotas
        assertEquals(14, cuotas);
    }

    @Test
    @DisplayName("T9b: primera = última → 1 cuota")
    void contarCuotas_mismaFecha() {
        LocalDate fecha = LocalDate.of(2026, 10, 7);
        assertEquals(1, calendario.contarCuotas(fecha, fecha));
    }

    @Test
    @DisplayName("T9c: última < primera → error")
    void contarCuotas_fechaInvertida() {
        LocalDate primera = LocalDate.of(2027, 1, 1);
        LocalDate ultima = LocalDate.of(2026, 12, 1);

        assertThrows(CalculoFinancieroException.class,
                () -> calendario.contarCuotas(primera, ultima));
    }

    @Test
    @DisplayName("T9d: resultado > 360 → error")
    void contarCuotas_excedeMaximo() {
        LocalDate primera = LocalDate.of(2000, 1, 1);
        LocalDate ultima = LocalDate.of(2100, 1, 1);

        assertThrows(CalculoFinancieroException.class,
                () -> calendario.contarCuotas(primera, ultima));
    }

    @Test
    @DisplayName("T9e: fechasVencimiento desde 31-ene-2027 → 31-ene, 28-feb, 31-mar")
    void fechasVencimiento_finDeMes() {
        LocalDate primera = LocalDate.of(2027, 1, 31);
        List<LocalDate> fechas = calendario.fechasVencimiento(primera, 3);

        assertEquals(3, fechas.size());
        assertEquals(LocalDate.of(2027, 1, 31), fechas.get(0));
        assertEquals(LocalDate.of(2027, 2, 28), fechas.get(1)); // feb 2027 no bisiesto
        assertEquals(LocalDate.of(2027, 3, 31), fechas.get(2));
    }

    @Test
    @DisplayName("T9f: fechasVencimiento desde 31-ene-2028 → 29-feb (bisiesto)")
    void fechasVencimiento_bisiesto() {
        LocalDate primera = LocalDate.of(2028, 1, 31);
        List<LocalDate> fechas = calendario.fechasVencimiento(primera, 3);

        assertEquals(3, fechas.size());
        assertEquals(LocalDate.of(2028, 1, 31), fechas.get(0));
        assertEquals(LocalDate.of(2028, 2, 29), fechas.get(1)); // 2028 es bisiesto
        assertEquals(LocalDate.of(2028, 3, 31), fechas.get(2));
    }

    @Test
    @DisplayName("fechasVencimiento: ancla desde día 15 → todos los 15")
    void fechasVencimiento_diaMedio() {
        LocalDate primera = LocalDate.of(2027, 1, 15);
        List<LocalDate> fechas = calendario.fechasVencimiento(primera, 4);

        assertEquals(4, fechas.size());
        assertEquals(LocalDate.of(2027, 1, 15), fechas.get(0));
        assertEquals(LocalDate.of(2027, 2, 15), fechas.get(1));
        assertEquals(LocalDate.of(2027, 3, 15), fechas.get(2));
        assertEquals(LocalDate.of(2027, 4, 15), fechas.get(3));
    }

    @Test
    @DisplayName("fechasVencimiento con cantidadCuotas = 1 → solo la primera")
    void fechasVencimiento_unaCuota() {
        LocalDate primera = LocalDate.of(2027, 6, 30);
        List<LocalDate> fechas = calendario.fechasVencimiento(primera, 1);

        assertEquals(1, fechas.size());
        assertEquals(primera, fechas.getFirst());
    }
}
