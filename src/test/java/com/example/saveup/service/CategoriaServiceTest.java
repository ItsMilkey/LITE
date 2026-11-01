package com.example.saveup.service;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * T10a — Catálogo: el DTO expone `codigo` y el orden es estable
 * (NECESIDAD → DESEO → AHORRO → OTROS y luego nombre).
 */
@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    @DisplayName("A08: las 18 categorías salen con codigo y en orden estable")
    void obtenerTodas_exponeCodigoYOrdenEstable() {
        // Se entregan desordenadas a propósito: el orden lo fija el servicio.
        when(categoriaRepository.findAll()).thenReturn(List.of(
                categoria(1L, "OTROS", "Otros", TipoPresupuesto.OTROS),
                categoria(2L, "SUELDO", "Sueldo", TipoPresupuesto.OTROS),
                categoria(3L, "AHORRO", "Ahorro y Fondo de Reserva", TipoPresupuesto.AHORRO),
                categoria(4L, "OCIO_RECREACION", "Ocio y Recreación", TipoPresupuesto.DESEO),
                categoria(5L, "EQUIPAMIENTO_TECNOLOGIA", "Equipamiento y Tecnología", TipoPresupuesto.DESEO),
                categoria(6L, "TRANSPORTE_MOVILIDAD", "Transporte y Movilidad", TipoPresupuesto.NECESIDAD),
                categoria(7L, "ALIMENTACION_BASE", "Alimentación Base", TipoPresupuesto.NECESIDAD),
                categoria(8L, "SUSCRIPCIONES_DIGITAL", "Suscripciones y Digital", TipoPresupuesto.DESEO),
                categoria(9L, "REGALOS_CELEBRACIONES", "Regalos y Celebraciones", TipoPresupuesto.DESEO),
                categoria(10L, "RESTAURANTES_SALIDAS", "Restaurantes y Salidas", TipoPresupuesto.DESEO),
                categoria(11L, "CUIDADO_PERSONAL_VESTUARIO", "Cuidado Personal y Vestuario", TipoPresupuesto.DESEO),
                categoria(12L, "EDUCACION_FORMACION", "Educación y Formación", TipoPresupuesto.NECESIDAD),
                categoria(13L, "SEGUROS_IMPUESTOS", "Seguros e Impuestos", TipoPresupuesto.NECESIDAD),
                categoria(14L, "MASCOTAS", "Mascotas", TipoPresupuesto.NECESIDAD),
                categoria(15L, "HOGAR_CONSUMIBLES", "Hogar y Consumibles", TipoPresupuesto.NECESIDAD),
                categoria(16L, "VIVIENDA_SUMINISTROS", "Vivienda y Suministros", TipoPresupuesto.NECESIDAD),
                categoria(17L, "SALUD_BIENESTAR", "Salud y Bienestar", TipoPresupuesto.NECESIDAD),
                categoria(18L, "DEUDAS", "Pago de Deudas", TipoPresupuesto.NECESIDAD)));

        List<CategoriaDTO> resultado = categoriaService.obtenerTodas();

        assertEquals(18, resultado.size());
        assertTrue(resultado.stream().allMatch(c -> c.getCodigo() != null && !c.getCodigo().isBlank()),
                "Toda categoría debe exponer codigo");
        assertEquals(18, resultado.stream().map(CategoriaDTO::getCodigo).distinct().count(),
                "Los codigos deben ser únicos");

        // NECESIDAD (por nombre) → DESEO (por nombre) → AHORRO → OTROS (por nombre).
        assertEquals(List.of(
                "ALIMENTACION_BASE",
                "EDUCACION_FORMACION",
                "HOGAR_CONSUMIBLES",
                "MASCOTAS",
                "DEUDAS",
                "SALUD_BIENESTAR",
                "SEGUROS_IMPUESTOS",
                "TRANSPORTE_MOVILIDAD",
                "VIVIENDA_SUMINISTROS",
                "CUIDADO_PERSONAL_VESTUARIO",
                "EQUIPAMIENTO_TECNOLOGIA",
                "OCIO_RECREACION",
                "REGALOS_CELEBRACIONES",
                "RESTAURANTES_SALIDAS",
                "SUSCRIPCIONES_DIGITAL",
                "AHORRO",
                "OTROS",
                "SUELDO"),
                resultado.stream().map(CategoriaDTO::getCodigo).toList());

        CategoriaDTO primera = resultado.get(0);
        assertEquals("ALIMENTACION_BASE", primera.getCodigo());
        assertEquals("Alimentación Base", primera.getNombre());
        assertEquals(TipoPresupuesto.NECESIDAD, primera.getTipoPresupuesto());
        assertNotNull(primera.getIconId());
        assertNotNull(primera.getColorHex());
    }

    private Categoria categoria(Long id, String codigo, String nombre, TipoPresupuesto tipo) {
        return new Categoria(id, codigo, nombre, "ic_test", "#000000", tipo);
    }
}
