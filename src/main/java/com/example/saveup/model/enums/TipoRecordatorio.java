package com.example.saveup.model.enums;

/**
 * Momento del aviso de vencimiento de una cuota (T9.6). Cada tipo se envía una
 * sola vez por (deuda, cuota): la clave está en {@code RECORDATORIO_ENVIADO}.
 */
public enum TipoRecordatorio {

    /** La cuota vence en {@code app.push.dias-antes} días. */
    PROXIMA_VENCER,

    /** La cuota vence hoy. */
    VENCE_HOY,

    /** La cuota venció ayer y no se ha pagado. */
    VENCIDA
}
