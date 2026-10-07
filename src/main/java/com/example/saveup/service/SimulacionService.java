package com.example.saveup.service;

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
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal UMBRAL_INGRESO_COMPROMETIDO = new BigDecimal("30");
    private static final String ADVERTENCIA_CAE = "El CAE mostrado es referencial; el oficial lo informa la institución financiera.";
    private static final String ADVERTENCIA_SOBRE_ENDEUDAMIENTO = "La cuota mensual compromete más del 30% de tus ingresos declarados (se recomienda un máximo de 30%).";

    private final CalculoFinancieroService calculoService;
    private final CalendarioCuotas calendarioCuotas;

    public SimulacionService() {
        this(new CalculoFinancieroService(), new CalendarioCuotas());
    }

    public SimulacionService(CalculoFinancieroService calculoService, CalendarioCuotas calendarioCuotas) {
        this.calculoService = calculoService;
        this.calendarioCuotas = calendarioCuotas;
    }

    public SimulacionCreditoResponseDTO simularCredito(SimulacionCreditoRequestDTO request) {
        validarParametrosBasicos(request);

        // 1. Determinar y validar plazo
        int cantidadCuotas;
        LocalDate fechaUltimaCuotaNormalizada = null;
        List<LocalDate> fechasVencimiento = null;

        boolean tieneCantidadCuotas = request.getCantidadCuotas() != null;
        boolean tieneFechasCompletas = request.getFechaPrimeraCuota() != null && request.getFechaUltimaCuota() != null;

        if (tieneCantidadCuotas && request.getFechaUltimaCuota() != null) {
            throw new CalculoFinancieroException("No se puede especificar cantidadCuotas junto con fechaUltimaCuota");
        }

        if (tieneCantidadCuotas) {
            cantidadCuotas = request.getCantidadCuotas();
            if (cantidadCuotas < 1 || cantidadCuotas > 360) {
                throw new CalculoFinancieroException("La cantidad de cuotas debe estar entre 1 y 360");
            }
            if (request.getFechaPrimeraCuota() != null) {
                fechasVencimiento = calendarioCuotas.fechasVencimiento(request.getFechaPrimeraCuota(), cantidadCuotas);
                fechaUltimaCuotaNormalizada = fechasVencimiento.get(fechasVencimiento.size() - 1);
            }
        } else if (tieneFechasCompletas) {
            cantidadCuotas = calendarioCuotas.contarCuotas(request.getFechaPrimeraCuota(), request.getFechaUltimaCuota());
            if (cantidadCuotas < 1 || cantidadCuotas > 360) {
                throw new CalculoFinancieroException("La cantidad de cuotas calculada debe estar entre 1 y 360");
            }
            fechasVencimiento = calendarioCuotas.fechasVencimiento(request.getFechaPrimeraCuota(), cantidadCuotas);
            fechaUltimaCuotaNormalizada = fechasVencimiento.get(fechasVencimiento.size() - 1);
        } else {
            throw new CalculoFinancieroException("Debe especificar exactamente una forma de plazo: cantidadCuotas o (fechaPrimeraCuota y fechaUltimaCuota)");
        }

        // 2. Gastos opcionales
        BigDecimal gastosIniciales = request.getGastosIniciales() != null ? request.getGastosIniciales() : BigDecimal.ZERO;
        BigDecimal costoAdicionalPorCuota = request.getCostoAdicionalPorCuota() != null ? request.getCostoAdicionalPorCuota() : BigDecimal.ZERO;

        // 3. Validar tasas según modalidad y calcular tasa mensual fraccionaria
        BigDecimal tasaMensualFraccion = resolverTasaMensualFraccion(request, gastosIniciales, costoAdicionalPorCuota);

        // 4. Validar cuotas alternativas
        validarCuotasAlternativas(request);

        // 5. Ejecutar cálculo con motor financiero
        CondicionesCredito condiciones = new CondicionesCredito(
                request.getModalidad(),
                request.getMontoCapital().setScale(ESCALA_DINERO, REDONDEO),
                cantidadCuotas,
                tasaMensualFraccion,
                request.getValorCuota(),
                gastosIniciales.setScale(ESCALA_DINERO, REDONDEO),
                costoAdicionalPorCuota.setScale(ESCALA_DINERO, REDONDEO)
        );

        ResultadoCalculo resultado = calculoService.calcular(condiciones);

        // 6. Construir condiciones normalizadas
        CondicionesSimulacionDTO condicionesNormalizadas = CondicionesSimulacionDTO.builder()
                .modalidad(request.getModalidad())
                .montoCapital(condiciones.montoCapital())
                .cantidadCuotas(cantidadCuotas)
                .fechaPrimeraCuota(request.getFechaPrimeraCuota())
                .fechaUltimaCuota(fechaUltimaCuotaNormalizada)
                .tasaMensual(toPorcentaje(resultado.tasaMensual()))
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
                .gastosIniciales(gastosIniciales.setScale(ESCALA_DINERO, REDONDEO))
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
        if (request.getModalidad() == null) {
            throw new CalculoFinancieroException("La modalidad es obligatoria");
        }
        if (request.getMontoCapital() == null || request.getMontoCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculoFinancieroException("El monto de capital debe ser mayor a 0");
        }
        if (request.getGastosIniciales() != null && request.getGastosIniciales().compareTo(BigDecimal.ZERO) < 0) {
            throw new CalculoFinancieroException("Los gastos iniciales no pueden ser negativos");
        }
        if (request.getCostoAdicionalPorCuota() != null && request.getCostoAdicionalPorCuota().compareTo(BigDecimal.ZERO) < 0) {
            throw new CalculoFinancieroException("El costo adicional por cuota no puede ser negativo");
        }
        if (request.getIngresoMensual() != null && request.getIngresoMensual().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculoFinancieroException("El ingreso mensual debe ser mayor a 0");
        }
    }

    private BigDecimal resolverTasaMensualFraccion(SimulacionCreditoRequestDTO request,
                                                    BigDecimal gastosIniciales,
                                                    BigDecimal costoAdicionalPorCuota) {
        switch (request.getModalidad()) {
            case SIN_INTERES -> {
                if ((request.getTasaMensual() != null && request.getTasaMensual().compareTo(BigDecimal.ZERO) != 0)
                        || (request.getTasaAnualEfectiva() != null && request.getTasaAnualEfectiva().compareTo(BigDecimal.ZERO) != 0)) {
                    throw new CalculoFinancieroException("SIN_INTERES no admite tasas de interés");
                }
                if (request.getValorCuota() != null) {
                    throw new CalculoFinancieroException("SIN_INTERES no admite valorCuota");
                }
                return BigDecimal.ZERO;
            }
            case TASA_CONOCIDA -> {
                if (request.getValorCuota() != null) {
                    throw new CalculoFinancieroException("TASA_CONOCIDA no admite valorCuota");
                }
                if (request.getTasaMensual() != null && request.getTasaAnualEfectiva() != null) {
                    throw new CalculoFinancieroException("No se puede especificar tasaMensual y tasaAnualEfectiva juntas");
                }
                if (request.getTasaMensual() == null && request.getTasaAnualEfectiva() == null) {
                    throw new CalculoFinancieroException("TASA_CONOCIDA requiere tasaMensual o tasaAnualEfectiva");
                }
                if (request.getTasaMensual() != null) {
                    if (request.getTasaMensual().compareTo(BigDecimal.ZERO) < 0
                            || request.getTasaMensual().compareTo(CIEN) > 0) {
                        throw new CalculoFinancieroException("La tasa mensual debe estar entre 0 y 100%");
                    }
                    return request.getTasaMensual().divide(CIEN, MC);
                } else {
                    if (request.getTasaAnualEfectiva().compareTo(BigDecimal.ZERO) < 0) {
                        throw new CalculoFinancieroException("La tasa anual efectiva debe ser mayor o igual a 0");
                    }
                    BigDecimal tasaAnualFraccion = request.getTasaAnualEfectiva().divide(CIEN, MC);
                    return calculoService.tasaAnualAMensual(tasaAnualFraccion);
                }
            }
            case CUOTA_CONOCIDA -> {
                if (request.getTasaMensual() != null || request.getTasaAnualEfectiva() != null) {
                    throw new CalculoFinancieroException("CUOTA_CONOCIDA no admite tasa de interés");
                }
                if (request.getValorCuota() == null || request.getValorCuota().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new CalculoFinancieroException("CUOTA_CONOCIDA requiere valorCuota mayor a 0");
                }
                if (gastosIniciales.compareTo(BigDecimal.ZERO) != 0) {
                    throw new CalculoFinancieroException("CUOTA_CONOCIDA no admite gastosIniciales");
                }
                if (costoAdicionalPorCuota.compareTo(BigDecimal.ZERO) != 0) {
                    throw new CalculoFinancieroException("CUOTA_CONOCIDA no admite costoAdicionalPorCuota");
                }
                return null;
            }
            default -> throw new CalculoFinancieroException("Modalidad no soportada: " + request.getModalidad());
        }
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
}
