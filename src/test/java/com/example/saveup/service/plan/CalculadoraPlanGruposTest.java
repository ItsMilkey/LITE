package com.example.saveup.service.plan;

import com.example.saveup.model.enums.EstadoCuota;
import com.example.saveup.model.enums.EstadoEjecucion;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaRef;
import com.example.saveup.service.plan.CalculadoraPlan.GrupoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.GrupoResultado;
import com.example.saveup.service.plan.CalculadoraPlan.MovimientoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10d (sesión 2) — calculadora pura: grupos, estado de cuota e imputación por
 * deuda vinculada. Casos D30–D32, D35, D41, D43 y D46, sin Spring ni base de
 * datos.
 */
class CalculadoraPlanGruposTest {

    private static final CategoriaRef VIVIENDA =
            new CategoriaRef(4L, "VIVIENDA_SUMINISTROS", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef ALIMENTACION =
            new CategoriaRef(2L, "ALIMENTACION_BASE", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef TRANSPORTE =
            new CategoriaRef(3L, "TRANSPORTE_MOVILIDAD", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef SUSCRIPCIONES =
            new CategoriaRef(8L, "SUSCRIPCIONES_DIGITAL", TipoPresupuesto.DESEO);
    private static final CategoriaRef DEUDAS =
            new CategoriaRef(9L, "DEUDAS", TipoPresupuesto.NECESIDAD);

    private static final GrupoCalculo FIJOS = new GrupoCalculo(1L, "Fijos", 1);
    private static final GrupoCalculo VARIABLE = new GrupoCalculo(2L, "Variable", 2);

    private static final BigDecimal INGRESO = dinero("1000000.00");

    private final CalculadoraPlan calculadora = new CalculadoraPlan();

    // ─────────────────────────── grupos ───────────────────────────

    @Test
    @DisplayName("D30: dos grupos dan Fijos 416.000 (55,76 %) y Variable 330.000 (44,24 %)")
    void d30_dosGrupos() {
        Resultado resultado = calculadora.calcular(INGRESO, null, List.of(FIJOS, VARIABLE),
                partidasDeReferenciaConGrupos(), List.of());

        assertEquals(2, resultado.grupos().size());
        GrupoResultado fijos = resultado.grupos().get(0);
        assertEquals(FIJOS.id(), fijos.id());
        assertEquals("Fijos", fijos.nombre());
        assertEquals(1, fijos.orden());
        assertMoney("416000.00", fijos.planificado());
        assertMoney("55.76", fijos.porcentajeDelPlanificado());
        assertMoney("0.00", fijos.real());
        assertEquals(List.of(1L, 4L, 5L), fijos.partidaIds());

        GrupoResultado variable = resultado.grupos().get(1);
        assertEquals(VARIABLE.id(), variable.id());
        assertMoney("330000.00", variable.planificado());
        assertMoney("44.24", variable.porcentajeDelPlanificado());
        assertMoney("0.00", variable.real());
        assertEquals(List.of(2L, 3L), variable.partidaIds());
    }

    @Test
    @DisplayName("D31: un plan con total 0 deja porcentajeDelPlanificado nulo")
    void d31_totalCero() {
        Resultado resultado = calculadora.calcular(dinero("0.00"), null, List.of(FIJOS),
                List.of(partida(1L, "Arriendo", VIVIENDA, "0.00", 1, FIJOS.id(), null)),
                List.of());

        GrupoResultado fijos = resultado.grupos().getFirst();
        assertMoney("0.00", fijos.planificado());
        assertNull(fijos.porcentajeDelPlanificado());
    }

    @Test
    @DisplayName("D32: \"Sin grupo\" aparece solo con partidas sin grupo y va al final")
    void d32_sinGrupo() {
        Resultado conSueltas = calculadora.calcular(INGRESO, null, List.of(FIJOS),
                List.of(partida(1L, "Arriendo", VIVIENDA, "400000.00", 1, FIJOS.id(), null),
                        partida(2L, "Supermercado", ALIMENTACION, "250000.00", 2, null, null)),
                List.of());

        assertEquals(2, conSueltas.grupos().size());
        GrupoResultado sinGrupo = conSueltas.grupos().get(1);
        assertNull(sinGrupo.id());
        assertEquals("Sin grupo", sinGrupo.nombre());
        assertNull(sinGrupo.orden());
        assertMoney("250000.00", sinGrupo.planificado());
        assertMoney("38.46", sinGrupo.porcentajeDelPlanificado());
        assertMoney("0.00", sinGrupo.real());
        assertEquals(List.of(2L), sinGrupo.partidaIds());

        Resultado sinSueltas = calculadora.calcular(INGRESO, null, List.of(FIJOS),
                List.of(partida(1L, "Arriendo", VIVIENDA, "400000.00", 1, FIJOS.id(), null)),
                List.of());

        assertEquals(1, sinSueltas.grupos().size());
        assertEquals(FIJOS.id(), sinSueltas.grupos().getFirst().id());
    }

    @Test
    @DisplayName("D35: un grupo sin partidas aparece con planificado 0")
    void d35_grupoVacio() {
        GrupoCalculo vacio = new GrupoCalculo(3L, "Vacío", 3);
        Resultado resultado = calculadora.calcular(INGRESO, null, List.of(FIJOS, vacio),
                List.of(partida(1L, "Arriendo", VIVIENDA, "400000.00", 1, FIJOS.id(), null)),
                List.of());

        assertEquals(2, resultado.grupos().size());
        GrupoResultado grupoVacio = resultado.grupos().get(1);
        assertEquals(3L, grupoVacio.id());
        assertEquals("Vacío", grupoVacio.nombre());
        assertMoney("0.00", grupoVacio.planificado());
        assertMoney("0.00", grupoVacio.porcentajeDelPlanificado());
        assertMoney("0.00", grupoVacio.real());
        assertTrue(grupoVacio.partidaIds().isEmpty());
    }

    // ─────────────────────────── estado de cuota ───────────────────────────

    @Test
    @DisplayName("D41: pago de 20.000 es PARCIAL; el pago completo es PAGADA; sin pago es PENDIENTE")
    void d41_estadoCuota() {
        PartidaResultado parcial = unicaPartida(pagoDeuda("20000.00"));
        assertMoney("20000.00", parcial.real());
        assertMoney("23500.00", parcial.diferencia());
        assertEquals(EstadoCuota.PARCIAL, parcial.estadoCuota());

        PartidaResultado pagada = unicaPartida(pagoDeuda("43500.00"));
        assertEquals(EstadoCuota.PAGADA, pagada.estadoCuota());

        PartidaResultado pendiente = unicaPartida(null);
        assertMoney("0.00", pendiente.real());
        assertEquals(EstadoCuota.PENDIENTE, pendiente.estadoCuota());
    }

    @Test
    @DisplayName("D46: la partida vinculada calcula además el estado neutro de ejecución")
    void d46_estadoNeutroAdemasDelEstadoCuota() {
        PartidaResultado parcial = unicaPartida(pagoDeuda("20000.00"));
        assertEquals(EstadoCuota.PARCIAL, parcial.estadoCuota());
        assertEquals(EstadoEjecucion.BAJO_EL_PLAN, parcial.estado());

        PartidaResultado pagada = unicaPartida(pagoDeuda("50000.00"));
        assertEquals(EstadoCuota.PAGADA, pagada.estadoCuota());
        assertEquals(EstadoEjecucion.SOBRE_EL_PLAN, pagada.estado());
    }

    // ─────────────────────────── imputación por deuda ───────────────────────────

    @Test
    @DisplayName("D43: un PAGO_DEUDA sin partida_id va a la partida vinculada, antes que la categoría")
    void d43_imputacionPorDeuda() {
        PartidaCalculo cuota = partida(20L, "Cuota crédito", DEUDAS, "43500.00", 1, FIJOS.id(), 77L);
        PartidaCalculo otraCuota = partida(21L, "Otra cuota", DEUDAS, "10000.00", 2, FIJOS.id(), null);

        // Sin partida_id: la categoría DEUDAS tiene dos partidas (sería ambigua),
        // pero el vínculo por deuda manda.
        Resultado porDeuda = calculadora.calcular(INGRESO, null, List.of(FIJOS),
                List.of(cuota, otraCuota),
                List.of(new MovimientoCalculo(dinero("-20000.00"), TipoMovimiento.PAGO_DEUDA,
                        null, 77L, DEUDAS)));

        assertMoney("20000.00", porDeuda.partidas().get(0).real());
        assertMoney("0.00", porDeuda.partidas().get(1).real());
        assertMoney("0.00", porDeuda.resumen().sinPlanificar().monto());

        // El partida_id explícito sigue mandando sobre el vínculo por deuda.
        Resultado porPartida = calculadora.calcular(INGRESO, null, List.of(FIJOS),
                List.of(cuota, otraCuota),
                List.of(new MovimientoCalculo(dinero("-5000.00"), TipoMovimiento.PAGO_DEUDA,
                        otraCuota.id(), 77L, DEUDAS)));

        assertMoney("0.00", porPartida.partidas().get(0).real());
        assertMoney("5000.00", porPartida.partidas().get(1).real());
    }

    // ─────────────────────────── helpers ───────────────────────────

    private Resultado calcularConCuota(MovimientoCalculo movimiento) {
        return calculadora.calcular(INGRESO, null, List.of(FIJOS),
                List.of(partida(20L, "Cuota crédito", DEUDAS, "43500.00", 1, FIJOS.id(), 77L)),
                movimiento == null ? List.of() : List.of(movimiento));
    }

    private PartidaResultado unicaPartida(MovimientoCalculo movimiento) {
        Resultado resultado = calcularConCuota(movimiento);
        assertEquals(1, resultado.partidas().size());
        return resultado.partidas().getFirst();
    }

    private static MovimientoCalculo pagoDeuda(String monto) {
        return new MovimientoCalculo(dinero(monto).negate(), TipoMovimiento.PAGO_DEUDA,
                null, 77L, DEUDAS);
    }

    private static List<PartidaCalculo> partidasDeReferenciaConGrupos() {
        return List.of(
                partida(1L, "Arriendo", VIVIENDA, "400000.00", 1, FIJOS.id(), null),
                partida(2L, "Supermercado", ALIMENTACION, "250000.00", 2, VARIABLE.id(), null),
                partida(3L, "Transporte", TRANSPORTE, "80000.00", 3, VARIABLE.id(), null),
                partida(4L, "Netflix", SUSCRIPCIONES, "10000.00", 4, FIJOS.id(), null),
                partida(5L, "Spotify", SUSCRIPCIONES, "6000.00", 5, FIJOS.id(), null));
    }

    private static PartidaCalculo partida(Long id, String nombre, CategoriaRef categoria,
                                          String monto, int orden, Long grupoId, Long deudaId) {
        return new PartidaCalculo(id, nombre, categoria, dinero(monto), orden, grupoId, deudaId);
    }

    private static BigDecimal dinero(String valor) {
        return new BigDecimal(valor).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void assertMoney(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                "Se esperaba " + esperado + " pero fue " + real);
    }
}
