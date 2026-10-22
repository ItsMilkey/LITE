package com.example.saveup.controller;

import com.example.saveup.dto.ConfiguracionPresupuestoRequestDTO;
import com.example.saveup.dto.ConfiguracionPresupuestoResponseDTO;
import com.example.saveup.dto.EjecucionCategoriaDTO;
import com.example.saveup.dto.EjecucionPresupuestoDTO;
import com.example.saveup.dto.HistorialPresupuestoDTO;
import com.example.saveup.dto.PresupuestoCategoriaDTO;
import com.example.saveup.dto.PresupuestoCategoriaRequestDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.PresupuestoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Presupuesto del usuario (50/30/20, límites por categoría e historial). El
 * controlador solo enruta: toda la lógica vive en {@link PresupuestoService}.
 */
@RestController
@RequestMapping("/api/v1/presupuestos")
@Tag(name = "Presupuestos", description = "Configuración 50/30/20, límites por categoría, ejecución e historial.")
@SecurityRequirement(name = "bearerAuth")
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
     * Sin configuración responde 404 NO_ENCONTRADO en problem+json.
     */
    @GetMapping("/me")
    @Operation(summary = "Obtiene la configuración de presupuesto", operationId = "obtenerConfiguracionPresupuesto")
    public ResponseEntity<ConfiguracionPresupuestoResponseDTO> obtenerConfiguracionMe() {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.obtenerConfiguracion(rut));
    }

    /**
     * Endpoint preferido (Implicit Context): Guarda o actualiza configuración del usuario autenticado.
     * Los porcentajes deben sumar exactamente 100.00 y ninguno ser negativo; si no, 400.
     */
    @PostMapping("/me")
    @Operation(summary = "Guarda la configuración de presupuesto", operationId = "guardarConfiguracionPresupuesto")
    public ResponseEntity<ConfiguracionPresupuestoResponseDTO> guardarConfiguracionMe(
            @Valid @RequestBody ConfiguracionPresupuestoRequestDTO request) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.guardarConfiguracion(rut, request));
    }

    /**
     * Endpoint preferido (Implicit Context): Ejecución presupuestaria del usuario autenticado.
     */
    @GetMapping("/ejecucion/me")
    @Operation(summary = "Obtiene la ejecución presupuestaria del mes", operationId = "obtenerEjecucionPresupuesto")
    public ResponseEntity<EjecucionPresupuestoDTO> getEjecucionPresupuestoMe(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.calcularEjecucionGeneral(rut, month, year));
    }

    /** Límites mensuales por categoría del usuario autenticado. */
    @GetMapping("/categorias/me")
    @Operation(summary = "Lista los límites mensuales por categoría", operationId = "obtenerCategoriasPresupuesto")
    public ResponseEntity<List<PresupuestoCategoriaDTO>> obtenerCategoriasMe() {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.obtenerCategorias(rut));
    }

    /** Reemplaza el conjunto completo de límites. Categoría inexistente o límite ≤ 0 → 400. */
    @PutMapping("/categorias/me")
    @Operation(summary = "Reemplaza los límites mensuales por categoría", operationId = "reemplazarCategoriasPresupuesto")
    public ResponseEntity<List<PresupuestoCategoriaDTO>> reemplazarCategoriasMe(
            @Valid @RequestBody PresupuestoCategoriaRequestDTO request) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.reemplazarCategorias(rut, request.getCategorias()));
    }

    /** Ejecución (gastado vs. límite) por categoría en un mes. */
    @GetMapping("/categorias/ejecucion/me")
    @Operation(summary = "Obtiene la ejecución por categoría del mes", operationId = "obtenerEjecucionCategorias")
    public ResponseEntity<List<EjecucionCategoriaDTO>> getEjecucionCategoriasMe(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.obtenerEjecucionCategorias(rut, month, year));
    }

    /** Historial de la ejecución general, mes a mes. Rango máximo: 24 meses. */
    @GetMapping("/historial/me")
    @Operation(summary = "Obtiene el historial mensual de ejecución", operationId = "obtenerHistorialPresupuesto")
    public ResponseEntity<List<HistorialPresupuestoDTO>> getHistorialMe(
            @RequestParam String desde,
            @RequestParam String hasta) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(presupuestoService.obtenerHistorial(rut, desde, hasta));
    }
}
