package com.example.saveup.model;

/**
 * Códigos estables del catálogo de categorías (T10a). El backend busca y
 * compara por estos valores, nunca por el nombre visible.
 *
 * <p>Los valores coinciden con {@code categoria.codigo} y con el CHECK
 * {@code chk_categoria_codigo}; si se agrega una categoría al catálogo, se
 * agrega aquí la constante correspondiente.
 */
public final class CodigoCategoria {

    public static final String SUELDO = "SUELDO";
    public static final String ALIMENTACION_BASE = "ALIMENTACION_BASE";
    public static final String TRANSPORTE_MOVILIDAD = "TRANSPORTE_MOVILIDAD";
    public static final String VIVIENDA_SUMINISTROS = "VIVIENDA_SUMINISTROS";
    public static final String SALUD_BIENESTAR = "SALUD_BIENESTAR";
    public static final String OCIO_RECREACION = "OCIO_RECREACION";
    public static final String CUIDADO_PERSONAL_VESTUARIO = "CUIDADO_PERSONAL_VESTUARIO";
    public static final String DEUDAS = "DEUDAS";
    public static final String AHORRO = "AHORRO";
    public static final String OTROS = "OTROS";
    public static final String EDUCACION_FORMACION = "EDUCACION_FORMACION";
    public static final String SEGUROS_IMPUESTOS = "SEGUROS_IMPUESTOS";
    public static final String MASCOTAS = "MASCOTAS";
    public static final String HOGAR_CONSUMIBLES = "HOGAR_CONSUMIBLES";
    public static final String EQUIPAMIENTO_TECNOLOGIA = "EQUIPAMIENTO_TECNOLOGIA";
    public static final String SUSCRIPCIONES_DIGITAL = "SUSCRIPCIONES_DIGITAL";
    public static final String RESTAURANTES_SALIDAS = "RESTAURANTES_SALIDAS";
    public static final String REGALOS_CELEBRACIONES = "REGALOS_CELEBRACIONES";

    private CodigoCategoria() {
    }
}
