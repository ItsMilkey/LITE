package com.example.saveup.controller;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.dto.PagoDeudaResponseDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO.CuotaSimulacionDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.DeudaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deudas")
@Tag(name = "Deudas", description = "Deudas, pagos, amortización e historial de pagos.")
@SecurityRequirement(name = "bearerAuth")
public class DeudaController {

    private final DeudaService deudaService;

    private final SecurityUtils securityUtils;

    public DeudaController(DeudaService deudaService, SecurityUtils securityUtils) {
        this.deudaService = deudaService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    @Operation(summary = "Registra una deuda del usuario autenticado", operationId = "crearDeuda")
    public ResponseEntity<DeudaResponseDTO> crearDeuda(@Valid @RequestBody DeudaCreacionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deudaService.crearDeuda(dto));
    }

    /**
     * Endpoint preferido (Implicit Context): Obtiene deudas del usuario autenticado.
     */
    @GetMapping("/me")
    @Operation(summary = "Lista las deudas del usuario autenticado", operationId = "obtenerDeudasMe")
    public ResponseEntity<List<DeudaResponseDTO>> obtenerDeudasMe() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(deudaService.obtenerDeudasPorUsuario(rutAutenticado));
    }

    @PostMapping("/{deudaId}/pagar")
    @Operation(summary = "Registra un pago sobre una deuda", operationId = "registrarPagoDeuda")
    public ResponseEntity<DeudaResponseDTO> registrarPago(@PathVariable Long deudaId,
                                                          @Valid @RequestBody PagoDeudaDTO pagoDTO) {
        return ResponseEntity.ok(deudaService.registrarPago(deudaId, pagoDTO).deuda());
    }

    @PutMapping("/{deudaId}")
    @Operation(summary = "Edita una deuda existente", operationId = "editarDeuda")
    public ResponseEntity<DeudaResponseDTO> editarDeuda(@PathVariable Long deudaId,
                                                        @Valid @RequestBody DeudaCreacionDTO dto) {
        return ResponseEntity.ok(deudaService.editarDeuda(deudaId, dto));
    }

    @PatchMapping("/{deudaId}/cancelar")
    @Operation(summary = "Cancela una deuda", operationId = "cancelarDeuda")
    public ResponseEntity<DeudaResponseDTO> cancelarDeuda(@PathVariable Long deudaId) {
        return ResponseEntity.ok(deudaService.cancelarDeuda(deudaId));
    }

    @GetMapping("/{deudaId}/amortizacion")
    @Operation(summary = "Obtiene la tabla de amortización de una deuda", operationId = "obtenerAmortizacionDeuda")
    public ResponseEntity<List<CuotaSimulacionDTO>> obtenerAmortizacion(@PathVariable Long deudaId) {
        return ResponseEntity.ok(deudaService.obtenerAmortizacion(deudaId));
    }

    @GetMapping("/{deudaId}")
    @Operation(summary = "Obtiene el detalle de una deuda", operationId = "obtenerDeuda")
    public ResponseEntity<DeudaResponseDTO> obtenerDeuda(@PathVariable Long deudaId) {
        return ResponseEntity.ok(deudaService.obtenerDeuda(deudaId));
    }

    @GetMapping("/{deudaId}/pagos")
    @Operation(summary = "Lista los pagos de una deuda", operationId = "obtenerPagosDeuda")
    public ResponseEntity<PageResponseDTO<PagoDeudaResponseDTO>> obtenerPagos(
            @PathVariable Long deudaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(deudaService.obtenerPagos(deudaId, page, size));
    }
}
