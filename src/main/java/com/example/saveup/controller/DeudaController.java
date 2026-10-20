package com.example.saveup.controller;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.dto.PagoDeudaResponseDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO.CuotaSimulacionDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.DeudaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deudas")
public class DeudaController {

    private final DeudaService deudaService;

    private final SecurityUtils securityUtils;

    public DeudaController(DeudaService deudaService, SecurityUtils securityUtils) {
        this.deudaService = deudaService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<DeudaResponseDTO> crearDeuda(@Valid @RequestBody DeudaCreacionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deudaService.crearDeuda(dto));
    }

    /**
     * Endpoint preferido (Implicit Context): Obtiene deudas del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<List<DeudaResponseDTO>> obtenerDeudasMe() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(deudaService.obtenerDeudasPorUsuario(rutAutenticado));
    }

    @PostMapping("/{deudaId}/pagar")
    public ResponseEntity<DeudaResponseDTO> registrarPago(@PathVariable Long deudaId,
                                                          @Valid @RequestBody PagoDeudaDTO pagoDTO) {
        return ResponseEntity.ok(deudaService.registrarPago(deudaId, pagoDTO).deuda());
    }

    @PutMapping("/{deudaId}")
    public ResponseEntity<DeudaResponseDTO> editarDeuda(@PathVariable Long deudaId,
                                                        @Valid @RequestBody DeudaCreacionDTO dto) {
        return ResponseEntity.ok(deudaService.editarDeuda(deudaId, dto));
    }

    @PatchMapping("/{deudaId}/cancelar")
    public ResponseEntity<DeudaResponseDTO> cancelarDeuda(@PathVariable Long deudaId) {
        return ResponseEntity.ok(deudaService.cancelarDeuda(deudaId));
    }

    @GetMapping("/{deudaId}/amortizacion")
    public ResponseEntity<List<CuotaSimulacionDTO>> obtenerAmortizacion(@PathVariable Long deudaId) {
        return ResponseEntity.ok(deudaService.obtenerAmortizacion(deudaId));
    }

    @GetMapping("/{deudaId}")
    public ResponseEntity<DeudaResponseDTO> obtenerDeuda(@PathVariable Long deudaId) {
        return ResponseEntity.ok(deudaService.obtenerDeuda(deudaId));
    }

    @GetMapping("/{deudaId}/pagos")
    public ResponseEntity<PageResponseDTO<PagoDeudaResponseDTO>> obtenerPagos(
            @PathVariable Long deudaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(deudaService.obtenerPagos(deudaId, page, size));
    }
}
