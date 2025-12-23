package com.example.saveup.controller;

import com.example.saveup.dto.AbonoRetiroDTO;import com.example.saveup.dto.MetaAhorroCreacionDTO;
import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.service.MetaAhorroService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metas")
public class MetaAhorroController {

    @Autowired
    private MetaAhorroService metaAhorroService;

    @PostMapping
    public ResponseEntity<?> crearMeta(@Valid @RequestBody MetaAhorroCreacionDTO dto) {
        try {
            MetaAhorroResponseDTO nuevaMeta = metaAhorroService.crearMeta(dto);
            return new ResponseEntity<>(nuevaMeta, HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/usuario/{rut}")
    public ResponseEntity<List<MetaAhorroResponseDTO>> obtenerMetas(@PathVariable String rut) {
        return ResponseEntity.ok(metaAhorroService.obtenerMetasPorUsuario(rut));
    }

    @PostMapping("/{metaId}/abonar")
    public ResponseEntity<?> abonarAMeta(@PathVariable Long metaId, @Valid @RequestBody AbonoRetiroDTO dto) {
        try {
            MetaAhorroResponseDTO metaActualizada = metaAhorroService.realizarAbono(metaId, dto);
            return ResponseEntity.ok(metaActualizada);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/{metaId}/retirar")
    public ResponseEntity<?> retirarDeMeta(@PathVariable Long metaId, @Valid @RequestBody AbonoRetiroDTO dto) {
        try {
            MetaAhorroResponseDTO metaActualizada = metaAhorroService.realizarRetiro(metaId, dto);
            return ResponseEntity.ok(metaActualizada);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }
    
    @PutMapping("/{metaId}")
    public ResponseEntity<?> editarMeta(@PathVariable Long metaId, @Valid @RequestBody MetaAhorroCreacionDTO dto) {
        try {
            MetaAhorroResponseDTO metaActualizada = metaAhorroService.editarMeta(metaId, dto);
            return ResponseEntity.ok(metaActualizada);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }

    @DeleteMapping("/{metaId}")
    public ResponseEntity<?> eliminarMeta(@PathVariable Long metaId) {
        try {
            metaAhorroService.eliminarMeta(metaId);
            return ResponseEntity.ok(Map.of("message", "Meta eliminada correctamente. Los fondos han sido devueltos a tu saldo principal."));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }
}