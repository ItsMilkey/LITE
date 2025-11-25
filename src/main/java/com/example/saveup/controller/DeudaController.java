package com.example.saveup.controller;

import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.service.DeudaService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deudas")
public class DeudaController {

    @Autowired
    private DeudaService deudaService;

    // Endpoint para crear una nueva deuda
    @PostMapping
    public ResponseEntity<?> crearDeuda(@Valid @RequestBody DeudaCreacionDTO dto) {
        try {
            DeudaResponseDTO nuevaDeuda = deudaService.crearDeuda(dto);
            return new ResponseEntity<>(nuevaDeuda, HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    // Endpoint para obtener todas las deudas de un usuario
    @GetMapping("/usuario/{rut}")
    public ResponseEntity<?> obtenerDeudasPorUsuario(@PathVariable String rut) {
        try {
            List<DeudaResponseDTO> deudas = deudaService.obtenerDeudasPorUsuario(rut);
            return ResponseEntity.ok(deudas);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
    
    // Endpoint para registrar el pago de una cuota
    @PostMapping("/{deudaId}/pagar")
    public ResponseEntity<?> registrarPago(@PathVariable Long deudaId, @Valid @RequestBody PagoDeudaDTO pagoDTO) {
        try {
            DeudaResponseDTO deudaActualizada = deudaService.registrarPago(deudaId, pagoDTO);
            return ResponseEntity.ok(deudaActualizada);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            // Para casos como intentar pagar una deuda ya pagada o cancelada
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }
    
    // Endpoint para editar una deuda (con restricciones de negocio)
    @PutMapping("/{deudaId}")
    public ResponseEntity<?> editarDeuda(@PathVariable Long deudaId, @Valid @RequestBody DeudaCreacionDTO dto) {
        try {
            DeudaResponseDTO deudaActualizada = deudaService.editarDeuda(deudaId, dto);
            return ResponseEntity.ok(deudaActualizada);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            // Si se intenta editar una deuda con pagos
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }

    // Endpoint para cancelar una deuda (soft delete)
    @PatchMapping("/{deudaId}/cancelar")
    public ResponseEntity<?> cancelarDeuda(@PathVariable Long deudaId) {
        try {
            DeudaResponseDTO deudaCancelada = deudaService.cancelarDeuda(deudaId);
            return ResponseEntity.ok(deudaCancelada);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }
}