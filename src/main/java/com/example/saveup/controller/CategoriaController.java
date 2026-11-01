package com.example.saveup.controller;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.dto.SugerenciaCategoriaResponseDTO;
import com.example.saveup.model.enums.ContextoSugerencia;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.CategoriaService;
import com.example.saveup.service.categoria.SugerenciaCategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorías", description = "Catálogo de categorías de movimientos y sugerencias por texto.")
@SecurityRequirement(name = "bearerAuth")
public class CategoriaController {

    private final CategoriaService categoriaService;
    private final SugerenciaCategoriaService sugerenciaCategoriaService;
    private final SecurityUtils securityUtils;

    public CategoriaController(CategoriaService categoriaService,
                               SugerenciaCategoriaService sugerenciaCategoriaService,
                               SecurityUtils securityUtils) {
        this.categoriaService = categoriaService;
        this.sugerenciaCategoriaService = sugerenciaCategoriaService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    @Operation(summary = "Lista todas las categorías", operationId = "obtenerCategorias")
    public ResponseEntity<List<CategoriaDTO>> obtenerTodas() {
        return ResponseEntity.ok(categoriaService.obtenerTodas());
    }

    @GetMapping("/sugerencia")
    @Operation(summary = "Sugiere hasta 3 categorías para un texto libre",
            description = """
                    Devuelve hasta 3 categorías del catálogo para preseleccionar el menú al
                    registrar un gasto o una partida. No persiste nada y no registra el texto.
                    Texto ausente, de más de 80 caracteres o contexto desconocido → 400
                    PARAMETRO_INVALIDO. Menos de 3 caracteres normalizados → 200 con lista vacía.
                    """,
            operationId = "sugerirCategoria")
    public ResponseEntity<SugerenciaCategoriaResponseDTO> sugerirCategoria(
            @Parameter(description = "Texto libre del usuario (máximo 80 caracteres).", required = true)
            @RequestParam String texto,
            @Parameter(description = "GASTO (por defecto) o INGRESO.")
            @RequestParam(defaultValue = "GASTO") ContextoSugerencia contexto) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(sugerenciaCategoriaService.sugerir(rut, texto, contexto));
    }
}
