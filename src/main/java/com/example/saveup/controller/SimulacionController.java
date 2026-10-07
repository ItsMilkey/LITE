package com.example.saveup.controller;

import com.example.saveup.dto.SimulacionCreditoRequestDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO;
import com.example.saveup.service.SimulacionService;
import com.example.saveup.service.finanzas.CalculoFinancieroException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador REST para el simulador de crédito (sin persistencia).
 */
@RestController
@RequestMapping("/api/simulaciones")
public class SimulacionController {

    private final SimulacionService simulacionService;

    public SimulacionController(SimulacionService simulacionService) {
        this.simulacionService = simulacionService;
    }

    @PostMapping("/credito")
    public ResponseEntity<?> simularCredito(@Valid @RequestBody SimulacionCreditoRequestDTO request) {
        try {
            SimulacionCreditoResponseDTO respuesta = simulacionService.simularCredito(request);
            return ResponseEntity.ok(respuesta);
        } catch (CalculoFinancieroException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
