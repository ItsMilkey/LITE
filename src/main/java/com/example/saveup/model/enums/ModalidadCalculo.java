package com.example.saveup.model.enums;

/**
 * Modalidad de cálculo para el motor financiero.
 * T1c lo persistirá; los CHECK de BD se actualizarán en esa migración.
 */
public enum ModalidadCalculo {
    SIN_INTERES,
    TASA_CONOCIDA,
    CUOTA_CONOCIDA
}
