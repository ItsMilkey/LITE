package com.example.saveup.controller;

import com.example.saveup.dto.AbonoRetiroDTO;
import com.example.saveup.dto.MetaAhorroCreacionDTO;
import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MetaAhorroService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metas")
public class MetaAhorroController {

    private final MetaAhorroService metaAhorroService;

    private final SecurityUtils securityUtils;

    public MetaAhorroController(MetaAhorroService metaAhorroService, SecurityUtils securityUtils) {
        this.metaAhorroService = metaAhorroService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<MetaAhorroResponseDTO> crearMeta(
            @Valid @RequestBody MetaAhorroCreacionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metaAhorroService.crearMeta(dto));
    }

    /**
     * Endpoint preferido (Implicit Context): Obtiene las metas del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<List<MetaAhorroResponseDTO>> obtenerMetasMe() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(metaAhorroService.obtenerMetasPorUsuario(rutAutenticado));
    }

    @PostMapping("/{metaId}/abonar")
    public ResponseEntity<MetaAhorroResponseDTO> abonarAMeta(
            @PathVariable Long metaId, @Valid @RequestBody AbonoRetiroDTO dto) {
        return ResponseEntity.ok(metaAhorroService.realizarAbono(metaId, dto));
    }

    @PostMapping("/{metaId}/retirar")
    public ResponseEntity<MetaAhorroResponseDTO> retirarDeMeta(
            @PathVariable Long metaId, @Valid @RequestBody AbonoRetiroDTO dto) {
        return ResponseEntity.ok(metaAhorroService.realizarRetiro(metaId, dto));
    }

    @PutMapping("/{metaId}")
    public ResponseEntity<MetaAhorroResponseDTO> editarMeta(
            @PathVariable Long metaId, @Valid @RequestBody MetaAhorroCreacionDTO dto) {
        return ResponseEntity.ok(metaAhorroService.editarMeta(metaId, dto));
    }

    @DeleteMapping("/{metaId}")
    public ResponseEntity<Void> eliminarMeta(@PathVariable Long metaId) {
        metaAhorroService.eliminarMeta(metaId);
        return ResponseEntity.noContent().build();
    }
}
