package com.example.saveup.service.finanzas;

import com.example.saveup.dto.CondicionesCreditoInput;
import com.example.saveup.model.enums.ModalidadCalculo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

public class ResolutorCondiciones {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final CalculoFinancieroService calculoService;
    private final CalendarioCuotas calendarioCuotas;

    public ResolutorCondiciones(CalculoFinancieroService calculoService, CalendarioCuotas calendarioCuotas) {
        this.calculoService = calculoService;
        this.calendarioCuotas = calendarioCuotas;
    }

    public record ResultadoResolucion(
            ModalidadCalculo modalidad,
            BigDecimal montoCapital,
            int cantidadCuotas,
            LocalDate fechaPrimeraCuota,
            LocalDate fechaUltimaCuota,
            BigDecimal tasaMensualFraccion,
            BigDecimal valorCuotaInput,
            BigDecimal gastosIniciales,
            BigDecimal costoAdicionalPorCuota
    ) {}

    public ResultadoResolucion resolver(CondicionesCreditoInput dto) {
        if (dto == null) {
            throw new CalculoFinancieroException("Las condiciones de crédito no pueden ser nulas");
        }

        ModalidadCalculo modalidad = dto.getModalidad();
        if (modalidad == null) {
            throw new CalculoFinancieroException("La modalidad es obligatoria");
        }

        BigDecimal montoCapital = dto.getMontoCapital();
        if (montoCapital == null || montoCapital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculoFinancieroException("El monto de capital debe ser mayor a 0");
        }

        BigDecimal gastosIniciales = dto.getGastosIniciales() != null ? dto.getGastosIniciales() : BigDecimal.ZERO;
        if (gastosIniciales.compareTo(BigDecimal.ZERO) < 0) {
            throw new CalculoFinancieroException("Los gastos iniciales no pueden ser negativos");
        }

        BigDecimal costoAdicional = dto.getCostoAdicionalPorCuota() != null ? dto.getCostoAdicionalPorCuota() : BigDecimal.ZERO;
        if (costoAdicional.compareTo(BigDecimal.ZERO) < 0) {
            throw new CalculoFinancieroException("El costo adicional por cuota no puede ser negativo");
        }

        int cantidadCuotas;
        LocalDate fechaUltimaCuotaNormalizada = null;
        boolean tieneCantidadCuotas = dto.getCantidadCuotas() != null;
        boolean tieneFechasCompletas = dto.getFechaPrimeraCuota() != null && dto.getFechaUltimaCuota() != null;

        if (tieneCantidadCuotas && dto.getFechaUltimaCuota() != null) {
            throw new CalculoFinancieroException("No se puede especificar cantidadCuotas junto con fechaUltimaCuota");
        }

        if (tieneCantidadCuotas) {
            cantidadCuotas = dto.getCantidadCuotas();
            if (cantidadCuotas < 1 || cantidadCuotas > 360) {
                throw new CalculoFinancieroException("La cantidad de cuotas debe estar entre 1 y 360");
            }
            if (dto.getFechaPrimeraCuota() != null) {
                var fechasVencimiento = calendarioCuotas.fechasVencimiento(dto.getFechaPrimeraCuota(), cantidadCuotas);
                fechaUltimaCuotaNormalizada = fechasVencimiento.get(fechasVencimiento.size() - 1);
            }
        } else if (tieneFechasCompletas) {
            cantidadCuotas = calendarioCuotas.contarCuotas(dto.getFechaPrimeraCuota(), dto.getFechaUltimaCuota());
            if (cantidadCuotas < 1 || cantidadCuotas > 360) {
                throw new CalculoFinancieroException("La cantidad de cuotas calculada debe estar entre 1 y 360");
            }
            var fechasVencimiento = calendarioCuotas.fechasVencimiento(dto.getFechaPrimeraCuota(), cantidadCuotas);
            fechaUltimaCuotaNormalizada = fechasVencimiento.get(fechasVencimiento.size() - 1);
        } else {
            throw new CalculoFinancieroException("Debe indicar cantidadCuotas o fechas en las condiciones");
        }

        BigDecimal tasaMensualFraccion = null;
        BigDecimal valorCuotaInput = null;

        // Nota: no llamamos a validarReglasModalidad aquí porque la tasaMensual
        // aún no se ha resuelto (viene en % en el DTO). Las reglas de "no admite tasa"
        // se validan abajo con los campos del DTO directamente.

        switch (modalidad) {
            case SIN_INTERES -> {
                if ((dto.getTasaMensual() != null && dto.getTasaMensual().compareTo(BigDecimal.ZERO) != 0)
                        || (dto.getTasaAnualEfectiva() != null && dto.getTasaAnualEfectiva().compareTo(BigDecimal.ZERO) != 0)) {
                    throw new CalculoFinancieroException("SIN_INTERES no admite tasas de interés");
                }
                if (dto.getValorCuota() != null) {
                    throw new CalculoFinancieroException("SIN_INTERES no admite valorCuota");
                }
                tasaMensualFraccion = BigDecimal.ZERO;
            }
            case TASA_CONOCIDA -> {
                if (dto.getValorCuota() != null) {
                    throw new CalculoFinancieroException("TASA_CONOCIDA no admite valorCuota");
                }
                if (dto.getTasaMensual() != null && dto.getTasaAnualEfectiva() != null) {
                    throw new CalculoFinancieroException("No se puede especificar tasaMensual y tasaAnualEfectiva juntas");
                }
                if (dto.getTasaMensual() == null && dto.getTasaAnualEfectiva() == null) {
                    throw new CalculoFinancieroException("TASA_CONOCIDA requiere tasaMensual o tasaAnualEfectiva");
                }
                if (dto.getTasaMensual() != null) {
                    if (dto.getTasaMensual().compareTo(BigDecimal.ZERO) < 0 || dto.getTasaMensual().compareTo(CIEN) > 0) {
                        throw new CalculoFinancieroException("La tasa mensual debe estar entre 0 y 100%");
                    }
                    tasaMensualFraccion = dto.getTasaMensual().divide(CIEN, MC);
                } else {
                    if (dto.getTasaAnualEfectiva().compareTo(BigDecimal.ZERO) < 0) {
                        throw new CalculoFinancieroException("La tasa anual efectiva debe ser mayor o igual a 0");
                    }
                    tasaMensualFraccion = calculoService.tasaAnualAMensual(dto.getTasaAnualEfectiva().divide(CIEN, MC));
                }
            }
            case CUOTA_CONOCIDA -> {
                if (dto.getValorCuota() == null || dto.getValorCuota().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new CalculoFinancieroException("CUOTA_CONOCIDA requiere valorCuota mayor a 0");
                }
                valorCuotaInput = dto.getValorCuota();
            }
        }

        return new ResultadoResolucion(
                modalidad,
                montoCapital,
                cantidadCuotas,
                dto.getFechaPrimeraCuota(),
                fechaUltimaCuotaNormalizada,
                tasaMensualFraccion,
                valorCuotaInput,
                gastosIniciales,
                costoAdicional
        );
    }
}
