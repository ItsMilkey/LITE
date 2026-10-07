package com.example.saveup.service;

import com.example.saveup.dto.SimulacionCreditoRequestDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.service.finanzas.CalculoFinancieroException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para SimulacionService (JUnit 5, sin Spring, sin BD).
 */
class SimulacionServiceTest {

    private SimulacionService service;

    @BeforeEach
    void setUp() {
        service = new SimulacionService();
    }

    // ──────────────────────── 1. Modalidades de T1a ────────────────────────

    @Test
    @DisplayName("Modalidad SIN_INTERES: calcula cuota exacta, tasa 0, costoPorCada100 = 100")
    void sinInteres_calculaCorrectamente() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(4)
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(new BigDecimal("25000.00"), resp.getValorCuota());
        assertEquals(new BigDecimal("100000.00"), resp.getMontoTotal());
        assertEquals(new BigDecimal("100000.00"), resp.getCostoTotalCredito());
        assertEquals(new BigDecimal("0.00"), resp.getInteresesYCostos());
        assertEquals(new BigDecimal("0.00"), resp.getCargaAnualEquivalente());
        assertEquals(new BigDecimal("100.00"), resp.getResumenEducativo().getCostoPorCada100());
        assertEquals(new BigDecimal("0.00"), resp.getResumenEducativo().getPorcentajeSobreCapital());
        assertEquals(new BigDecimal("0.00"), resp.getResumenEducativo().getPorcentajeInteresesYCostos());
        assertEquals(4, resp.getTablaAmortizacion().size());
        assertNull(resp.getCondiciones().getFechaPrimeraCuota());
        assertNull(resp.getCondiciones().getFechaUltimaCuota());
        assertTrue(resp.getAdvertencias().contains("El CAE mostrado es referencial; el oficial lo informa la institución financiera."));
    }

    @Test
    @DisplayName("Modalidad TASA_CONOCIDA con tasaMensual: 100.000 al 2% en 1 cuota")
    void tasaConocida_tasaMensual() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(1)
                .tasaMensual(new BigDecimal("2.0")) // 2%
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(new BigDecimal("102000.00"), resp.getValorCuota());
        assertEquals(new BigDecimal("102000.00"), resp.getCostoTotalCredito());
        assertEquals(new BigDecimal("2000.00"), resp.getInteresesYCostos());
        assertEquals(new BigDecimal("2.00"), resp.getCondiciones().getTasaMensual());
        assertEquals(new BigDecimal("26.82"), resp.getCargaAnualEquivalente());
        assertEquals(new BigDecimal("102.00"), resp.getResumenEducativo().getCostoPorCada100());
        assertEquals(new BigDecimal("2.00"), resp.getResumenEducativo().getPorcentajeSobreCapital());
        assertEquals(new BigDecimal("1.96"), resp.getResumenEducativo().getPorcentajeInteresesYCostos());
    }

    @Test
    @DisplayName("Modalidad TASA_CONOCIDA con tasaAnualEfectiva: convierte a mensual y calcula")
    void tasaConocida_tasaAnualEfectiva() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaAnualEfectiva(new BigDecimal("12.682503")) // ≈ 1% mensual
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        // Cuota ≈ 8884.88
        assertTrue(resp.getValorCuota().subtract(new BigDecimal("8884.88")).abs()
                .compareTo(new BigDecimal("0.01")) <= 0);
        assertEquals(new BigDecimal("1.00"), resp.getCondiciones().getTasaMensual());
    }

    @Test
    @DisplayName("Modalidad CUOTA_CONOCIDA: calcula tasa implícita y CAE")
    void cuotaConocida_calculaTasaImplicita() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.CUOTA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .valorCuota(new BigDecimal("8884.88"))
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(new BigDecimal("8884.88"), resp.getValorCuota());
        assertEquals(new BigDecimal("1.00"), resp.getCondiciones().getTasaMensual());
        assertNotNull(resp.getCargaAnualEquivalente());
        assertTrue(resp.getCargaAnualEquivalente().compareTo(BigDecimal.ZERO) > 0);
    }

    // ──────────────────────── 2. Plazo por fechas ────────────────────────

    @Test
    @DisplayName("Plazo por fechas: 7-oct-2026 a 8-nov-2027 → cantidadCuotas 14, fechaUltimaCuota 7-nov-2027")
    void plazoPorFechas_catorceCuotas() {
        LocalDate primera = LocalDate.of(2026, 10, 7);
        LocalDate ultima = LocalDate.of(2027, 11, 8);

        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("140000"))
                .fechaPrimeraCuota(primera)
                .fechaUltimaCuota(ultima)
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(14, resp.getCondiciones().getCantidadCuotas());
        assertEquals(primera, resp.getCondiciones().getFechaPrimeraCuota());
        assertEquals(LocalDate.of(2027, 11, 7), resp.getCondiciones().getFechaUltimaCuota());
        assertEquals(14, resp.getTablaAmortizacion().size());
        assertEquals(primera, resp.getTablaAmortizacion().getFirst().getFechaVencimiento());
        assertEquals(LocalDate.of(2027, 11, 7), resp.getTablaAmortizacion().getLast().getFechaVencimiento());
    }

    @Test
    @DisplayName("Plazo por cantidadCuotas con fechaPrimeraCuota opcional asigna vencimientos")
    void cantidadCuotasConFechaPrimeraCuota() {
        LocalDate primera = LocalDate.of(2026, 10, 7);

        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("30000"))
                .cantidadCuotas(3)
                .fechaPrimeraCuota(primera)
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(3, resp.getCondiciones().getCantidadCuotas());
        assertEquals(primera, resp.getCondiciones().getFechaPrimeraCuota());
        assertEquals(LocalDate.of(2026, 12, 7), resp.getCondiciones().getFechaUltimaCuota());
        assertEquals(3, resp.getTablaAmortizacion().size());
        assertEquals(LocalDate.of(2026, 12, 7), resp.getTablaAmortizacion().getLast().getFechaVencimiento());
    }

    // ──────────────────────── 3. Validaciones cruzadas ────────────────────────

    @Test
    @DisplayName("Error 400 si se envía cantidadCuotas junto con fechaUltimaCuota")
    void error_cantidadCuotasJuntoConFechaUltimaCuota() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .fechaUltimaCuota(LocalDate.of(2027, 10, 7))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(request));
    }

    @Test
    @DisplayName("Error 400 si no se envía ni cantidadCuotas ni fechas completas")
    void error_sinPlazoValido() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(request));
    }

    @Test
    @DisplayName("Error 400 si se envían tasaMensual y tasaAnualEfectiva juntas")
    void error_tasaMensualYTasaAnualJuntas() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.5"))
                .tasaAnualEfectiva(new BigDecimal("19.56"))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(request));
    }

    @Test
    @DisplayName("Error 400 si no se envía ninguna tasa en TASA_CONOCIDA")
    void error_tasaConocidaSinTasas() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(request));
    }

    @Test
    @DisplayName("Error 400 si se envía tasa en SIN_INTERES")
    void error_sinInteresConTasa() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.5"))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(request));
    }

    @Test
    @DisplayName("Error 400 si se envía valorCuota en SIN_INTERES o TASA_CONOCIDA")
    void error_valorCuotaEnModalidadIncorrecta() {
        SimulacionCreditoRequestDTO requestSinInteres = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .valorCuota(new BigDecimal("8000"))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(requestSinInteres));

        SimulacionCreditoRequestDTO requestTasaConocida = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.0"))
                .valorCuota(new BigDecimal("8000"))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(requestTasaConocida));
    }

    @Test
    @DisplayName("Error 400 si CUOTA_CONOCIDA tiene gastos o costos adicionales o no tiene valorCuota")
    void error_cuotaConocidaValidacionesCruzadas() {
        // Sin valor cuota
        SimulacionCreditoRequestDTO reqSinCuota = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.CUOTA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .build();
        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(reqSinCuota));

        // Con gastos
        SimulacionCreditoRequestDTO reqConGastos = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.CUOTA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .valorCuota(new BigDecimal("10000"))
                .gastosIniciales(new BigDecimal("5000"))
                .build();
        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(reqConGastos));

        // Con costo adicional
        SimulacionCreditoRequestDTO reqConCosto = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.CUOTA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .valorCuota(new BigDecimal("10000"))
                .costoAdicionalPorCuota(new BigDecimal("500"))
                .build();
        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(reqConCosto));

        // Con alternativas
        SimulacionCreditoRequestDTO reqConAlt = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.CUOTA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .valorCuota(new BigDecimal("10000"))
                .cantidadCuotasAlternativas(List.of(6, 24))
                .build();
        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(reqConAlt));
    }

    @Test
    @DisplayName("Error 400 si cantidadCuotasAlternativas supera 5 elementos")
    void error_cuotasAlternativasExcedeMaximo() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.0"))
                .cantidadCuotasAlternativas(List.of(6, 12, 18, 24, 36, 48))
                .build();

        assertThrows(CalculoFinancieroException.class, () -> service.simularCredito(request));
    }

    // ──────────────────────── 4. ComparacionPlazos ────────────────────────

    @Test
    @DisplayName("comparacionPlazos: a más cuotas, menor valorCuota y mayor costoTotalCredito (con tasa > 0)")
    void comparacionPlazos_comportamientoCorrecto() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.5"))
                .cantidadCuotasAlternativas(List.of(6, 12, 24))
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertNotNull(resp.getComparacionPlazos());
        assertEquals(3, resp.getComparacionPlazos().size());

        var comp6 = resp.getComparacionPlazos().get(0);
        var comp12 = resp.getComparacionPlazos().get(1);
        var comp24 = resp.getComparacionPlazos().get(2);

        // A más cuotas, menor valorCuota
        assertTrue(comp6.getValorCuota().compareTo(comp12.getValorCuota()) > 0);
        assertTrue(comp12.getValorCuota().compareTo(comp24.getValorCuota()) > 0);

        // A más cuotas, mayor costoTotalCredito
        assertTrue(comp6.getCostoTotalCredito().compareTo(comp12.getCostoTotalCredito()) < 0);
        assertTrue(comp12.getCostoTotalCredito().compareTo(comp24.getCostoTotalCredito()) < 0);
    }

    // ──────────────────────── 5. Resumen educativo y advertencias ────────────────────────

    @Test
    @DisplayName("Advertencia de endeudamiento agregada si la cuota supera el 30% del ingreso mensual")
    void advertencia_ingresoComprometidoSuperaTreinta() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("120000"))
                .cantidadCuotas(12) // Cuota: 10.000
                .ingresoMensual(new BigDecimal("25000")) // 10.000 / 25.000 = 40%
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(new BigDecimal("40.00"), resp.getResumenEducativo().getPorcentajeIngresoComprometido());
        assertEquals(2, resp.getAdvertencias().size());
        assertTrue(resp.getAdvertencias().get(1).contains("30%"));
    }

    @Test
    @DisplayName("Sin advertencia de sobreendeudamiento si la cuota no supera el 30% del ingreso mensual")
    void sinAdvertencia_ingresoComprometidoBajoTreinta() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("120000"))
                .cantidadCuotas(12) // Cuota: 10.000
                .ingresoMensual(new BigDecimal("100000")) // 10.000 / 100.000 = 10%
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertEquals(new BigDecimal("10.00"), resp.getResumenEducativo().getPorcentajeIngresoComprometido());
        assertEquals(1, resp.getAdvertencias().size());
    }

    @Test
    @DisplayName("incluirTabla = false no retorna la tabla de amortización")
    void incluirTabla_falso() {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(4)
                .incluirTabla(false)
                .build();

        SimulacionCreditoResponseDTO resp = service.simularCredito(request);

        assertNull(resp.getTablaAmortizacion());
        assertNotNull(resp.getCondiciones());
    }
}
