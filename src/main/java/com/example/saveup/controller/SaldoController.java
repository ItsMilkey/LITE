package com.example.saveup.controller;

import com.example.saveup.dto.SaldoResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/saldos")
@Tag(name = "Saldos", description = "Saldo actual del usuario autenticado.")
@SecurityRequirement(name = "bearerAuth")
public class SaldoController {

    private final MovimientoService movimientoService;

    private final SecurityUtils securityUtils;

    public SaldoController(MovimientoService movimientoService, SecurityUtils securityUtils) {
        this.movimientoService = movimientoService;
        this.securityUtils = securityUtils;
    }

    /**
     * Obtiene el saldo del usuario autenticado. Se mantienen dos rutas por
     * compatibilidad, con operationId distinto para que el cliente generado no
     * tenga métodos duplicados.
     */
    @GetMapping("")
    @Operation(summary = "Obtiene el saldo actual", operationId = "obtenerSaldo")
    public ResponseEntity<SaldoResponseDTO> obtenerSaldo() {
        return obtenerSaldoMe();
    }

    @GetMapping("/me")
    @Operation(summary = "Obtiene el saldo del usuario autenticado", operationId = "obtenerSaldoMe")
    public ResponseEntity<SaldoResponseDTO> obtenerSaldoMe() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        BigDecimal saldo = movimientoService.obtenerSaldoActual(rutAutenticado);
        return ResponseEntity.ok(new SaldoResponseDTO(saldo));
    }
}
