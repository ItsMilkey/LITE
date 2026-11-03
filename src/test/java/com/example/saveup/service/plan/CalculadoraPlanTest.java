package com.example.saveup.service.plan;

import com.example.saveup.model.enums.EstadoEjecucion;
import com.example.saveup.model.enums.MotivoSinPlanificar;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaRef;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.MovimientoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10c — calculadora pura del plan (C01–C20 y C22). Sin Spring y sin base de
 * datos: entra el ingreso de referencia, las partidas y los movimientos, y
 * sale el resumen, las partidas y el desglose por categoría.
 */
class CalculadoraPlanTest {

    private static final CategoriaRef VIVIENDA = new CategoriaRef(4L, "VIVIENDA_SUMINISTROS", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef ALIMENTACION = new CategoriaRef(2L, "ALIMENTACION_BASE", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef TRANSPORTE = new CategoriaRef(3L, "TRANSPORTE_MOVILIDAD", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef SUSCRIPCIONES = new CategoriaRef(8L, "SUSCRIPCIONES_DIGITAL", TipoPresupuesto.DESEO);
    private static final CategoriaRef SALUD = new CategoriaRef(5L, "SALUD_BIENESTAR", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef DEUDAS = new CategoriaRef(9L, "DEUDAS", TipoPresupuesto.NECESIDAD);

    private static final PartidaCalculo ARRIENDO = partida(1L, "Arriendo", VIVIENDA, "400000.00", 1);
    private static final PartidaCalculo SUPERMERCADO = partida(2L, "Supermercado", ALIMENTACION, "250000.00", 2);
    private static final PartidaCalculo TRANSPORTE_PARTIDA = partida(3L, "Transporte", TRANSPORTE, "80000.00", 3);
    private static final PartidaCalculo NETFLIX = partida(4L, "Netflix", SUSCRIPCIONES, "10000.00", 4);
    private static final PartidaCalculo SPOTIFY = partida(5L, "Spotify", SUSCRIPCIONES, "6000.00", 5);

    private final CalculadoraPlan calculadora = new CalculadoraPlan();

    @Test
    @DisplayName("C01: sin plan, sin ingreso y sin movimientos deja todo en cero y los nulos en remanente/margen")
    void c01_sinDatos() {
        Resultado resultado = calculadora.calcular(null, List.of(), List.of());

        assertMoney("0.00", resultado.resumen().planificadoTotal());
        assertNull(resultado.resumen().remanenteLibre());
        assertNull(resultado.resumen().margenSeguridadPct());
        assertFalse(resultado.resumen().excedeIngreso());
        assertMoney("0.00", resultado.resumen().ingresoReal());
        assertMoney("0.00", resultado.resumen().gastoRealTotal());
        assertMoney("0.00", resultado.resumen().disponibleReal());
        assertMoney("0.00", resultado.resumen().ahorroRegistrado());
        assertMoney("0.00", resultado.resumen().sinPlanificar().monto());
        assertEquals(0, resultado.resumen().sinPlanificar().movimientos());
        assertTrue(resultado.resumen().sinPlanificar().porMotivo().isEmpty());
        assertTrue(resultado.partidas().isEmpty());
        assertTrue(resultado.porCategoria().isEmpty());
    }

    @Test
    @DisplayName("C02: ingreso de 1.000.000 sin partidas deja remanente 1.000.000 y margen 100,00")
    void c02_ingresoSinPartidas() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"), List.of(), List.of());

        assertMoney("0.00", resultado.resumen().planificadoTotal());
        assertMoney("1000000.00", resultado.resumen().remanenteLibre());
        assertMoney("100.00", resultado.resumen().margenSeguridadPct());
        assertFalse(resultado.resumen().excedeIngreso());
    }

    @Test
    @DisplayName("C03: datos de referencia dan 746.000 planificados, 254.000 remanentes y margen 25,40")
    void c03_datosDeReferencia() {
        Resultado resultado = referencia();

        assertMoney("746000.00", resultado.resumen().planificadoTotal());
        assertMoney("254000.00", resultado.resumen().remanenteLibre());
        assertMoney("25.40", resultado.resumen().margenSeguridadPct());
        assertFalse(resultado.resumen().excedeIngreso());
    }

    @Test
    @DisplayName("C04: subir Supermercado a 300.000 deja planificado 796.000 y margen 20,40")
    void c04_subirSupermercado() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(ARRIENDO, partida(2L, "Supermercado", ALIMENTACION, "300000.00", 2),
                        TRANSPORTE_PARTIDA, NETFLIX, SPOTIFY),
                List.of());

        assertMoney("796000.00", resultado.resumen().planificadoTotal());
        assertMoney("204000.00", resultado.resumen().remanenteLibre());
        assertMoney("20.40", resultado.resumen().margenSeguridadPct());
    }

    @Test
    @DisplayName("C05: subir Arriendo a 700.000 excede el ingreso y deja margen -4,60")
    void c05_excederIngreso() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(partida(1L, "Arriendo", VIVIENDA, "700000.00", 1), SUPERMERCADO,
                        TRANSPORTE_PARTIDA, NETFLIX, SPOTIFY),
                List.of());

        assertMoney("1046000.00", resultado.resumen().planificadoTotal());
        assertMoney("-46000.00", resultado.resumen().remanenteLibre());
        assertMoney("-4.60", resultado.resumen().margenSeguridadPct());
        assertTrue(resultado.resumen().excedeIngreso());
    }

    @Test
    @DisplayName("C06: ingreso de referencia 0 con partidas deja el margen nulo y el remanente negativo")
    void c06_ingresoCero() {
        Resultado resultado = calculadora.calcular(dinero("0.00"),
                List.of(ARRIENDO, SUPERMERCADO), List.of());

        assertMoney("-650000.00", resultado.resumen().remanenteLibre());
        assertNull(resultado.resumen().margenSeguridadPct());
    }

    @Test
    @DisplayName("C07: sin ingreso de referencia el remanente y el margen son nulos y no se excede")
    void c07_sinIngreso() {
        Resultado resultado = calculadora.calcular(null,
                List.of(ARRIENDO, SUPERMERCADO), List.of());

        assertNull(resultado.resumen().remanenteLibre());
        assertNull(resultado.resumen().margenSeguridadPct());
        assertFalse(resultado.resumen().excedeIngreso());
    }

    @Test
    @DisplayName("C08: 199.999 de 250.000 da 79,99 % y BAJO_EL_PLAN")
    void c08_bajo() {
        PartidaResultado supermercado = partidaConReal(SUPERMERCADO, "199999.00");

        assertMoney("79.99", supermercado.porcentajeUsado());
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, supermercado.estado());
    }

    @Test
    @DisplayName("C09: 200.000 de 250.000 da 80,00 % y CERCA_DEL_PLAN")
    void c09_cerca() {
        PartidaResultado supermercado = partidaConReal(SUPERMERCADO, "200000.00");

        assertMoney("80.00", supermercado.porcentajeUsado());
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, supermercado.estado());
    }

    @Test
    @DisplayName("C10: 250.000 de 250.000 da 100,00 % y CERCA_DEL_PLAN")
    void c10_justo() {
        PartidaResultado supermercado = partidaConReal(SUPERMERCADO, "250000.00");

        assertMoney("100.00", supermercado.porcentajeUsado());
        assertEquals(EstadoEjecucion.CERCA_DEL_PLAN, supermercado.estado());
    }

    @Test
    @DisplayName("C11: 250.001 de 250.000 trunca a 100,00 pero el estado es SOBRE_EL_PLAN")
    void c11_sobre() {
        PartidaResultado supermercado = partidaConReal(SUPERMERCADO, "250001.00");

        assertMoney("100.00", supermercado.porcentajeUsado());
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, supermercado.estado());
    }

    @Test
    @DisplayName("C12: 262.500 de 250.000 da 105,00 %, SOBRE y diferencia -12.500")
    void c12_diferencia() {
        PartidaResultado supermercado = partidaConReal(SUPERMERCADO, "262500.00");

        assertMoney("105.00", supermercado.porcentajeUsado());
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, supermercado.estado());
        assertMoney("-12500.00", supermercado.diferencia());
    }

    @Test
    @DisplayName("C13: monto 0 con real 0 da porcentaje nulo y BAJO; con real 1.000 da SOBRE")
    void c13_montoCero() {
        PartidaResultado sinGasto = partidaConReal(partida(10L, "Sin gasto", SALUD, "0.00", 1), "0.00");
        PartidaResultado conGasto = partidaConReal(partida(11L, "Con gasto", SALUD, "0.00", 2), "1000.00");

        assertNull(sinGasto.porcentajeUsado());
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, sinGasto.estado());
        assertNull(conGasto.porcentajeUsado());
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, conGasto.estado());
    }

    @Test
    @DisplayName("C14: un gasto con partida_id de Netflix se imputa a Netflix aunque comparta categoría con Spotify")
    void c14_imputacionExplicita() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(NETFLIX, SPOTIFY),
                List.of(gasto("10000.00", NETFLIX.id(), SUSCRIPCIONES)));

        assertMoney("10000.00", resultado.partidas().get(0).real());
        assertMoney("0.00", resultado.partidas().get(1).real());
        assertMoney("0.00", resultado.resumen().sinPlanificar().monto());
    }

    @Test
    @DisplayName("C15: un gasto sin partida_id con una sola partida de su categoría se imputa a ella")
    void c15_imputacionPorCategoriaUnica() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(TRANSPORTE_PARTIDA),
                List.of(gasto("20000.00", null, TRANSPORTE)));

        assertMoney("20000.00", resultado.partidas().get(0).real());
        assertMoney("0.00", resultado.resumen().sinPlanificar().monto());
    }

    @Test
    @DisplayName("C16: un gasto sin partida_id en una categoría con dos partidas queda CATEGORIA_AMBIGUA")
    void c16_categoriaAmbigua() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(NETFLIX, SPOTIFY),
                List.of(gasto("10000.00", null, SUSCRIPCIONES)));

        assertMoney("0.00", resultado.partidas().get(0).real());
        assertMoney("0.00", resultado.partidas().get(1).real());
        assertMoney("10000.00", resultado.resumen().sinPlanificar().monto());
        assertEquals(1, resultado.resumen().sinPlanificar().movimientos());
        assertEquals(MotivoSinPlanificar.CATEGORIA_AMBIGUA,
                resultado.resumen().sinPlanificar().porMotivo().get(0).motivo());
    }

    @Test
    @DisplayName("C17: un gasto de una categoría sin partidas queda SIN_PARTIDA y aparece en porCategoria con planificado 0")
    void c17_sinPartida() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(ARRIENDO),
                List.of(gasto("12000.00", null, SALUD)));

        assertEquals(MotivoSinPlanificar.SIN_PARTIDA,
                resultado.resumen().sinPlanificar().porMotivo().get(0).motivo());
        CategoriaResultado salud = resultado.porCategoria().stream()
                .filter(categoria -> categoria.categoriaId().equals(SALUD.id()))
                .findFirst()
                .orElseThrow();
        assertMoney("0.00", salud.planificado());
        assertMoney("12000.00", salud.real());
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, salud.estado());
    }

    @Test
    @DisplayName("C18: un PAGO_DEUDA con partida de categoría DEUDAS se imputa a ella")
    void c18_pagoDeuda() {
        PartidaCalculo pagoDeuda = partida(20L, "Cuota crédito", DEUDAS, "50000.00", 1);

        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(pagoDeuda),
                List.of(new MovimientoCalculo(dinero("-50000.00"), TipoMovimiento.PAGO_DEUDA,
                        pagoDeuda.id(), DEUDAS)));

        assertMoney("50000.00", resultado.partidas().get(0).real());
        assertMoney("50000.00", resultado.resumen().gastoRealTotal());
    }

    @Test
    @DisplayName("C19: INGRESO_GENERAL, ABONO_META y RETIRO_META no se imputan; los abonos suman a ahorroRegistrado")
    void c19_tiposNoImputables() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(ARRIENDO),
                List.of(
                        new MovimientoCalculo(dinero("900000.00"), TipoMovimiento.INGRESO_GENERAL, null, null),
                        new MovimientoCalculo(dinero("-80000.00"), TipoMovimiento.ABONO_META, null, null),
                        new MovimientoCalculo(dinero("30000.00"), TipoMovimiento.RETIRO_META, null, null)));

        assertMoney("0.00", resultado.partidas().get(0).real());
        assertMoney("900000.00", resultado.resumen().ingresoReal());
        assertMoney("80000.00", resultado.resumen().ahorroRegistrado());
        assertMoney("0.00", resultado.resumen().gastoRealTotal());
        assertMoney("0.00", resultado.resumen().sinPlanificar().monto());
    }

    @Test
    @DisplayName("C20: dos partidas de Suscripciones (10.000 y 6.000) con gasto real 10.000 dan 16.000 planificados y 62,50 % BAJO")
    void c20_porCategoria() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(NETFLIX, SPOTIFY),
                List.of(gasto("10000.00", NETFLIX.id(), SUSCRIPCIONES)));

        CategoriaResultado suscripciones = resultado.porCategoria().get(0);
        assertMoney("16000.00", suscripciones.planificado());
        assertMoney("10000.00", suscripciones.real());
        assertMoney("62.50", suscripciones.porcentajeUsado());
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, suscripciones.estado());
    }

    @Test
    @DisplayName("C22: el ingreso real no altera el plan de referencia y disponibleReal usa el ingreso real")
    void c22_ingresoRealNoAlteraElPlan() {
        Resultado resultado = calculadora.calcular(dinero("1000000.00"),
                List.of(ARRIENDO, SUPERMERCADO),
                List.of(new MovimientoCalculo(dinero("900000.00"), TipoMovimiento.INGRESO_GENERAL, null, null),
                        gasto("100000.00", null, VIVIENDA)));

        assertMoney("650000.00", resultado.resumen().planificadoTotal());
        assertMoney("350000.00", resultado.resumen().remanenteLibre());
        assertMoney("900000.00", resultado.resumen().ingresoReal());
        assertMoney("100000.00", resultado.resumen().gastoRealTotal());
        assertMoney("800000.00", resultado.resumen().disponibleReal());
    }

    // ─────────────────────────── helpers ───────────────────────────

    private Resultado referencia() {
        return calculadora.calcular(dinero("1000000.00"),
                List.of(ARRIENDO, SUPERMERCADO, TRANSPORTE_PARTIDA, NETFLIX, SPOTIFY),
                List.of());
    }

    private PartidaResultado partidaConReal(PartidaCalculo partida, String real) {
        Resultado resultado = calculadora.calcular(null, List.of(partida),
                List.of(gasto(real, partida.id(), partida.categoria())));
        return resultado.partidas().get(0);
    }

    private static PartidaCalculo partida(Long id, String nombre, CategoriaRef categoria,
                                          String monto, int orden) {
        return new PartidaCalculo(id, nombre, categoria, dinero(monto), orden);
    }

    private static MovimientoCalculo gasto(String monto, Long partidaId, CategoriaRef categoria) {
        return new MovimientoCalculo(dinero(monto).negate(), TipoMovimiento.GASTO_GENERAL,
                partidaId, categoria);
    }

    private static BigDecimal dinero(String valor) {
        return new BigDecimal(valor);
    }

    private static void assertMoney(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                "Se esperaba " + esperado + " pero llegó " + real);
    }
}
