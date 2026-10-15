package com.example.saveup.controller;

import com.example.saveup.dto.ConfiguracionPresupuestoRequestDTO;
import com.example.saveup.dto.ConfiguracionPresupuestoResponseDTO;
import com.example.saveup.dto.PresupuestoCategoriaRequestDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.PresupuestoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Presupuesto del usuario (50/30/20, límites por categoría e historial). El
 * controlador solo enruta y traduce excepciones de negocio a códigos HTTP: toda
 * la lógica vive en {@link PresupuestoService}.
 */
@RestController
@RequestMapping("/api/presupuestos")
public class ConfiguracionPresupuestoController {

    private final PresupuestoService presupuestoService;

    private final SecurityUtils securityUtils;

    public ConfiguracionPresupuestoController(PresupuestoService presupuestoService,
                                              SecurityUtils securityUtils) {
        this.presupuestoService = presupuestoService;
        this.securityUtils = securityUtils;
    }

    /**
     * Endpoint preferido (Implicit Context): Obtiene la configuración del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<ConfiguracionPresupuestoResponseDTO> obtenerConfiguracionMe() {
        String rut = securityUtils.getAuthenticatedRut();
        return presupuestoService.obtenerConfiguracion(rut)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Endpoint preferido (Implicit Context): Guarda o actualiza configuración del usuario autenticado.
     * Los porcentajes deben sumar exactamente 100.00 y ninguno ser negativo; si no, 400.
     */
    @PostMapping("/me")
    public ResponseEntity<?> guardarConfiguracionMe(
            @Valid @RequestBody ConfiguracionPresupuestoRequestDTO request) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            return ResponseEntity.ok(presupuestoService.guardarConfiguracion(rut, request));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Endpoint preferido (Implicit Context): Ejecución presupuestaria del usuario autenticado.
     */
    @GetMapping("/ejecucion/me")
    public ResponseEntity<?> getEjecucionPresupuestoMe(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            return ResponseEntity.ok(presupuestoService.calcularEjecucionGeneral(rut, month, year));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /** Límites mensuales por categoría del usuario autenticado. */
    @GetMapping("/categorias/me")
    public ResponseEntity<?> obtenerCategoriasMe() {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.obtenerCategorias(rut));
    }

    /** Reemplaza el conjunto completo de límites. Categoría inexistente o límite ≤ 0 → 400. */
    @PutMapping("/categorias/me")
    public ResponseEntity<?> reemplazarCategoriasMe(
            @Valid @RequestBody PresupuestoCategoriaRequestDTO request) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            return ResponseEntity.ok(presupuestoService.reemplazarCategorias(rut, request.getCategorias()));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /** Ejecución (gastado vs. límite) por categoría en un mes. */
    @GetMapping("/categorias/ejecucion/me")
    public ResponseEntity<?> getEjecucionCategoriasMe(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            return ResponseEntity.ok(presupuestoService.obtenerEjecucionCategorias(rut, month, year));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /** Historial de la ejecución general, mes a mes. Rango máximo: 24 meses. */
    @GetMapping("/historial/me")
    public ResponseEntity<?> getHistorialMe(
            @RequestParam String desde,
            @RequestParam String hasta) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            return ResponseEntity.ok(presupuestoService.obtenerHistorial(rut, desde, hasta));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
}
