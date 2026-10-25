package com.example.saveup.service.movimiento;

import com.example.saveup.error.DatosInvalidosException;
import com.example.saveup.model.enums.TipoMovimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * Regla de signo canónico de los movimientos.
 *
 * <p>Clase pura: no depende de Spring ni de la base de datos. El signo de un
 * movimiento no lo decide el cliente ni el productor: lo impone el tipo.
 *
 * <ul>
 *   <li>Signo positivo: {@code INGRESO_GENERAL}, {@code RETIRO_META}</li>
 *   <li>Signo negativo: {@code GASTO_GENERAL}, {@code PAGO_DEUDA}, {@code ABONO_META}</li>
 * </ul>
 *
 * <p>El saldo del usuario es {@code SUM(MOVIMIENTO.monto)}, así que basta con que
 * cada productor guarde el signo canónico para que el saldo sea correcto sin
 * reconciliaciones.
 *
 * <p>Si se agrega un valor a {@link TipoMovimiento} hay que registrarlo aquí en la
 * misma tarea, junto con el CHECK {@code chk_movimiento_signo} de la base de datos.
 */
public final class SignoMovimiento {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Tipos cuyo monto debe guardarse negativo. Los demás se guardan positivos. */
    private static final Map<TipoMovimiento, Boolean> ES_NEGATIVO = new EnumMap<>(TipoMovimiento.class);

    static {
        ES_NEGATIVO.put(TipoMovimiento.INGRESO_GENERAL, Boolean.FALSE);
        ES_NEGATIVO.put(TipoMovimiento.RETIRO_META, Boolean.FALSE);
        ES_NEGATIVO.put(TipoMovimiento.GASTO_GENERAL, Boolean.TRUE);
        ES_NEGATIVO.put(TipoMovimiento.PAGO_DEUDA, Boolean.TRUE);
        ES_NEGATIVO.put(TipoMovimiento.ABONO_META, Boolean.TRUE);
    }

    private SignoMovimiento() {
    }

    /**
     * Devuelve el monto con el signo canónico de su tipo, en escala 2.
     *
     * @param tipo tipo del movimiento; no puede ser nulo.
     * @param monto monto con cualquier signo; no puede ser nulo ni cero.
     * @return el monto en valor absoluto si el tipo es positivo, o negado si es negativo.
     * @throws IllegalArgumentException si el tipo o el monto son nulos, o el monto es cero.
     * @throws IllegalStateException    si el tipo no está registrado (invariante interna).
     */
    public static BigDecimal aplicar(TipoMovimiento tipo, BigDecimal monto) {
        if (tipo == null) {
            throw new DatosInvalidosException("El tipo de movimiento es obligatorio.");
        }
        if (monto == null) {
            throw new DatosInvalidosException("El monto del movimiento es obligatorio.");
        }

        BigDecimal normalizado = monto.abs().setScale(ESCALA_DINERO, REDONDEO);
        if (normalizado.compareTo(BigDecimal.ZERO) == 0) {
            throw new DatosInvalidosException("El monto del movimiento no puede ser 0.");
        }

        Boolean negativo = ES_NEGATIVO.get(tipo);
        if (negativo == null) {
            throw new IllegalStateException(
                    "Tipo de movimiento sin signo definido: " + tipo
                            + ". Regístralo en SignoMovimiento y en el CHECK de la base de datos.");
        }

        return negativo ? normalizado.negate() : normalizado;
    }

    /** Indica si el tipo guarda su monto en negativo. */
    public static boolean esNegativo(TipoMovimiento tipo) {
        if (tipo == null) {
            throw new DatosInvalidosException("El tipo de movimiento es obligatorio.");
        }
        Boolean negativo = ES_NEGATIVO.get(tipo);
        if (negativo == null) {
            throw new IllegalStateException(
                    "Tipo de movimiento sin signo definido: " + tipo);
        }
        return negativo;
    }
}
