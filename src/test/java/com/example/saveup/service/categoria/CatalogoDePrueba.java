package com.example.saveup.service.categoria;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.TipoPresupuesto;

import java.util.List;

/** Catálogo de T10a con ids estables para las pruebas del sugeridor. */
public final class CatalogoDePrueba {

    private CatalogoDePrueba() {
    }

    public static List<Categoria> categorias() {
        return List.of(
                categoria(1L, "SUELDO", "Sueldo", TipoPresupuesto.OTROS),
                categoria(2L, "ALIMENTACION_BASE", "Alimentación Base", TipoPresupuesto.NECESIDAD),
                categoria(3L, "TRANSPORTE_MOVILIDAD", "Transporte y Movilidad", TipoPresupuesto.NECESIDAD),
                categoria(4L, "VIVIENDA_SUMINISTROS", "Vivienda y Suministros", TipoPresupuesto.NECESIDAD),
                categoria(5L, "SALUD_BIENESTAR", "Salud y Bienestar", TipoPresupuesto.NECESIDAD),
                categoria(6L, "OCIO_RECREACION", "Ocio y Recreación", TipoPresupuesto.DESEO),
                categoria(7L, "CUIDADO_PERSONAL_VESTUARIO", "Cuidado Personal y Vestuario", TipoPresupuesto.DESEO),
                categoria(8L, "DEUDAS", "Pago de Deudas", TipoPresupuesto.NECESIDAD),
                categoria(9L, "AHORRO", "Ahorro y Fondo de Reserva", TipoPresupuesto.AHORRO),
                categoria(10L, "OTROS", "Otros", TipoPresupuesto.OTROS),
                categoria(11L, "EDUCACION_FORMACION", "Educación y Formación", TipoPresupuesto.NECESIDAD),
                categoria(12L, "SEGUROS_IMPUESTOS", "Seguros e Impuestos", TipoPresupuesto.NECESIDAD),
                categoria(13L, "MASCOTAS", "Mascotas", TipoPresupuesto.NECESIDAD),
                categoria(14L, "HOGAR_CONSUMIBLES", "Hogar y Consumibles", TipoPresupuesto.NECESIDAD),
                categoria(15L, "EQUIPAMIENTO_TECNOLOGIA", "Equipamiento y Tecnología", TipoPresupuesto.DESEO),
                categoria(16L, "SUSCRIPCIONES_DIGITAL", "Suscripciones y Digital", TipoPresupuesto.DESEO),
                categoria(17L, "RESTAURANTES_SALIDAS", "Restaurantes y Salidas", TipoPresupuesto.DESEO),
                categoria(18L, "REGALOS_CELEBRACIONES", "Regalos y Celebraciones", TipoPresupuesto.DESEO));
    }

    private static Categoria categoria(Long id, String codigo, String nombre, TipoPresupuesto tipo) {
        return new Categoria(id, codigo, nombre, "ic_test", "#000000", tipo);
    }
}
