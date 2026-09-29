package com.example.saveup.controller;

import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/saldos")
public class SaldoController {

    @Autowired
    private MovimientoService movimientoService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Endpoint preferido (Implicit Context): Obtiene el saldo del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<?> obtenerSaldoMe() {
        try {
            String rutAutenticado = securityUtils.getAuthenticatedRut();
            Double saldo = movimientoService.obtenerSaldoActual(rutAutenticado);
            return ResponseEntity.ok(Map.of("saldo", saldo));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Endpoint con validación de propiedad (anti-IDOR): Comprueba que el {rut}
     * coincida con el usuario del token JWT.
     */
    @GetMapping("/{rut}")
    public ResponseEntity<?> obtenerSaldoActual(@PathVariable String rut) {
        try {
            securityUtils.validarPropietario(rut);
            Double saldo = movimientoService.obtenerSaldoActual(rut);
            return ResponseEntity.ok(Map.of("saldo", saldo));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
}