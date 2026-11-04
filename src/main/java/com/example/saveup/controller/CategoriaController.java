package com.example.saveup.controller;

import com.example.saveup.dto.CategoriaDTO;
import com.example.saveup.dto.PreferenciaCategoriaDTO;
import com.example.saveup.dto.SugerenciaCategoriaResponseDTO;
import com.example.saveup.dto.TipoPreferidoRequestDTO;
import com.example.saveup.model.enums.ContextoSugerencia;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.CategoriaService;
import com.example.saveup.service.categoria.PreferenciaTipoService;
import com.example.saveup.service.categoria.SugerenciaCategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorías", description = "Catálogo de categorías, sugerencias por texto y preferencia de tipo.")
@SecurityRequirement(name = "bearerAuth")
public class CategoriaController {

    private final CategoriaService categoriaService;
    private final SugerenciaCategoriaService sugerenciaCategoriaService;
    private final PreferenciaTipoService preferenciaTipoService;
    private final SecurityUtils securityUtils;

    public CategoriaController(CategoriaService categoriaService,
                               SugerenciaCategoriaService sugerenciaCategoriaService,
                               PreferenciaTipoService preferenciaTipoService,
                               SecurityUtils securityUtils) {
        this.categoriaService = categoriaService;
        this.sugerenciaCategoriaService = sugerenciaCategoriaService;
        this.preferenciaTipoService = preferenciaTipoService;
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

    /** Preferencias de tipo del usuario (T10d): catálogo con tipoPorDefecto, tipoPreferido y tipoEfectivo. */
    @GetMapping("/preferencias")
    @Operation(summary = "Lista las categorías con su tipo por defecto, preferido y efectivo",
            description = """
                    El tipo efectivo es el preferido del usuario si existe y, si no, el tipo
                    por defecto del catálogo. Ordena igual que el catálogo: NECESIDAD →
                    DESEO → AHORRO → OTROS y luego por nombre.
                    """,
            operationId = "obtenerPreferenciasTipo")
    public ResponseEntity<List<PreferenciaCategoriaDTO>> obtenerPreferenciasTipo() {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(preferenciaTipoService.listar(rut));
    }

    /**
     * Reclasifica una categoría para el usuario (T10d). Solo NECESIDAD o DESEO;
     * AHORRO u OTROS → 400 DATOS_INVALIDOS; categoría inexistente → 404.
     */
    @PutMapping("/{id}/tipo-preferido")
    @Operation(summary = "Establece el tipo preferido de una categoría",
            description = """
                    Reclasifica la categoría como NECESIDAD o DESEO para el usuario
                    autenticado. El tipo efectivo pasa a ser ese valor en toda la app
                    (ejecución 50/30/20, plan y sugerencia). Cuerpo: {"tipo": "NECESIDAD"}.
                    """,
            operationId = "establecerTipoPreferido")
    public ResponseEntity<PreferenciaCategoriaDTO> establecerTipoPreferido(
            @Parameter(description = "Identificador de la categoría.", required = true)
            @PathVariable Long id,
            @Valid @RequestBody TipoPreferidoRequestDTO request) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(preferenciaTipoService.actualizar(rut, id, request.getTipo()));
    }

    /** Vuelve al tipo por defecto quitando la preferencia (T10d). Idempotente: 204 siempre que la categoría exista. */
    @DeleteMapping("/{id}/tipo-preferido")
    @Operation(summary = "Elimina el tipo preferido de una categoría",
            description = "La categoría vuelve a su tipo por defecto. Es idempotente: responde 204 aunque no tuviera preferencia previa.",
            operationId = "eliminarTipoPreferido")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Preferencia eliminada (idempotente)")
    })
    public ResponseEntity<Void> eliminarTipoPreferido(
            @Parameter(description = "Identificador de la categoría.", required = true)
            @PathVariable Long id) {
        String rut = securityUtils.getAuthenticatedRut();
        preferenciaTipoService.eliminar(rut, id);
        return ResponseEntity.noContent().build();
    }
}
