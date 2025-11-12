package com.example.saveup.controller;

import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.service.MovimientoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    @Autowired
    private MovimientoService movimientoService;

    /**
     * Endpoint para registrar un nuevo movimiento (ingreso o gasto).
     */
    @PostMapping
    public ResponseEntity<?> registrarMovimiento(@Valid @RequestBody MovimientoRegistroDTO dto) {
        try {
            MovimientoResponseDTO nuevoMovimiento = movimientoService.registrarMovimiento(dto);
            return new ResponseEntity<>(nuevoMovimiento, HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Ocurrió un error inesperado."), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Endpoint para obtener todos los movimientos de un usuario por su RUT.
     */
    @GetMapping("/usuario/{rut}")
    public ResponseEntity<?> obtenerMovimientosPorUsuario(@PathVariable String rut) {
        try {
            List<MovimientoResponseDTO> movimientos = movimientoService.obtenerMovimientosPorUsuario(rut);
            return ResponseEntity.ok(movimientos);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
}