package com.example.saveup.service.plan;

import com.example.saveup.service.plan.CalculadoraPlan.GuiaCalculo;
import com.example.saveup.service.plan.SugeridorRegla.MesSerie;
import com.example.saveup.service.plan.SugeridorRegla.Porcentajes;
import com.example.saveup.service.plan.SugeridorRegla.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Casos de T10d, sección 11 (sesión 1): D16 a D19, D22 y D26. El sugeridor es
 * puro: aquí no hay Spring ni base de datos.
 */
class SugeridorReglaTest {

    private final SugeridorRegla sugeridor = new SugeridorRegla();

    private static MesSerie mes(String id, String ingreso, String n, String d) {
        return new MesSerie(id, new BigDecimal(ingreso), new BigDecimal(n), new BigDecimal(d));
    }

    @Test
    @DisplayName("D16: 3 meses → actual 58,00/24,00/18,00 y sugerencia 60/25/15")
    void d16_tresMeses() {
        Resultado resultado = sugeridor.sugerir(List.of(
                mes("2026-07", "1000", "580", "220"),
                mes("2026-08", "1000", "600", "250"),
                mes("2026-09", "1000", "560", "240")), null);

        assertEquals(List.of("2026-07", "2026-08", "2026-09"), resultado.mesesAnalizados());
        assertEquals(new BigDecimal("58.00"), resultado.actual().necesidades());
        assertEquals(new BigDecimal("24.00"), resultado.actual().deseos());
        assertEquals(new BigDecimal("18.00"), resultado.actual().ahorro());
        assertEquals(new BigDecimal(60), resultado.sugerencia().necesidades());
        assertEquals(new BigDecimal(25), resultado.sugerencia().deseos());
        assertEquals(new BigDecimal(15), resultado.sugerencia().ahorro());
        assertFalse(resultado.gastoSuperaIngreso());
        assertFalse(resultado.igualALaGuia());
        assertNull(resultado.motivo());
    }

    @Test
    @DisplayName("D17: gasto supera ingreso → escala, sugerencia 60/40/0 y gastoSuperaIngreso verdadero")
    void d17_gastoSuperaIngreso() {
        Resultado resultado = sugeridor.sugerir(List.of(
                mes("2026-08", "1000", "700", "500"),
                mes("2026-09", "1000", "720", "480")), null);

        assertTrue(resultado.gastoSuperaIngreso());
        assertEquals(new BigDecimal("59.17"), resultado.actual().necesidades());
        assertEquals(new BigDecimal("40.83"), resultado.actual().deseos());
        assertEquals(new BigDecimal("0.00"), resultado.actual().ahorro());
        assertEquals(new BigDecimal(60), resultado.sugerencia().necesidades());
        assertEquals(new BigDecimal(40), resultado.sugerencia().deseos());
        assertEquals(new BigDecimal(0), resultado.sugerencia().ahorro());
        assertNull(resultado.motivo());
    }

    @Test
    @DisplayName("D18: medianas 50/30/20 con la misma configuración → igualALaGuia verdadero")
    void d18_igualaLaGuia() {
        Resultado resultado = sugeridor.sugerir(List.of(
                mes("2026-08", "1000", "500", "300"),
                mes("2026-09", "1000", "500", "300")),
                new GuiaCalculo(new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("20.00")));

        assertEquals(new BigDecimal(50), resultado.sugerencia().necesidades());
        assertEquals(new BigDecimal(30), resultado.sugerencia().deseos());
        assertEquals(new BigDecimal(20), resultado.sugerencia().ahorro());
        assertTrue(resultado.igualALaGuia());
    }

    @Test
    @DisplayName("D19: 52,5/27,5/20 → sugerencia 55/25/20; el empate de restos lo gana necesidades")
    void d19_empatePorOrden() {
        // Mediana de {50, 55} = 52,5 para necesidades y {25, 30} = 27,5 para deseos.
        Resultado resultado = sugeridor.sugerir(List.of(
                mes("2026-08", "1000", "500", "250"),
                mes("2026-09", "1000", "550", "300")), null);

        assertEquals(new BigDecimal("52.50"), resultado.actual().necesidades());
        assertEquals(new BigDecimal(55), resultado.sugerencia().necesidades());
        assertEquals(new BigDecimal(25), resultado.sugerencia().deseos());
        assertEquals(new BigDecimal(20), resultado.sugerencia().ahorro());
    }

    @Test
    @DisplayName("D22: 2 meses con 50 y 60 → mediana 55")
    void d22_medianaPromedio() {
        Resultado resultado = sugeridor.sugerir(List.of(
                mes("2026-08", "1000", "500", "100"),
                mes("2026-09", "1000", "600", "100")), null);

        assertEquals(new BigDecimal("55.00"), resultado.actual().necesidades());
    }

    @Test
    @DisplayName("Con 0 o 1 mes no hay sugerencia: motivo DATOS_INSUFICIENTES")
    void pocosMeses() {
        Resultado sinMeses = sugeridor.sugerir(List.of(), null);
        assertNull(sinMeses.sugerencia());
        assertEquals("DATOS_INSUFICIENTES", sinMeses.motivo());
        assertNull(sinMeses.actual().necesidades());
        assertFalse(sinMeses.igualALaGuia());

        Resultado unMes = sugeridor.sugerir(List.of(mes("2026-09", "1000", "500", "300")), null);
        assertNull(unMes.sugerencia());
        assertEquals("DATOS_INSUFICIENTES", unMes.motivo());
        assertEquals(new BigDecimal("50.00"), unMes.actual().necesidades());
    }

    @Test
    @DisplayName("D26 (propiedad): 200 combinaciones → la sugerencia suma 100 y son múltiplos de 5")
    void d26_propiedad() {
        Random aleatorio = new Random(20261102L); // semilla fija: reproducible
        for (int i = 0; i < 200; i++) {
            List<MesSerie> meses = new java.util.ArrayList<>();
            for (int j = 0; j < 2 + aleatorio.nextInt(3); j++) {
                BigDecimal ingreso = new BigDecimal(100 + aleatorio.nextInt(50_000));
                BigDecimal n = new BigDecimal(aleatorio.nextInt(200_000));
                BigDecimal d = new BigDecimal(aleatorio.nextInt(200_000));
                meses.add(new MesSerie("2026-0" + (1 + j), ingreso, n, d));
            }
            Resultado resultado = sugeridor.sugerir(meses, null);

            Porcentajes sugerencia = resultado.sugerencia();
            if (sugerencia == null) {
                // Con esta semilla siempre hay al menos 2 meses; nunca debería pasar.
                continue;
            }
            assertEquals(0, sugerencia.necesidades()
                    .add(sugerencia.deseos())
                    .add(sugerencia.ahorro())
                    .compareTo(new BigDecimal(100)),
                    "La sugerencia debe sumar 100: " + sugerencia);
            assertEquals(0, sugerencia.necesidades().remainder(new BigDecimal(5)).signum());
            assertEquals(0, sugerencia.deseos().remainder(new BigDecimal(5)).signum());
            assertEquals(0, sugerencia.ahorro().remainder(new BigDecimal(5)).signum());
            assertTrue(sugerencia.necesidades().signum() >= 0
                    && sugerencia.deseos().signum() >= 0
                    && sugerencia.ahorro().signum() >= 0);
        }
    }
}
