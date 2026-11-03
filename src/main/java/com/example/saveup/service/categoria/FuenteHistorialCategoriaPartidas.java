package com.example.saveup.service.categoria;

import com.example.saveup.model.PartidaPlan;
import com.example.saveup.repository.PartidaPlanRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Historial real del sugeridor (T10c, sección 10): la categoría de la partida
 * más reciente del mismo usuario con ese nombre normalizado.
 *
 * <p>El texto se normaliza con {@code NombrePartida.normalizar} y no con el
 * normalizador del sugeridor: {@code partida_plan.nombre_normalizado} conserva
 * palabras vacías y puntuación, así que ambos lados deben usar la misma regla.
 * Solo se consultan datos del propio usuario.
 */
@Component
public class FuenteHistorialCategoriaPartidas implements FuenteHistorialCategoria {

    private final PartidaPlanRepository partidaPlanRepository;

    public FuenteHistorialCategoriaPartidas(PartidaPlanRepository partidaPlanRepository) {
        this.partidaPlanRepository = partidaPlanRepository;
    }

    @Override
    public Optional<String> codigoPara(String rut, String textoNormalizado) {
        if (rut == null || textoNormalizado == null || textoNormalizado.isBlank()) {
            return Optional.empty();
        }
        return partidaPlanRepository
                .buscarHistorial(rut, textoNormalizado, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(PartidaPlan::getCategoria)
                .map(categoria -> categoria.getCodigo());
    }
}
