package com.example.saveup.service.categoria;

import com.example.saveup.dto.SugerenciaCategoriaResponseDTO;
import com.example.saveup.error.ParametroInvalidoException;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.enums.ContextoSugerencia;
import com.example.saveup.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Puente entre el endpoint y la lógica pura del sugeridor: valida el largo del
 * texto, lee el catálogo y le pasa el diccionario filtrado y el historial.
 */
@Service
public class SugerenciaCategoriaService {

    static final int MAX_CARACTERES = 80;

    private final CategoriaRepository categoriaRepository;
    private final DiccionarioCategorias diccionario;
    private final FuenteHistorialCategoria historial;
    private final SugeridorCategoria sugeridor = new SugeridorCategoria();

    public SugerenciaCategoriaService(CategoriaRepository categoriaRepository,
                                      DiccionarioCategorias diccionario,
                                      FuenteHistorialCategoria historial) {
        this.categoriaRepository = categoriaRepository;
        this.diccionario = diccionario;
        this.historial = historial;
    }

    @Transactional(readOnly = true)
    public SugerenciaCategoriaResponseDTO sugerir(String rut, String texto, ContextoSugerencia contexto) {
        String textoOriginal = texto == null ? "" : texto;
        if (textoOriginal.trim().length() > MAX_CARACTERES) {
            throw new ParametroInvalidoException(
                    "El texto no puede superar los " + MAX_CARACTERES + " caracteres.");
        }

        List<Categoria> catalogo = categoriaRepository.findAll();
        Set<String> codigos = catalogo.stream()
                .map(Categoria::getCodigo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return sugeridor.sugerir(textoOriginal, contexto, catalogo,
                diccionario.entradasValidas(codigos), rut, historial);
    }
}
