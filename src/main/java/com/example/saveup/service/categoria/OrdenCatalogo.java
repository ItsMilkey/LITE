package com.example.saveup.service.categoria;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.TipoPresupuesto;

import java.util.Comparator;
import java.util.List;

/**
 * Orden estable del catálogo de categorías (T10a): por tipo
 * (NECESIDAD → DESEO → AHORRO → OTROS) y luego por nombre. Lo usan el listado
 * del catálogo y el de preferencias, de modo que ambos presenten las filas
 * igual (T10d, sección 3).
 */
public final class OrdenCatalogo {

    /** Orden estable de los tipos. */
    private static final List<TipoPresupuesto> ORDEN_TIPOS = List.of(
            TipoPresupuesto.NECESIDAD,
            TipoPresupuesto.DESEO,
            TipoPresupuesto.AHORRO,
            TipoPresupuesto.OTROS);

    /** Comparador único del catálogo. */
    public static final Comparator<Categoria> CATALOGO =
            Comparator.comparingInt((Categoria c) -> {
                        int indice = ORDEN_TIPOS.indexOf(c.getTipoPresupuesto());
                        return indice < 0 ? ORDEN_TIPOS.size() : indice;
                    })
                    .thenComparing(Categoria::getNombre, String.CASE_INSENSITIVE_ORDER);

    private OrdenCatalogo() {
    }
}
