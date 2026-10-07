package com.example.saveup.service.finanzas;

import com.example.saveup.model.enums.ModalidadCalculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para el motor financiero (JUnit 5, sin Spring).
 */
class CalculoFinancieroServiceTest {

    private CalculoFinancieroService service;

    @BeforeEach
    void setUp() {
        service = new CalculoFinancieroService();
    }

    // ──────────────────────── Test 1: SIN_INTERES exacta ────────────────────────

    @Test
    @DisplayName("T1: SIN_INTERES, 100.000 en 4 cuotas → 4 × 25.000")
    void sinInteres_cuotasExactas() {
        CondicionesCredito c = new CondicionesCredito(
                ModalidadCalculo.SIN_INTERES,
                new BigDecimal("100000"), 4, null, null);

        ResultadoCalculo r = service.calcular(c);

        assertEquals(new BigDecimal("25000.00"), r.valorCuota());
        assertEquals(4, r.tabla().size());
        for (CuotaAmortizacion fila : r.tabla()) {
            assertEquals(new BigDecimal("25000.00"), fila.cuota());
            assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), fila.interes());
        }
        assertEquals(new BigDecimal("100000.00"), r.montoTotal());
        assertEquals(new BigDecimal("0.00"), r.interesesYCostos());
        assertEquals(0, r.cargaAnualEquivalente().compareTo(BigDecimal.ZERO));
    }

    // ──────────────────────── Test 2: SIN_INTERES con centavos ────────────────────────

    @Test
    @DisplayName("T2: SIN_INTERES, 100,00 en 3 cuotas → 33,33 / 33,33 / 33,34")
    void sinInteres_ajusteCentavos() {
        CondicionesCredito c = new CondicionesCredito(
                ModalidadCalculo.SIN_INTERES,
                new BigDecimal("100.00"), 3, null, null);

        ResultadoCalculo r = service.calcular(c);

        assertEquals(3, r.tabla().size());
        assertEquals(new BigDecimal("33.33"), r.tabla().get(0).cuota());
        assertEquals(new BigDecimal("33.33"), r.tabla().get(1).cuota());
        // Última cuota ajustada para cerrar saldo en 0
        assertEquals(new BigDecimal("33.34"), r.tabla().get(2).cuota());
        assertEquals(0, r.tabla().get(2).saldo().compareTo(BigDecimal.ZERO));
    }

    // ──────────────────────── Test 3: TASA_CONOCIDA, 1 cuota ────────────────────────

    @Test
    @DisplayName("T3: TASA_CONOCIDA, 100.000 al 2% mensual en 1 cuota")
    void tasaConocida_unaCuota() {
        CondicionesCredito c = new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 1, new BigDecimal("0.02"), null);

        ResultadoCalculo r = service.calcular(c);

        assertEquals(new BigDecimal("102000.00"), r.valorCuota());
        assertEquals(new BigDecimal("2000.00"), r.tabla().getFirst().interes());

        // CAE ≈ 26.82% → 0.2682 como fracción (tolerancia 0.01 pp = 0.0001)
        BigDecimal caeEsperado = new BigDecimal("0.26824179");
        assertTrue(
                r.cargaAnualEquivalente().subtract(caeEsperado).abs()
                        .compareTo(new BigDecimal("0.0001")) < 0,
                "CAE esperado ≈ 26.82%, obtenido: " + r.cargaAnualEquivalente());
    }

    // ──────────────────────── Test 4: TASA_CONOCIDA, 12 cuotas ────────────────────────

    @Test
    @DisplayName("T4: TASA_CONOCIDA, 100.000 al 1% mensual en 12 cuotas")
    void tasaConocida_doceCuotas() {
        CondicionesCredito c = new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.01"), null);

        ResultadoCalculo r = service.calcular(c);

        // Cuota ≈ 8884.88 (tolerancia 0.01)
        assertTrue(
                r.valorCuota().subtract(new BigDecimal("8884.88")).abs()
                        .compareTo(new BigDecimal("0.01")) <= 0,
                "Cuota esperada ≈ 8884.88, obtenida: " + r.valorCuota());

        // Σ capital = 100.000
        BigDecimal sumaCapital = r.tabla().stream()
                .map(CuotaAmortizacion::capital)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sumaCapital.compareTo(new BigDecimal("100000.00")),
                "Σ capital debe ser 100.000, obtenida: " + sumaCapital);

        // Saldo final = 0
        assertEquals(0, r.tabla().getLast().saldo().compareTo(BigDecimal.ZERO),
                "Saldo final debe ser 0");

        // Monto total ≈ 106.618,56 (tolerancia 0.10)
        assertTrue(
                r.montoTotal().subtract(new BigDecimal("106618.56")).abs()
                        .compareTo(new BigDecimal("0.10")) <= 0,
                "Monto total esperado ≈ 106618.56, obtenido: " + r.montoTotal());
    }

    // ──────────────────────── Test 5: Conversión de tasas ────────────────────────

    @Test
    @DisplayName("T5: Conversión tasa anual 12.682503% ⇔ mensual 1%")
    void conversionTasas() {
        BigDecimal tasaMensual = new BigDecimal("0.01");
        BigDecimal tasaAnualEsperada = new BigDecimal("0.12682503");

        // Mensual → Anual
        BigDecimal tasaAnual = service.tasaMensualAAnual(tasaMensual);
        assertTrue(
                tasaAnual.subtract(tasaAnualEsperada).abs()
                        .compareTo(new BigDecimal("0.000001")) < 0,
                "Anual esperada ≈ 0.12682503, obtenida: " + tasaAnual);

        // Anual → Mensual
        BigDecimal tasaMensualCalc = service.tasaAnualAMensual(tasaAnualEsperada);
        assertTrue(
                tasaMensualCalc.subtract(tasaMensual).abs()
                        .compareTo(new BigDecimal("0.000001")) < 0,
                "Mensual esperada ≈ 0.01, obtenida: " + tasaMensualCalc);
    }

    // ──────────────────────── Test 6: Con costos ────────────────────────

    @Test
    @DisplayName("T6: TASA_CONOCIDA con costoAdicionalPorCuota y gastosIniciales")
    void conCostos() {
        // Caso base sin costos
        CondicionesCredito cBase = new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.01"), null);
        ResultadoCalculo rBase = service.calcular(cBase);
        BigDecimal caeSinCostos = rBase.cargaAnualEquivalente();

        // Caso con costoAdicionalPorCuota = 500
        CondicionesCredito cConCosto = new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.01"), null,
                BigDecimal.ZERO, new BigDecimal("500"));
        ResultadoCalculo rConCosto = service.calcular(cConCosto);

        // CAE con costos > CAE sin costos
        assertTrue(rConCosto.cargaAnualEquivalente().compareTo(caeSinCostos) > 0,
                "CAE con costos debe ser mayor que sin costos");

        // Caso con costoAdicionalPorCuota = 500 + gastosIniciales = 2000
        CondicionesCredito cConTodo = new CondicionesCredito(
                ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.01"), null,
                new BigDecimal("2000"), new BigDecimal("500"));
        ResultadoCalculo rConTodo = service.calcular(cConTodo);

        // CAE sube más con gastos iniciales
        assertTrue(rConTodo.cargaAnualEquivalente().compareTo(rConCosto.cargaAnualEquivalente()) > 0,
                "CAE con gastos iniciales debe ser aún mayor");

        // costoTotalCredito = montoTotal + gastosIniciales
        assertEquals(0,
                rConTodo.costoTotalCredito().compareTo(
                        rConTodo.montoTotal().add(new BigDecimal("2000"))),
                "costoTotalCredito = montoTotal + gastosIniciales");
    }

    // ──────────────────────── Test 7: CUOTA_CONOCIDA ────────────────────────

    @Nested
    @DisplayName("T7: CUOTA_CONOCIDA")
    class CuotaConocidaTests {

        @Test
        @DisplayName("100.000 en 12 cuotas de 8.884,88 → tasa ≈ 1%")
        void tasaImplicita() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("100000"), 12, null, new BigDecimal("8884.88"));

            ResultadoCalculo r = service.calcular(c);

            // Tasa ≈ 1% = 0.01 (tolerancia 0.001% = 0.00001)
            assertTrue(
                    r.tasaMensual().subtract(new BigDecimal("0.01")).abs()
                            .compareTo(new BigDecimal("0.00001")) < 0,
                    "Tasa esperada ≈ 0.01, obtenida: " + r.tasaMensual());
        }

        @Test
        @DisplayName("120.000 en 12 cuotas de 10.000 → tasa 0")
        void tasaCero() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("120000"), 12, null, new BigDecimal("10000"));

            ResultadoCalculo r = service.calcular(c);

            assertEquals(0, r.tasaMensual().compareTo(BigDecimal.ZERO),
                    "Tasa debe ser 0 cuando cuotas cubren exactamente el capital");
        }

        @Test
        @DisplayName("100.000 con 12 × 8.000 → error 'los pagos no cubren el capital'")
        void pagosInsuficientes() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("100000"), 12, null, new BigDecimal("8000"));

            CalculoFinancieroException ex = assertThrows(CalculoFinancieroException.class,
                    () -> service.calcular(c));
            assertTrue(ex.getMessage().toLowerCase().contains("no cubren el capital"),
                    "Mensaje debe indicar que los pagos no cubren el capital");
        }
    }

    // ──────────────────────── Test 8: Validaciones ────────────────────────

    @Nested
    @DisplayName("T8: Validaciones por modalidad y rangos")
    class ValidacionesTests {

        // --- SIN_INTERES: campos prohibidos ---
        @Test
        @DisplayName("SIN_INTERES con tasaMensual → error")
        void sinInteres_prohibeTasa() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.SIN_INTERES,
                    new BigDecimal("100000"), 12, new BigDecimal("0.01"), null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("SIN_INTERES con valorCuotaPublicada → error")
        void sinInteres_prohibeCuotaPublicada() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.SIN_INTERES,
                    new BigDecimal("100000"), 12, null, new BigDecimal("9000"));
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        // --- TASA_CONOCIDA: campo obligatorio faltante y prohibido ---
        @Test
        @DisplayName("TASA_CONOCIDA sin tasaMensual → error")
        void tasaConocida_requiereTasa() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, null, null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("TASA_CONOCIDA con valorCuotaPublicada → error")
        void tasaConocida_prohibeCuotaPublicada() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("0.01"), new BigDecimal("9000"));
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        // --- CUOTA_CONOCIDA: campo obligatorio faltante y prohibidos ---
        @Test
        @DisplayName("CUOTA_CONOCIDA sin valorCuotaPublicada → error")
        void cuotaConocida_requiereCuota() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("100000"), 12, null, null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("CUOTA_CONOCIDA con tasaMensual → error")
        void cuotaConocida_prohibeTasa() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("0.01"), new BigDecimal("9000"));
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("CUOTA_CONOCIDA con gastosIniciales → error")
        void cuotaConocida_prohibeGastos() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("100000"), 12, null, new BigDecimal("9000"),
                    new BigDecimal("1000"), BigDecimal.ZERO);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("CUOTA_CONOCIDA con costoAdicionalPorCuota → error")
        void cuotaConocida_prohibeCostoAdicional() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("100000"), 12, null, new BigDecimal("9000"),
                    BigDecimal.ZERO, new BigDecimal("500"));
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        // --- Rangos ---
        @Test
        @DisplayName("cantidadCuotas < 1 → error")
        void cuotasMenorQueUno() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.SIN_INTERES,
                    new BigDecimal("100000"), 0, null, null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("cantidadCuotas > 360 → error")
        void cuotasMayorQueTrescientosSesenta() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.SIN_INTERES,
                    new BigDecimal("100000"), 361, null, null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("montoCapital ≤ 0 → error")
        void capitalCero() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.SIN_INTERES,
                    BigDecimal.ZERO, 12, null, null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("montoCapital negativo → error")
        void capitalNegativo() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.SIN_INTERES,
                    new BigDecimal("-1000"), 12, null, null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("gastosIniciales negativos → error")
        void gastosNegativos() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("0.01"), null,
                    new BigDecimal("-100"), BigDecimal.ZERO);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("gastosIniciales ≥ capital → error")
        void gastosMayoresQueCapital() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("0.01"), null,
                    new BigDecimal("100000"), BigDecimal.ZERO);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("costoAdicionalPorCuota negativo → error")
        void costoAdicionalNegativo() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("0.01"), null,
                    BigDecimal.ZERO, new BigDecimal("-100"));
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("tasaMensual fuera de rango [0, 1] → error")
        void tasaFueraDeRango() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("1.5"), null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }

        @Test
        @DisplayName("tasaMensual negativa → error")
        void tasaNegativa() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("100000"), 12, new BigDecimal("-0.01"), null);
            assertThrows(CalculoFinancieroException.class, () -> service.calcular(c));
        }
    }

    // ──────────────────────── Test 10: cifras de simulador oficial ────────────────────────

    @Nested
    @DisplayName("T10: Cifras de simulador oficial")
    class SimuladorOficialTests {

        @Test
        @DisplayName("Caso 1: TASA_CONOCIDA sin costos — 2M, 24 cuotas, 3,49% mensual")
        void caso1_sinCostos() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("2000000"), 24, new BigDecimal("0.0349"), null);

            ResultadoCalculo r = service.calcular(c);

            // valorCuota = 124.414,89
            assertEquals(new BigDecimal("124414.89"), r.valorCuota(),
                    "valorCuota esperado 124.414,89");

            // Última cuota ajustada = 124.414,92
            assertEquals(new BigDecimal("124414.92"), r.tabla().getLast().cuota(),
                    "última cuota ajustada esperada 124.414,92");

            // 1.ª cuota: interés = 69.800,00
            assertEquals(new BigDecimal("69800.00"), r.tabla().getFirst().interes(),
                    "interés 1.ª cuota esperado 69.800,00");

            // 1.ª cuota: capital = 54.614,89
            assertEquals(new BigDecimal("54614.89"), r.tabla().getFirst().capital(),
                    "capital 1.ª cuota esperado 54.614,89");

            // Σ capital = 2.000.000
            BigDecimal sumaCapital = r.tabla().stream()
                    .map(CuotaAmortizacion::capital)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, sumaCapital.compareTo(new BigDecimal("2000000.00")),
                    "Σ capital debe ser 2.000.000");

            // Saldo final = 0
            assertEquals(0, r.tabla().getLast().saldo().compareTo(BigDecimal.ZERO));

            // montoTotal = costoTotalCredito = 2.985.957,39
            assertEquals(new BigDecimal("2985957.39"), r.montoTotal(),
                    "montoTotal esperado 2.985.957,39");
            assertEquals(r.montoTotal(), r.costoTotalCredito(),
                    "sin costos: montoTotal == costoTotalCredito");

            // interesesYCostos = 985.957,39
            assertEquals(new BigDecimal("985957.39"), r.interesesYCostos(),
                    "interesesYCostos esperado 985.957,39");

            // Tasa anual efectiva ≈ 50,9318%  (tolerancia 0,01 pp = 0,0001)
            assertTrue(
                    r.tasaAnualEfectiva().subtract(new BigDecimal("0.509318")).abs()
                            .compareTo(new BigDecimal("0.0001")) < 0,
                    "TAE esperada ≈ 50,93%, obtenida: " + r.tasaAnualEfectiva());

            // CAE = TAE (sin costos)  ≈ 50,93%
            assertTrue(
                    r.cargaAnualEquivalente().subtract(new BigDecimal("0.5093")).abs()
                            .compareTo(new BigDecimal("0.001")) < 0,
                    "CAE esperado ≈ 50,93%, obtenido: " + r.cargaAnualEquivalente());
        }

        @Test
        @DisplayName("Caso 2: TASA_CONOCIDA + seguro mensual — 3M, 36 cuotas, 1,8%, costo 3.500")
        void caso2_conSeguroMensual() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("3000000"), 36, new BigDecimal("0.018"), null,
                    BigDecimal.ZERO, new BigDecimal("3500"));

            ResultadoCalculo r = service.calcular(c);

            // valorCuota (sin costo adicional) = 113.951,50
            assertEquals(new BigDecimal("113951.50"), r.valorCuota(),
                    "valorCuota esperado 113.951,50");

            // Última cuota ajustada = 113.951,33
            assertEquals(new BigDecimal("113951.33"), r.tabla().getLast().cuota(),
                    "última cuota ajustada esperada 113.951,33");

            // 1.ª cuota: interés = 54.000,00 · capital = 59.951,50
            assertEquals(new BigDecimal("54000.00"), r.tabla().getFirst().interes());
            assertEquals(new BigDecimal("59951.50"), r.tabla().getFirst().capital());

            // Σ capital = 3.000.000 · saldo final = 0
            BigDecimal sumaCapital = r.tabla().stream()
                    .map(CuotaAmortizacion::capital)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, sumaCapital.compareTo(new BigDecimal("3000000.00")));
            assertEquals(0, r.tabla().getLast().saldo().compareTo(BigDecimal.ZERO));

            // montoTotal = 4.228.253,83
            assertTrue(
                    r.montoTotal().subtract(new BigDecimal("4228253.83")).abs()
                            .compareTo(new BigDecimal("0.10")) <= 0,
                    "montoTotal esperado ≈ 4.228.253,83, obtenido: " + r.montoTotal());

            // interesesYCostos = 1.228.253,83
            assertTrue(
                    r.interesesYCostos().subtract(new BigDecimal("1228253.83")).abs()
                            .compareTo(new BigDecimal("0.10")) <= 0,
                    "interesesYCostos esperado ≈ 1.228.253,83, obtenido: " + r.interesesYCostos());

            // TAE ≈ 23,8721%
            assertTrue(
                    r.tasaAnualEfectiva().subtract(new BigDecimal("0.238721")).abs()
                            .compareTo(new BigDecimal("0.0001")) < 0,
                    "TAE esperada ≈ 23,87%, obtenida: " + r.tasaAnualEfectiva());

            // CAE ≈ 26,6292%  (tolerancia 0,01 pp)
            assertTrue(
                    r.cargaAnualEquivalente().subtract(new BigDecimal("0.266292")).abs()
                            .compareTo(new BigDecimal("0.001")) < 0,
                    "CAE esperado ≈ 26,63%, obtenido: " + r.cargaAnualEquivalente());

            // CAE > TAE (porque hay costos)
            assertTrue(r.cargaAnualEquivalente().compareTo(r.tasaAnualEfectiva()) > 0,
                    "CAE debe ser > TAE cuando hay costos");
        }

        @Test
        @DisplayName("Caso 3: TASA_CONOCIDA + gastos iniciales — 5M, 48 cuotas, 1,5%, gastos 120.000")
        void caso3_conGastosIniciales() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.TASA_CONOCIDA,
                    new BigDecimal("5000000"), 48, new BigDecimal("0.015"), null,
                    new BigDecimal("120000"), BigDecimal.ZERO);

            ResultadoCalculo r = service.calcular(c);

            // valorCuota = 146.875,00
            assertEquals(new BigDecimal("146875.00"), r.valorCuota(),
                    "valorCuota esperado 146.875,00");

            // Última cuota ajustada = 146.874,89
            assertEquals(new BigDecimal("146874.89"), r.tabla().getLast().cuota(),
                    "última cuota ajustada esperada 146.874,89");

            // 1.ª cuota: interés = 75.000,00 · capital = 71.875,00
            assertEquals(new BigDecimal("75000.00"), r.tabla().getFirst().interes());
            assertEquals(new BigDecimal("71875.00"), r.tabla().getFirst().capital());

            // Σ capital = 5.000.000 · saldo final = 0
            BigDecimal sumaCapital = r.tabla().stream()
                    .map(CuotaAmortizacion::capital)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, sumaCapital.compareTo(new BigDecimal("5000000.00")));
            assertEquals(0, r.tabla().getLast().saldo().compareTo(BigDecimal.ZERO));

            // montoTotal = 7.049.999,89
            assertTrue(
                    r.montoTotal().subtract(new BigDecimal("7049999.89")).abs()
                            .compareTo(new BigDecimal("0.10")) <= 0,
                    "montoTotal esperado ≈ 7.049.999,89, obtenido: " + r.montoTotal());

            // costoTotalCredito = montoTotal + gastos = 7.169.999,89
            assertTrue(
                    r.costoTotalCredito().subtract(new BigDecimal("7169999.89")).abs()
                            .compareTo(new BigDecimal("0.10")) <= 0,
                    "costoTotalCredito esperado ≈ 7.169.999,89, obtenido: " + r.costoTotalCredito());

            // interesesYCostos = 2.169.999,89
            assertTrue(
                    r.interesesYCostos().subtract(new BigDecimal("2169999.89")).abs()
                            .compareTo(new BigDecimal("0.10")) <= 0,
                    "interesesYCostos esperado ≈ 2.169.999,89, obtenido: " + r.interesesYCostos());

            // TAE ≈ 19,5618%
            assertTrue(
                    r.tasaAnualEfectiva().subtract(new BigDecimal("0.195618")).abs()
                            .compareTo(new BigDecimal("0.0001")) < 0,
                    "TAE esperada ≈ 19,56%, obtenida: " + r.tasaAnualEfectiva());

            // CAE ≈ 21,1893%  (tolerancia 0,01 pp)
            assertTrue(
                    r.cargaAnualEquivalente().subtract(new BigDecimal("0.211893")).abs()
                            .compareTo(new BigDecimal("0.001")) < 0,
                    "CAE esperado ≈ 21,19%, obtenido: " + r.cargaAnualEquivalente());

            // CAE > TAE (porque hay gastos iniciales)
            assertTrue(r.cargaAnualEquivalente().compareTo(r.tasaAnualEfectiva()) > 0,
                    "CAE debe ser > TAE cuando hay gastos iniciales");
        }

        @Test
        @DisplayName("Caso 4: CUOTA_CONOCIDA — compra en tienda, 399.990 en 12 cuotas de 38.000")
        void caso4_cuotaConocida_tienda() {
            CondicionesCredito c = new CondicionesCredito(
                    ModalidadCalculo.CUOTA_CONOCIDA,
                    new BigDecimal("399990"), 12, null, new BigDecimal("38000"));

            ResultadoCalculo r = service.calcular(c);

            // Total pagado = 456.000
            assertTrue(
                    r.montoTotal().subtract(new BigDecimal("456000")).abs()
                            .compareTo(new BigDecimal("0.05")) <= 0,
                    "montoTotal esperado ≈ 456.000, obtenido: " + r.montoTotal());

            // interesesYCostos = 56.010
            assertTrue(
                    r.interesesYCostos().subtract(new BigDecimal("56010")).abs()
                            .compareTo(new BigDecimal("0.05")) <= 0,
                    "interesesYCostos esperado ≈ 56.010, obtenido: " + r.interesesYCostos());

            // Tasa mensual implícita ≈ 2,076150%  (tolerancia 0,001% = 0,00001)
            assertTrue(
                    r.tasaMensual().subtract(new BigDecimal("0.02076150")).abs()
                            .compareTo(new BigDecimal("0.00001")) < 0,
                    "tasa mensual esperada ≈ 2,0762%, obtenida: " + r.tasaMensual());

            // CAE ≈ 27,9650%  (tolerancia 0,01 pp)
            assertTrue(
                    r.cargaAnualEquivalente().subtract(new BigDecimal("0.279650")).abs()
                            .compareTo(new BigDecimal("0.001")) < 0,
                    "CAE esperado ≈ 27,97%, obtenido: " + r.cargaAnualEquivalente());
        }
    }
}
