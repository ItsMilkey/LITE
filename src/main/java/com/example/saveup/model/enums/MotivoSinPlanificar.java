package com.example.saveup.model.enums;

/**
 * Motivo por el que un gasto real del mes no se pudo imputar a una partida
 * (T10c, sección 5).
 *
 * <ul>
 *   <li>{@link #CATEGORIA_AMBIGUA}: la categoría del gasto tiene dos o más
 *       partidas en el plan, así que no hay una única candidata.</li>
 *   <li>{@link #SIN_PARTIDA}: la categoría no tiene ninguna partida o el mes
 *       no tiene plan.</li>
 * </ul>
 */
public enum MotivoSinPlanificar {
    CATEGORIA_AMBIGUA,
    SIN_PARTIDA
}
