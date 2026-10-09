package com.example.saveup.service.finanzas;

import com.example.saveup.dto.CondicionesCreditoInput;
import org.springframework.stereotype.Component;

/**
 * Fachada que agrupa los tres componentes del motor financiero:
 * {@link CalculoFinancieroService}, {@link CalendarioCuotas} y {@link ResolutorCondiciones}.
 * Elimina el "data clump" de tener que wirear los tres juntos en cada servicio.
 */
@Component
public class MotorFinanciero {

    private final CalculoFinancieroService calculoService;
    private final CalendarioCuotas calendarioCuotas;
    private final ResolutorCondiciones resolutorCondiciones;

    public MotorFinanciero() {
        this.calculoService = new CalculoFinancieroService();
        this.calendarioCuotas = new CalendarioCuotas();
        this.resolutorCondiciones = new ResolutorCondiciones(calculoService, calendarioCuotas);
    }

    public MotorFinanciero(CalculoFinancieroService calculoService, CalendarioCuotas calendarioCuotas) {
        this.calculoService = calculoService;
        this.calendarioCuotas = calendarioCuotas;
        this.resolutorCondiciones = new ResolutorCondiciones(calculoService, calendarioCuotas);
    }

    public CalculoFinancieroService calculoService() {
        return calculoService;
    }

    public CalendarioCuotas calendarioCuotas() {
        return calendarioCuotas;
    }

    public ResolutorCondiciones resolutorCondiciones() {
        return resolutorCondiciones;
    }

    /** Resuelve las condiciones de entrada (DTO) en condiciones normalizadas. */
    public ResolutorCondiciones.ResultadoResolucion resolver(CondicionesCreditoInput dto) {
        return resolutorCondiciones.resolver(dto);
    }

    /** Ejecuta el cálculo financiero completo. */
    public ResultadoCalculo calcular(CondicionesCredito condiciones) {
        return calculoService.calcular(condiciones);
    }

    /** Genera las fechas de vencimiento. */
    public java.util.List<java.time.LocalDate> fechasVencimiento(java.time.LocalDate primeraCuota, int cantidadCuotas) {
        return calendarioCuotas.fechasVencimiento(primeraCuota, cantidadCuotas);
    }
}
