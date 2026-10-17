package com.example.saveup.controller;

import com.example.saveup.dto.MovimientoEdicionDTO;
import com.example.saveup.dto.MovimientoFiltroDTO;
import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final MovimientoService movimientoService;

    private final SecurityUtils securityUtils;

    public MovimientoController(MovimientoService movimientoService, SecurityUtils securityUtils) {
        this.movimientoService = movimientoService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<?> registrarMovimiento(@Valid @RequestBody MovimientoRegistroDTO dto) {
        try {
            MovimientoResponseDTO nuevoMovimiento = movimientoService.registrarMovimiento(dto);
            return new ResponseEntity<>(nuevoMovimiento, HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            // Regla de negocio violada (p. ej. sobrepago o deuda cerrada): mismo 409 que /api/deudas/{id}/pagar.
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Ocurrió un error inesperado."), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Endpoint basado estrictamente en el contexto del usuario autenticado en la sesión JWT.
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
     * Endpoint paginado para el usuario autenticado en sesión, con filtros
     * opcionales (T4). Sin filtros se comporta como antes (fecha desc).
     */
    @GetMapping("/paginados/me")
    public ResponseEntity<?> obtenerMovimientosPaginadosMe(
            @ModelAttribute MovimientoFiltroDTO filtro,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            String rut = securityUtils.getAuthenticatedRut();
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fecha"));
            PageResponseDTO<MovimientoResponseDTO> movimientos =
                    movimientoService.obtenerMovimientosPaginados(rut, pageable, filtro);
            return ResponseEntity.ok(movimientos);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Edita descripción, monto y categoría de un movimiento propio (T4). La fecha
     * no es editable. Tipos no permitidos o ingreso con Smart-Split → 409.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> editarMovimiento(@PathVariable Long id, @Valid @RequestBody MovimientoEdicionDTO dto) {
        try {
            MovimientoResponseDTO movimiento = movimientoService.editarMovimiento(id, dto);
            return ResponseEntity.ok(movimiento);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Elimina un movimiento propio (T4) con las mismas reglas que la edición.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarMovimiento(@PathVariable Long id) {
        try {
            movimientoService.eliminarMovimiento(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
        }
    }
}