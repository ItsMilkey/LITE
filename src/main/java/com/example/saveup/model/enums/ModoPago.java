package com.example.saveup.model.enums;

/**
 * Modo de determinación del monto de un pago de deuda. No se persiste:
 * solo decide con qué monto se registra el movimiento PAGO_DEUDA.
 */
public enum ModoPago {

    /** El monto enviado se ignora: se cobra lo que falta de la próxima cuota impaga. */
    CUOTA_CALCULADA,

    /** Se cobra el monto enviado por el cliente (obligatorio y mayor a 0). */
    MONTO_PERSONALIZADO
}
