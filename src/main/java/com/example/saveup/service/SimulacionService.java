package com.example.saveup.service;

import com.example.saveup.dto.CondicionesCreditoDTO;
import com.example.saveup.dto.SimulacionCreditoRequestDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO.*;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.service.finanzas.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para simulación de créditos (sin persistencia en BD).
 * Orquesta CalculoFinancieroService y CalendarioCuotas.
 */
@Service
public class SimulacionService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int ESCALA_DINERO = 2;
    private static final int ESCALA_TASA = 4;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal UMBRAL_INGRESO_COMPROMETIDO = new BigDecimal("30");
    private static final String ADVERTENCIA_CAE = "El CAE mostrado es referencial; el oficial lo informa la institución financiera.";
    private static final String ADVERTENCIA_SOBRE_ENDEUDAMIENTO = "La cuota mensual compromete más del 30% de tus ingresos declarados (se recomienda un máximo de 30%).";

    private final CalculoFinancieroService calculoService;
    private final CalendarioCuotas calendarioCuotas;
    private final ResolutorCondiciones resolutorCondiciones;

    public SimulacionService() {
        this(new CalculoFinancieroService(), new CalendarioCuotas());
    }

    public SimulacionService(CalculoFinancieroService calculoService, CalendarioCuotas calendarioCuotas) {
        this(calculoService, calendarioCuotas, new ResolutorCondiciones(calculoService, calendarioCuotas));
    }

    public SimulacionService(CalculoFinancieroService calculoService, CalendarioCuotas calendarioCuotas, ResolutorCondiciones resolutorCondiciones) {
        this.calculoService = calculoService;
        this.calendarioCuotas = calendarioCuotas;
        this.resolutorCondiciones = resolutorCondiciones;
    }

    public SimulacionCreditoResponseDTO simularCredito(SimulacionCreditoRequestDTO request) {
        validarParametrosBasicos(request);

        ResolutorCondiciones.ResultadoResolucion resResolucion = resolutorCondiciones.resolver(toCondicionesDTO(request));

        int cantidadCuotas = resResolucion.cantidadCuotas();
        LocalDate fechaUltimaCuotaNormalizada = resResolucion.fechaUltimaCuota();
        List<LocalDate> fechasVencimiento = null;
        if (resResolucion.fechaPrimeraCuota() != null) {
            fechasVencimiento = calendarioCuotas.fechasVencimiento(resResolucion.fechaPrimeraCuota(), cantidadCuotas);
        }

        // 4. Validar cuotas alternativas
        validarCuotasAlternativas(request);

        // 5. Ejecutar cálculo con motor financiero
        CondicionesCredito condiciones = new CondicionesCredito(
                resResolucion.modalidad(),
                resResolucion.montoCapital().setScale(ESCALA_DINERO, REDONDEO),
                cantidadCuotas,
                resResolucion.tasaMensualFraccion(),
                resResolucion.valorCuotaInput(),
                resResolucion.gastosIniciales().setScale(ESCALA_DINERO, REDONDEO),
                resResolucion.costoAdicionalPorCuota().setScale(ESCALA_DINERO, REDONDEO)
        );

        ResultadoCalculo resultado = calculoService.calcular(condiciones);

        // 6. Construir condiciones normalizadas
        CondicionesSimulacionDTO condicionesNormalizadas = CondicionesSimulacionDTO.builder()
                .modalidad(request.getModalidad())
                .montoCapital(condiciones.montoCapital())
                .cantidadCuotas(cantidadCuotas)
                .fechaPrimeraCuota(request.getFechaPrimeraCuota())
                .fechaUltimaCuota(fechaUltimaCuotaNormalizada)
                .tasaMensual(toPorcentajeTasa(resultado.tasaMensual()))
                .tasaAnualEfectiva(toPorcentaje(resultado.tasaAnualEfectiva()))
                .build();

        // 7. Construir resumen educativo
        BigDecimal costoPorCada100 = resultado.costoTotalCredito()
                .divide(condiciones.montoCapital(), MC)
                .multiply(CIEN)
                .setScale(ESCALA_DINERO, REDONDEO);

        BigDecimal porcentajeSobreCapital = resultado.interesesYCostos()
                .divide(condiciones.montoCapital(), MC)
                .multiply(CIEN)
                .setScale(ESCALA_DINERO, REDONDEO);

        BigDecimal porcentajeInteresesYCostos = resultado.costoTotalCredito().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO)
                : resultado.interesesYCostos()
                .divide(resultado.costoTotalCredito(), MC)
                .multiply(CIEN)
                .setScale(ESCALA_DINERO, REDONDEO);

        BigDecimal porcentajeIngresoComprometido = null;
        if (request.getIngresoMensual() != null) {
            porcentajeIngresoComprometido = resultado.valorCuota()
                    .divide(request.getIngresoMensual(), MC)
                    .multiply(CIEN)
                    .setScale(ESCALA_DINERO, REDONDEO);
        }

        ResumenEducativoDTO resumenEducativo = ResumenEducativoDTO.builder()
                .costoPorCada100(costoPorCada100)
                .porcentajeSobreCapital(porcentajeSobreCapital)
                .porcentajeInteresesYCostos(porcentajeInteresesYCostos)
                .porcentajeIngresoComprometido(porcentajeIngresoComprometido)
                .build();

        // 8. Tabla de amortización (si requerida)
        List<CuotaSimulacionDTO> tablaAmortizacion = null;
        boolean incluirTabla = request.getIncluirTabla() == null || Boolean.TRUE.equals(request.getIncluirTabla());
        if (incluirTabla) {
            tablaAmortizacion = new ArrayList<>(resultado.tabla().size());
            for (int i = 0; i < resultado.tabla().size(); i++) {
                CuotaAmortizacion c = resultado.tabla().get(i);
                LocalDate fv = fechasVencimiento != null ? fechasVencimiento.get(i) : null;
                tablaAmortizacion.add(CuotaSimulacionDTO.builder()
                        .numero(c.numero())
                        .fechaVencimiento(fv)
                        .cuota(c.cuota())
                        .capital(c.capital())
                        .interes(c.interes())
                        .costoAdicional(c.costoAdicional())
                        .saldo(c.saldo())
                        .build());
            }
        }

        // 9. Comparación de plazos (si se solicitaron cuotas alternativas)
        List<ComparacionPlazoDTO> comparacionPlazos = null;
        if (request.getCantidadCuotasAlternativas() != null && !request.getCantidadCuotasAlternativas().isEmpty()) {
            comparacionPlazos = new ArrayList<>(request.getCantidadCuotasAlternativas().size());
            for (Integer altCuotas : request.getCantidadCuotasAlternativas()) {
                CondicionesCredito altCond = new CondicionesCredito(
                        request.getModalidad(),
                        condiciones.montoCapital(),
                        altCuotas,
                        resultado.tasaMensual(),
                        null,
                        condiciones.gastosIniciales(),
                        condiciones.costoAdicionalPorCuota()
                );
                ResultadoCalculo altRes = calculoService.calcular(altCond);
                comparacionPlazos.add(ComparacionPlazoDTO.builder()
                        .cantidadCuotas(altCuotas)
                        .valorCuota(altRes.valorCuota())
                        .costoTotalCredito(altRes.costoTotalCredito())
                        .interesesYCostos(altRes.interesesYCostos())
                        .cargaAnualEquivalente(toPorcentaje(altRes.cargaAnualEquivalente()))
                        .build());
            }
        }

        // 10. Advertencias
        List<String> advertencias = new ArrayList<>();
        advertencias.add(ADVERTENCIA_CAE);
        if (porcentajeIngresoComprometido != null && porcentajeIngresoComprometido.compareTo(UMBRAL_INGRESO_COMPROMETIDO) > 0) {
            advertencias.add(ADVERTENCIA_SOBRE_ENDEUDAMIENTO);
        }

        return SimulacionCreditoResponseDTO.builder()
                .condiciones(condicionesNormalizadas)
                .valorCuota(resultado.valorCuota())
                .montoTotal(resultado.montoTotal())
                .gastosIniciales(resResolucion.gastosIniciales().setScale(ESCALA_DINERO, REDONDEO))
                .costoTotalCredito(resultado.costoTotalCredito())
                .interesesYCostos(resultado.interesesYCostos())
                .cargaAnualEquivalente(toPorcentaje(resultado.cargaAnualEquivalente()))
                .resumenEducativo(resumenEducativo)
                .tablaAmortizacion(tablaAmortizacion)
                .comparacionPlazos(comparacionPlazos)
                .advertencias(advertencias)
                .build();
    }

    private void validarParametrosBasicos(SimulacionCreditoRequestDTO request) {
        if (request == null) {
            throw new CalculoFinancieroException("El request no puede ser nulo");
        }
        if (request.getIngresoMensual() != null && request.getIngresoMensual().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculoFinancieroException("El ingreso mensual debe ser mayor a 0");
        }
    }

    private CondicionesCreditoDTO toCondicionesDTO(SimulacionCreditoRequestDTO req) {
        return CondicionesCreditoDTO.builder()
                .modalidad(req.getModalidad())
                .montoCapital(req.getMontoCapital())
                .cantidadCuotas(req.getCantidadCuotas())
                .fechaPrimeraCuota(req.getFechaPrimeraCuota())
                .fechaUltimaCuota(req.getFechaUltimaCuota())
                .tasaMensual(req.getTasaMensual())
                .tasaAnualEfectiva(req.getTasaAnualEfectiva())
                .valorCuota(req.getValorCuota())
                .gastosIniciales(req.getGastosIniciales())
                .costoAdicionalPorCuota(req.getCostoAdicionalPorCuota())
                .build();
    }

    private void validarCuotasAlternativas(SimulacionCreditoRequestDTO request) {
        if (request.getCantidadCuotasAlternativas() != null && !request.getCantidadCuotasAlternativas().isEmpty()) {
            if (request.getModalidad() == ModalidadCalculo.CUOTA_CONOCIDA) {
                throw new CalculoFinancieroException("CUOTA_CONOCIDA no admite cuotas alternativas");
            }
            if (request.getCantidadCuotasAlternativas().size() > 5) {
                throw new CalculoFinancieroException("Máximo 5 plazos alternativos permitidos");
            }
            for (Integer cuotas : request.getCantidadCuotasAlternativas()) {
                if (cuotas == null || cuotas < 1 || cuotas > 360) {
                    throw new CalculoFinancieroException("Las cuotas alternativas deben estar entre 1 y 360");
                }
            }
        }
    }

    private BigDecimal toPorcentaje(BigDecimal fraccion) {
        if (fraccion == null) {
            return null;
        }
        return fraccion.multiply(CIEN).setScale(ESCALA_DINERO, REDONDEO);
    }

    private BigDecimal toPorcentajeTasa(BigDecimal fraccion) {
        if (fraccion == null) {
            return null;
        }
        return fraccion.multiply(CIEN).setScale(ESCALA_TASA, REDONDEO);
    }
}
