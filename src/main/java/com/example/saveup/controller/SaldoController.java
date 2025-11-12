package com.example.saveup.controller;

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
    private MovimientoService movimientoService; // Reutilizamos el mismo servicio

    /**
     * Endpoint para obtener el saldo actual y total de un usuario por su RUT.
     */
    @GetMapping("/{rut}")
    public ResponseEntity<?> obtenerSaldoActual(@PathVariable String rut) {
        try {
            Double saldo = movimientoService.obtenerSaldoActual(rut);
            // Devolvemos un objeto JSON para mantener la consistencia en la API.
            return ResponseEntity.ok(Map.of("saldo", saldo));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
}