package com.example.saveup.controller;

import com.example.saveup.dto.SimulacionCreditoRequestDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO;
import com.example.saveup.service.SimulacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<SimulacionCreditoResponseDTO> simularCredito(
            @Valid @RequestBody SimulacionCreditoRequestDTO request) {
        return ResponseEntity.ok(simulacionService.simularCredito(request));
    }
}
