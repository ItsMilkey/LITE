package com.example.saveup.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T10a — Bordes de los umbrales 80 y 100 con comparación por multiplicación
 * cruzada. Clase pura: no requiere Spring ni BD.
 */
class EstadoEjecucionTest {

    @Test
    @DisplayName("A09: real 79,99 sobre planificado 100 → BAJO_EL_PLAN")
    void porDebajoDe80_esBajoElPlan() {
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("79.99"), new BigDecimal("100")));
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("0.00"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("A10: real 80 sobre 100 → CERCA_DEL_PLAN")
    void borde80_esCercaDelPlan() {
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("80"), new BigDecimal("100")));
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("80.00"), new BigDecimal("100.00")));
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("79.99"), new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Entre 80 % y 100 % sigue en CERCA_DEL_PLAN")
    void entre80y100_esCercaDelPlan() {
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("99.99"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("A11: real 100 sobre 100 → CERCA_DEL_PLAN (100 % inclusive)")
    void borde100_esCercaDelPlan() {
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("100"), new BigDecimal("100")));
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("100.00"), new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("A12: real 100,01 sobre 100 → SOBRE_EL_PLAN")
    void sobre100_esSobreElPlan() {
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("100.01"), new BigDecimal("100")));
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("120.50"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("A13: real 199.999 sobre 250.000 → BAJO_EL_PLAN (la multiplicación cruzada no redondea)")
    void sinRedondeo_esBajoElPlan() {
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("199.999"), new BigDecimal("250.000")));
    }

    @Test
    @DisplayName("Plan 0: SOBRE_EL_PLAN si hay gasto real y BAJO_EL_PLAN si no lo hay")
    void planCero_segunGastoReal() {
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, EstadoEjecucion.desde(
                new BigDecimal("1.00"), BigDecimal.ZERO));
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Valores nulos se tratan como cero sin lanzar excepciones")
    void nulos_seTratanComoCero() {
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(null, new BigDecimal("100")));
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(BigDecimal.ZERO, null));
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, EstadoEjecucion.desde(null, null));
    }
}
