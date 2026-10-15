package com.example.saveup.service.finanzas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImputacionPagosTest {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CalculoFinancieroService motor = new CalculoFinancieroService();

    private List<CuotaAmortizacion> tablaSinInteres() {
        return motor.calcular(new CondicionesCredito(
                com.example.saveup.model.enums.ModalidadCalculo.SIN_INTERES,
                new BigDecimal("100000"), 10, null, null, CERO, CERO)).tabla();
    }

    private List<CuotaAmortizacion> tablaConCostoAdicional() {
        return motor.calcular(new CondicionesCredito(
                com.example.saveup.model.enums.ModalidadCalculo.SIN_INTERES,
                new BigDecimal("100000"), 10, null, null, CERO, new BigDecimal("500"))).tabla();
    }

    private List<CuotaAmortizacion> tablaConTasa() {
        return motor.calcular(new CondicionesCredito(
                com.example.saveup.model.enums.ModalidadCalculo.TASA_CONOCIDA,
                new BigDecimal("100000"), 12, new BigDecimal("0.015"), null, CERO, CERO)).tabla();
    }

    private ImputacionPagos.ImputacionCuota cuota(List<ImputacionPagos.ImputacionCuota> imputacion, int numero) {
        return imputacion.stream().filter(c -> c.numero() == numero).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Un pago de 2,5 cuotas cubre 2 y deja la 3.ª en PARCIAL")
    void pagoDe25Cuotas_cubreDosYDejaLaTerceraParcial() {
        List<ImputacionPagos.ImputacionCuota> imputacion =
                ImputacionPagos.imputar(tablaSinInteres(), 0, new BigDecimal("25000"));

        assertEquals(ImputacionPagos.EstadoCuota.PAGADA, cuota(imputacion, 1).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PAGADA, cuota(imputacion, 2).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PARCIAL, cuota(imputacion, 3).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PENDIENTE, cuota(imputacion, 4).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PENDIENTE, cuota(imputacion, 10).estado());

        assertEquals(0, new BigDecimal("5000.00").compareTo(cuota(imputacion, 3).pagado()));
        assertEquals(2, ImputacionPagos.contarPagadas(imputacion));
        assertEquals(0, new BigDecimal("25000.00").compareTo(ImputacionPagos.capitalImputado(imputacion)));
    }

    @Test
    @DisplayName("Con cuotas previas, los pagos reales se imputan desde la cuota siguiente")
    void conPrevias_losPagosRealesEmpiezanEnLaSiguiente() {
        List<ImputacionPagos.ImputacionCuota> imputacion =
                ImputacionPagos.imputar(tablaSinInteres(), 2, new BigDecimal("10000"));

        assertTrue(cuota(imputacion, 1).previa());
        assertTrue(cuota(imputacion, 2).previa());
        assertFalse(cuota(imputacion, 3).previa());

        assertEquals(ImputacionPagos.EstadoCuota.PAGADA, cuota(imputacion, 1).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PAGADA, cuota(imputacion, 2).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PAGADA, cuota(imputacion, 3).estado());
        assertEquals(ImputacionPagos.EstadoCuota.PENDIENTE, cuota(imputacion, 4).estado());

        assertEquals(3, ImputacionPagos.contarPagadas(imputacion));
    }

    @Test
    @DisplayName("Deuda sin interés: el interés imputado es 0")
    void deudaSinInteres_interesCero() {
        List<ImputacionPagos.ImputacionCuota> imputacion =
                ImputacionPagos.imputar(tablaSinInteres(), 1, new BigDecimal("25000"));

        BigDecimal intereses = CERO;
        for (ImputacionPagos.ImputacionCuota fila : imputacion) {
            intereses = intereses.add(fila.interes());
        }
        assertEquals(0, CERO.compareTo(intereses));
    }

    @Test
    @DisplayName("Deuda con tasa: al pagar todo, la suma del capital imputado es el capital")
    void deudaConTasa_sumaCapitalIgualCapitalAlPagarTodo() {
        List<CuotaAmortizacion> tabla = tablaConTasa();
        BigDecimal montoTotal = motor.calcular(new CondicionesCredito(
                        com.example.saveup.model.enums.ModalidadCalculo.TASA_CONOCIDA,
                        new BigDecimal("100000"), 12, new BigDecimal("0.015"), null, CERO, CERO))
                .montoTotal();

        List<ImputacionPagos.ImputacionCuota> imputacion = ImputacionPagos.imputar(tabla, 0, montoTotal);

        assertEquals(0, new BigDecimal("100000.00").compareTo(ImputacionPagos.capitalImputado(imputacion)),
                "Σ capital imputado debe cerrar en el capital de la deuda");
        assertEquals(12, ImputacionPagos.contarPagadas(imputacion));
        assertTrue(imputacion.stream().anyMatch(f -> f.interes().signum() > 0),
                "Con tasa conocida debe haber interés imputado");
    }

    @Test
    @DisplayName("CUOTA_CALCULADA: cobra cuota + costo adicional de la próxima cuota impaga")
    void montoProximaCuotaImpaga_incluyeCostoAdicional() {
        assertEquals(0, new BigDecimal("10500.00").compareTo(
                ImputacionPagos.montoProximaCuotaImpaga(tablaConCostoAdicional(), 0, CERO)));

        assertEquals(0, new BigDecimal("10500.00").compareTo(
                ImputacionPagos.montoProximaCuotaImpaga(tablaConCostoAdicional(), 2, CERO)));

        // Cuota parcial: se cobra solo lo que falta
        assertEquals(0, new BigDecimal("5500.00").compareTo(
                ImputacionPagos.montoProximaCuotaImpaga(tablaConCostoAdicional(), 0, new BigDecimal("15500"))));
    }

    @Test
    @DisplayName("CUOTA_CALCULADA: monto completo sin costo adicional y resto cuando no queda nada")
    void montoProximaCuotaImpaga_sinCostoAdicional() {
        assertEquals(0, new BigDecimal("10000.00").compareTo(
                ImputacionPagos.montoProximaCuotaImpaga(tablaSinInteres(), 0, CERO)));

        assertEquals(0, new BigDecimal("10000.00").compareTo(
                ImputacionPagos.montoProximaCuotaImpaga(tablaSinInteres(), 9, CERO)));

        assertEquals(0, CERO.compareTo(
                ImputacionPagos.montoProximaCuotaImpaga(tablaSinInteres(), 0, new BigDecimal("100000"))));
    }

    @Test
    @DisplayName("Entradas inválidas se rechazan antes de imputar")
    void entradasInvalidas_seRechazan() {
        assertThrows(IllegalArgumentException.class,
                () -> ImputacionPagos.imputar(null, 0, CERO));
        assertThrows(IllegalArgumentException.class,
                () -> ImputacionPagos.imputar(tablaSinInteres(), 0, new BigDecimal("-1000")));
        assertThrows(IllegalArgumentException.class,
                () -> ImputacionPagos.imputar(tablaSinInteres(), 11, CERO));

        // Pagos reales nulos se toman como 0: la deuda queda pendiente
        List<ImputacionPagos.ImputacionCuota> imputacion = ImputacionPagos.imputar(tablaSinInteres(), 0, null);
        assertEquals(ImputacionPagos.EstadoCuota.PENDIENTE, cuota(imputacion, 1).estado());
        assertEquals(0, ImputacionPagos.contarPagadas(imputacion));
    }
}
