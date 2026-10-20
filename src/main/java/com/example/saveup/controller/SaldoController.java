package com.example.saveup.controller;

import com.example.saveup.dto.SaldoResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/saldos")
public class SaldoController {

    private final MovimientoService movimientoService;

    private final SecurityUtils securityUtils;

    public SaldoController(MovimientoService movimientoService, SecurityUtils securityUtils) {
        this.movimientoService = movimientoService;
        this.securityUtils = securityUtils;
    }

    /**
     * Obtiene el saldo del usuario autenticado en sesión a partir de su token JWT.
     * Soporta tanto /api/saldos/me como /api/saldos de forma implícita.
     */
    @GetMapping({"", "/me"})
    public ResponseEntity<SaldoResponseDTO> obtenerSaldoMe() {
        String rutAutenticado = securityUtils.getAuthenticatedRut();
        BigDecimal saldo = movimientoService.obtenerSaldoActual(rutAutenticado);
        return ResponseEntity.ok(new SaldoResponseDTO(saldo));
    }
}
