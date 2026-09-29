package com.example.saveup.controller;

import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<?> registrarMovimiento(@Valid @RequestBody MovimientoRegistroDTO dto) {
        try {
            String rutAutenticado = securityUtils.getAuthenticatedRut();
            if (dto.getUsuarioRut() != null && !dto.getUsuarioRut().isBlank()) {
                securityUtils.validarPropietario(dto.getUsuarioRut());
            } else {
                dto.setUsuarioRut(rutAutenticado);
            }

            MovimientoResponseDTO nuevoMovimiento = movimientoService.registrarMovimiento(dto);
            return new ResponseEntity<>(nuevoMovimiento, HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Ocurrió un error inesperado."), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Endpoint preferido (Implicit Context): Obtiene movimientos del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<?> obtenerMovimientosMe(@RequestParam(required = false) Integer limit) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            List<MovimientoResponseDTO> movimientos = movimientoService.obtenerMovimientosPorUsuario(rut, limit);
            return ResponseEntity.ok(movimientos);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Endpoint preferido paginado (Implicit Context).
     */
    @GetMapping("/paginados/me")
    public ResponseEntity<?> obtenerMovimientosPaginadosMe(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            Pageable pageable = PageRequest.of(page, size);
            PageResponseDTO<MovimientoResponseDTO> movimientos = movimientoService.obtenerMovimientosPaginados(rut, pageable);
            return ResponseEntity.ok(movimientos);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Endpoint retrocompatible con validación anti-IDOR.
     */
    @GetMapping("/usuario/{rut}")
    public ResponseEntity<?> obtenerMovimientosPorUsuario(
            @PathVariable String rut,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            securityUtils.validarPropietario(rut);
            List<MovimientoResponseDTO> movimientos = movimientoService.obtenerMovimientosPorUsuario(rut, limit);
            return ResponseEntity.ok(movimientos);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Endpoint retrocompatible paginado con validación anti-IDOR.
     */
    @GetMapping("/paginados/usuario/{rut}")
    public ResponseEntity<?> obtenerMovimientosPaginados(
            @PathVariable String rut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            securityUtils.validarPropietario(rut);
            Pageable pageable = PageRequest.of(page, size);
            PageResponseDTO<MovimientoResponseDTO> movimientos = movimientoService.obtenerMovimientosPaginados(rut, pageable);
            return ResponseEntity.ok(movimientos);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }
}