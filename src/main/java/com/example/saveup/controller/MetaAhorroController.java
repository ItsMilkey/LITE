package com.example.saveup.controller;

import com.example.saveup.dto.AbonoRetiroDTO;
import com.example.saveup.dto.MetaAhorroCreacionDTO;
import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MetaAhorroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metas")
@Tag(name = "Metas de ahorro", description = "Metas, abonos, retiros y edición.")
@SecurityRequirement(name = "bearerAuth")
public class MetaAhorroController {

    private final MetaAhorroService metaAhorroService;

    private final SecurityUtils securityUtils;

    public MetaAhorroController(MetaAhorroService metaAhorroService, SecurityUtils securityUtils) {
        this.metaAhorroService = metaAhorroService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    @Operation(summary = "Crea una meta de ahorro", operationId = "crearMeta")
    public ResponseEntity<MetaAhorroResponseDTO> crearMeta(
            @Valid @RequestBody MetaAhorroCreacionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metaAhorroService.crearMeta(dto));
    }

    /**
     * Endpoint preferido (Implicit Context): Obtiene las metas del usuario autenticado.
     */
    @GetMapping("/me")
    @Operation(summary = "Lista las metas del usuario autenticado", operationId = "obtenerMetasMe")
    public ResponseEntity<List<MetaAhorroResponseDTO>> obtenerMetasMe() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(metaAhorroService.obtenerMetasPorUsuario(rutAutenticado));
    }

    @PostMapping("/{metaId}/abonar")
    @Operation(summary = "Abona dinero a una meta", operationId = "abonarAMeta")
    public ResponseEntity<MetaAhorroResponseDTO> abonarAMeta(
            @PathVariable Long metaId, @Valid @RequestBody AbonoRetiroDTO dto) {
        return ResponseEntity.ok(metaAhorroService.realizarAbono(metaId, dto));
    }

    @PostMapping("/{metaId}/retirar")
    @Operation(summary = "Retira dinero de una meta", operationId = "retirarDeMeta")
    public ResponseEntity<MetaAhorroResponseDTO> retirarDeMeta(
            @PathVariable Long metaId, @Valid @RequestBody AbonoRetiroDTO dto) {
        return ResponseEntity.ok(metaAhorroService.realizarRetiro(metaId, dto));
    }

    @PutMapping("/{metaId}")
    @Operation(summary = "Edita una meta de ahorro", operationId = "editarMeta")
    public ResponseEntity<MetaAhorroResponseDTO> editarMeta(
            @PathVariable Long metaId, @Valid @RequestBody MetaAhorroCreacionDTO dto) {
        return ResponseEntity.ok(metaAhorroService.editarMeta(metaId, dto));
    }

    @DeleteMapping("/{metaId}")
    @Operation(summary = "Elimina una meta y reintegra su ahorro", operationId = "eliminarMeta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> eliminarMeta(@PathVariable Long metaId) {
        metaAhorroService.eliminarMeta(metaId);
        return ResponseEntity.noContent().build();
    }
}
