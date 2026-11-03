package com.example.saveup.controller;

import com.example.saveup.dto.CambiosMontosDTO;
import com.example.saveup.dto.IngresoReferenciaDTO;
import com.example.saveup.dto.PartidaCreacionDTO;
import com.example.saveup.dto.PartidaEdicionDTO;
import com.example.saveup.dto.PlanMensualResponseDTO;
import com.example.saveup.service.plan.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Plan del mes (T10c). El controlador solo enruta: la validación y el cálculo
 * viven en {@link PlanService} y {@code CalculadoraPlan}.
 */
@RestController
@RequestMapping("/api/v1/planes")
@Tag(name = "Planes", description = "Plan del mes, partidas, ingreso de referencia, montos y copia entre meses.")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /** Plan completo del mes; 200 con {@code existe: false} si todavía no existe. */
    @GetMapping("/{mes}")
    @Operation(summary = "Obtiene el plan de un mes", operationId = "obtenerPlanMensual")
    public ResponseEntity<PlanMensualResponseDTO> obtener(@PathVariable String mes) {
        return ResponseEntity.ok(planService.obtener(mes));
    }

    /** Define, cambia o limpia el ingreso de referencia del mes. */
    @PutMapping("/{mes}/ingreso-referencia")
    @Operation(summary = "Actualiza el ingreso de referencia", operationId = "actualizarIngresoReferenciaPlan")
    public ResponseEntity<PlanMensualResponseDTO> actualizarIngreso(
            @PathVariable String mes, @Valid @RequestBody IngresoReferenciaDTO dto) {
        return ResponseEntity.ok(planService.actualizarIngreso(mes, dto));
    }

    /** Crea una partida y devuelve el plan completo. */
    @PostMapping("/{mes}/partidas")
    @Operation(summary = "Crea una partida del plan", operationId = "crearPartidaPlan")
    public ResponseEntity<PlanMensualResponseDTO> crearPartida(
            @PathVariable String mes, @Valid @RequestBody PartidaCreacionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planService.crearPartida(mes, dto));
    }

    /** Cambia el nombre y/o la categoría de una partida. */
    @PutMapping("/{mes}/partidas/{id}")
    @Operation(summary = "Edita una partida del plan", operationId = "editarPartidaPlan")
    public ResponseEntity<PlanMensualResponseDTO> editarPartida(
            @PathVariable String mes, @PathVariable Long id,
            @Valid @RequestBody PartidaEdicionDTO dto) {
        return ResponseEntity.ok(planService.editarPartida(mes, id, dto));
    }

    /** Aplica un lote atómico de montos absolutos (1 a 100 cambios). */
    @PatchMapping("/{mes}/montos")
    @Operation(summary = "Actualiza montos planificados por lote", operationId = "actualizarMontosPlan")
    public ResponseEntity<PlanMensualResponseDTO> actualizarMontos(
            @PathVariable String mes, @Valid @RequestBody CambiosMontosDTO dto) {
        return ResponseEntity.ok(planService.actualizarMontos(mes, dto));
    }

    /** Borra una partida; sus movimientos quedan sin partida. */
    @DeleteMapping("/{mes}/partidas/{id}")
    @Operation(summary = "Elimina una partida del plan", operationId = "eliminarPartidaPlan")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> eliminarPartida(@PathVariable String mes, @PathVariable Long id) {
        planService.eliminarPartida(mes, id);
        return ResponseEntity.noContent().build();
    }

    /** Copia las partidas (y el ingreso si falta) desde otro mes. */
    @PostMapping("/{mes}/copiar-desde/{mesOrigen}")
    @Operation(summary = "Copia el plan de otro mes", operationId = "copiarPlanMensual")
    public ResponseEntity<PlanMensualResponseDTO> copiar(
            @PathVariable String mes, @PathVariable String mesOrigen) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planService.copiar(mes, mesOrigen));
    }
}
