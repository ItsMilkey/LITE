package com.example.saveup.model.enums;

/**
 * Enum para categorizar los diferentes tipos de transacciones monetarias.
 * Esto permite un filtrado y una lógica de negocio más claros.
 */
public enum TipoMovimiento {
    INGRESO_GENERAL,
    GASTO_GENERAL,
    PAGO_DEUDA,
    ABONO_META,
    RETIRO_META
}