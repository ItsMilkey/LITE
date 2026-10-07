package com.example.saveup.controller;

import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/saldos")
public class SaldoController {

    @Autowired
    private MovimientoService movimientoService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Obtiene el saldo del usuario autenticado en sesión a partir de su token JWT.
     * Soporta tanto /api/saldos/me como /api/saldos de forma implícita.
     */
    @GetMapping({"", "/me"})
    public ResponseEntity<?> obtenerSaldoMe() {
        try {
            String rutAutenticado = securityUtils.getAuthenticatedRut();
            BigDecimal saldo = movimientoService.obtenerSaldoActual(rutAutenticado);
            return ResponseEntity.ok(Map.of("saldo", saldo));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
}