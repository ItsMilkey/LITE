package com.example.saveup.service.plan;

import com.example.saveup.model.Deuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.service.finanzas.CalculoFinancieroException;
import com.example.saveup.service.finanzas.CondicionesCredito;
import com.example.saveup.service.finanzas.CuotaAmortizacion;
import com.example.saveup.service.finanzas.MotorFinanciero;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carga en lote la cuota de cada deuda que vence en un mes (T10d, sección 7).
 *
 * <p>Es el único adaptador entre el plan y el motor financiero: reconstruye las
 * {@link CondicionesCredito} desde la deuda persistida con las mismas reglas
 * que {@code DeudaService} y devuelve cuota + costo adicional. Un vínculo que
 * ya no se puede calcular se informa como {@link Optional#empty()}; la lectura
 * del plan conserva entonces el último monto guardado.
 */
@Component
public class CuotasMesService {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private final MotorFinanciero motorFinanciero;

    public CuotasMesService(MotorFinanciero motorFinanciero) {
        this.motorFinanciero = motorFinanciero;
    }

    /** Cuota que vence en el mes, con el costo adicional ya sumado. */
    public record CuotaDelMes(int numero, BigDecimal monto) {
    }

    /**
     * Cuotas de todas las deudas indicadas para el mes, indexadas por id. Solo
     * aparecen las deudas con una cuota que vence en ese mes.
     */
    public Map<Long, CuotaDelMes> cuotasDelMes(Collection<Deuda> deudas, YearMonth mes) {
        Map<Long, CuotaDelMes> cuotas = new HashMap<>();
        if (deudas == null) {
            return cuotas;
        }
        for (Deuda deuda : deudas) {
            if (deuda == null || deuda.getId() == null) {
                continue;
            }
            buscar(deuda, mes).ifPresent(cuota -> cuotas.put(deuda.getId(), cuota));
        }
        return cuotas;
    }

    /**
     * Cuota de la deuda que vence en el mes; vacío si no hay calendario, si el
     * mes queda fuera del cronograma o si las condiciones ya no calculan.
     */
    public Optional<CuotaDelMes> buscar(Deuda deuda, YearMonth mes) {
        if (deuda == null || mes == null || deuda.getFechaPrimeraCuota() == null
                || deuda.getCantidadCuotas() <= 0) {
            return Optional.empty();
        }
        try {
            CondicionesCredito condiciones = condiciones(deuda);
            List<CuotaAmortizacion> tabla = motorFinanciero.calcular(condiciones).tabla();
            List<LocalDate> fechas = motorFinanciero.fechasVencimiento(
                    deuda.getFechaPrimeraCuota(), deuda.getCantidadCuotas());
            for (int i = 0; i < fechas.size() && i < tabla.size(); i++) {
                if (YearMonth.from(fechas.get(i)).equals(mes)) {
                    CuotaAmortizacion fila = tabla.get(i);
                    BigDecimal monto = fila.cuota().add(fila.costoAdicional())
                            .setScale(ESCALA_DINERO, REDONDEO);
                    return Optional.of(new CuotaDelMes(fila.numero(), monto));
                }
            }
            return Optional.empty();
        } catch (CalculoFinancieroException | IllegalArgumentException e) {
            // Condiciones editadas o cronograma inválido: el plan no falla, el
            // vínculo queda desactualizado y se muestra el último monto.
            return Optional.empty();
        }
    }

    /** Mismas condiciones que usa {@code DeudaService} para su cronograma. */
    private static CondicionesCredito condiciones(Deuda deuda) {
        ModalidadCalculo modalidad = deuda.getModalidadCalculo() != null
                ? deuda.getModalidadCalculo()
                : ModalidadCalculo.SIN_INTERES;
        boolean cuotaConocida = modalidad == ModalidadCalculo.CUOTA_CONOCIDA;
        return new CondicionesCredito(
                modalidad,
                deuda.getMontoCapital(),
                deuda.getCantidadCuotas(),
                cuotaConocida ? null : deuda.getTasaMensual(),
                cuotaConocida ? deuda.getValorCuota() : null,
                deuda.getGastosIniciales(),
                deuda.getCostoAdicionalPorCuota());
    }
}
