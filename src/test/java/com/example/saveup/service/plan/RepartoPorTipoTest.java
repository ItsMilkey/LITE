package com.example.saveup.service.plan;

import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaRef;
import com.example.saveup.service.plan.CalculadoraPlan.GuiaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.MovimientoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.RepartoTipoCalculado;
import com.example.saveup.service.plan.CalculadoraPlan.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El reparto por tipo del plan (T10d, sección 4): D12 a D15. Cada caso de
 * entrada/salida también vive en `docs/plan-vectores.json` (V19 a V25).
 */
class RepartoPorTipoTest {

    private final CalculadoraPlan calculadora = new CalculadoraPlan();

    private static final CategoriaRef VIVIENDA =
            new CategoriaRef(4L, "VIVIENDA_SUMINISTROS", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef ALIMENTACION =
            new CategoriaRef(2L, "ALIMENTACION_BASE", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef SUSCRIPCIONES =
            new CategoriaRef(8L, "SUSCRIPCIONES_DIGITAL", TipoPresupuesto.DESEO);
    private static final CategoriaRef OCASIONALES =
            new CategoriaRef(10L, "OTROS", TipoPresupuesto.OTROS);

    private static final GuiaCalculo GUIA_50_30_20 =
            new GuiaCalculo(new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("20.00"));

    private static PartidaCalculo partida(Long id, String nombre, CategoriaRef categoria, String monto) {
        return new PartidaCalculo(id, nombre, categoria, new BigDecimal(monto), id.intValue());
    }

    private static MovimientoCalculo gasto(String monto, CategoriaRef categoria) {
        return new MovimientoCalculo(new BigDecimal(monto).negate(),
                TipoMovimiento.GASTO_GENERAL, null, categoria);
    }

    @Test
    @DisplayName("D12: datos de referencia con guía 50/30/20 → +23,00 y −28,40 puntos")
    void d12_diferencias() {
        Resultado resultado = calculadora.calcular(
                new BigDecimal("1000000.00"), GUIA_50_30_20,
                List.of(partida(1L, "Arriendo", VIVIENDA, "400000.00"),
                        partida(2L, "Supermercado", ALIMENTACION, "250000.00"),
                        partida(3L, "Transporte", ALIMENTACION, "80000.00"),
                        partida(4L, "Netflix", SUSCRIPCIONES, "10000.00"),
                        partida(5L, "Spotify", SUSCRIPCIONES, "6000.00")),
                List.of());

        Map<TipoPresupuesto, RepartoTipoCalculado> reparto = resultado.repartoPorTipo().stream()
                .collect(Collectors.toMap(RepartoTipoCalculado::tipo, Function.identity()));
        assertEquals(3, reparto.size());

        RepartoTipoCalculado necesidades = reparto.get(TipoPresupuesto.NECESIDAD);
        assertEquals(new BigDecimal("730000.00"), necesidades.planificado());
        assertEquals(new BigDecimal("73.00"), necesidades.porcentajeDelIngreso());
        assertEquals(new BigDecimal("23.00"), necesidades.diferenciaPuntos());

        RepartoTipoCalculado deseos = reparto.get(TipoPresupuesto.DESEO);
        assertEquals(new BigDecimal("16000.00"), deseos.planificado());
        assertEquals(new BigDecimal("1.60"), deseos.porcentajeDelIngreso());
        assertEquals(new BigDecimal("-28.40"), deseos.diferenciaPuntos());
    }

    @Test
    @DisplayName("D13: sin ingreso de referencia y sin guía → porcentajes, guía y diferencias nulos")
    void d13_nulos() {
        Resultado resultado = calculadora.calcular(
                null, null,
                List.of(partida(1L, "Arriendo", VIVIENDA, "400000.00")),
                List.of(gasto("100.00", OCASIONALES)));

        assertNull(resultado.guia());
        for (RepartoTipoCalculado fila : resultado.repartoPorTipo()) {
            assertNull(fila.porcentajeDelIngreso(), fila.tipo().name());
            assertNull(fila.porcentajeRealDelIngresoReal(), fila.tipo().name());
            assertNull(fila.diferenciaPuntos(), fila.tipo().name());
        }
    }

    @Test
    @DisplayName("D13b: sin ingreso real → porcentajeRealDelIngresoReal nulo aunque el plan tenga ingreso de referencia")
    void d13b_sinIngresoReal() {
        Resultado resultado = calculadora.calcular(
                new BigDecimal("1000000.00"), GUIA_50_30_20,
                List.of(partida(1L, "Arriendo", VIVIENDA, "400000.00")),
                List.of());

        assertEquals(new BigDecimal("40.00"), resultado.repartoPorTipo().get(0).porcentajeDelIngreso());
        assertNull(resultado.repartoPorTipo().get(0).porcentajeRealDelIngresoReal());
        assertEquals(new BigDecimal("-10.00"), resultado.repartoPorTipo().get(0).diferenciaPuntos());
    }

    @Test
    @DisplayName("D14: el real se suma por tipo efectivo, no por el tipo por defecto")
    void d14_realPorTipoEfectivo() {
        // Suscripciones reclasificada como NECESIDAD para este usuario.
        CategoriaRef preferida = new CategoriaRef(8L, "SUSCRIPCIONES_DIGITAL", TipoPresupuesto.NECESIDAD);
        Resultado resultado = calculadora.calcular(
                new BigDecimal("1000000.00"), GUIA_50_30_20,
                List.of(partida(1L, "Netflix", preferida, "10000.00")),
                List.of(gasto("8000.00", preferida)));

        RepartoTipoCalculado necesidades = repartoPor(resultado, TipoPresupuesto.NECESIDAD);
        assertEquals(new BigDecimal("10000.00"), necesidades.planificado());
        assertEquals(new BigDecimal("8000.00"), necesidades.real());
        RepartoTipoCalculado deseos = repartoPor(resultado, TipoPresupuesto.DESEO);
        assertEquals(new BigDecimal("0.00"), deseos.planificado());
        assertEquals(new BigDecimal("0.00"), deseos.real());
    }

    @Test
    @DisplayName("D15: partida y gasto de categoría OTROS aparecen en el tipo OTROS, sin diferencia de puntos")
    void d15_otros() {
        Resultado resultado = calculadora.calcular(
                new BigDecimal("1000000.00"), GUIA_50_30_20,
                List.of(partida(1L, "Varios", OCASIONALES, "20000.00")),
                List.of(gasto("5000.00", OCASIONALES)));

        RepartoTipoCalculado otros = repartoPor(resultado, TipoPresupuesto.OTROS);
        assertEquals(new BigDecimal("20000.00"), otros.planificado());
        assertEquals(new BigDecimal("5000.00"), otros.real());
        assertNull(otros.diferenciaPuntos());
        assertTrue(resultado.repartoPorTipo().stream()
                .noneMatch(fila -> fila.tipo() == TipoPresupuesto.AHORRO));
    }

    private static RepartoTipoCalculado repartoPor(Resultado resultado, TipoPresupuesto tipo) {
        return resultado.repartoPorTipo().stream()
                .filter(fila -> fila.tipo() == tipo)
                .findFirst()
                .orElseThrow();
    }
}
