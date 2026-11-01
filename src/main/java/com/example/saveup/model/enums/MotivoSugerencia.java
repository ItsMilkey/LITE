package com.example.saveup.model.enums;

/** Motivo por el que una categoría fue sugerida (T10b). */
public enum MotivoSugerencia {

    /** Coincidencia exacta de una palabra o de una frase completa. */
    PALABRA_CLAVE,

    /** El texto que escribe el usuario es prefijo de una entrada de una palabra. */
    PREFIJO,

    /** Coincidencia aproximada (Damerau-Levenshtein) con una entrada de una palabra. */
    APROXIMADA,

    /** La fuente de historial del usuario propuso la categoría. */
    HISTORIAL
}
