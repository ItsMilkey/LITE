package com.example.saveup.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T5 — Bordes de los umbrales 80 y 100. Clase pura: no requiere Spring ni BD.
 */
class EstadoEjecucionTest {

    @Test
    @DisplayName("Por debajo del 80% el estado es OK")
    void porDebajoDe80_esOk() {
        assertEquals(EstadoEjecucion.OK, EstadoEjecucion.desdePorcentaje(new BigDecimal("79.99")));
        assertEquals(EstadoEjecucion.OK, EstadoEjecucion.desdePorcentaje(new BigDecimal("0.00")));
        assertEquals(EstadoEjecucion.OK, EstadoEjecucion.desdePorcentaje(null));
    }

    @Test
    @DisplayName("Exactamente 80% entra en ALERTA, pero 79.99% no")
    void borde80_esAlerta() {
        assertEquals(EstadoEjecucion.ALERTA, EstadoEjecucion.desdePorcentaje(new BigDecimal("80")));
        assertEquals(EstadoEjecucion.ALERTA, EstadoEjecucion.desdePorcentaje(new BigDecimal("80.00")));
        assertEquals(EstadoEjecucion.OK, EstadoEjecucion.desdePorcentaje(new BigDecimal("79.99")));
    }

    @Test
    @DisplayName("Entre 80% y 100% sigue en ALERTA")
    void entre80y100_esAlerta() {
        assertEquals(EstadoEjecucion.ALERTA, EstadoEjecucion.desdePorcentaje(new BigDecimal("99.99")));
    }

    @Test
    @DisplayName("Exactamente 100% pasa a EXCEDIDO")
    void borde100_esExcedido() {
        assertEquals(EstadoEjecucion.EXCEDIDO, EstadoEjecucion.desdePorcentaje(new BigDecimal("100")));
        assertEquals(EstadoEjecucion.EXCEDIDO, EstadoEjecucion.desdePorcentaje(new BigDecimal("100.00")));
        assertEquals(EstadoEjecucion.EXCEDIDO, EstadoEjecucion.desdePorcentaje(new BigDecimal("120.50")));
        assertEquals(EstadoEjecucion.ALERTA, EstadoEjecucion.desdePorcentaje(new BigDecimal("99.99")));
    }
}
