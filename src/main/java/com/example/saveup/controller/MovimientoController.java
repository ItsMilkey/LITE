package com.example.saveup.controller;

import com.example.saveup.dto.MovimientoEdicionDTO;
import com.example.saveup.dto.MovimientoFiltroDTO;
import com.example.saveup.dto.MovimientoRegistroDTO;
import com.example.saveup.dto.MovimientoResponseDTO;
import com.example.saveup.dto.PageResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.MovimientoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<MovimientoResponseDTO> registrarMovimiento(
            @Valid @RequestBody MovimientoRegistroDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarMovimiento(dto));
    }

    /**
     * Endpoint basado estrictamente en el contexto del usuario autenticado en la sesión JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<List<MovimientoResponseDTO>> obtenerMovimientosMe(
            @RequestParam(required = false) Integer limit) {
        String rut = securityUtils.getAuthenticatedRut();
        return ResponseEntity.ok(movimientoService.obtenerMovimientosPorUsuario(rut, limit));
    }

    /**
     * Endpoint paginado para el usuario autenticado en sesión, con filtros
     * opcionales (T4). Sin filtros se comporta como antes (fecha desc).
     */
    @GetMapping("/paginados/me")
    public ResponseEntity<PageResponseDTO<MovimientoResponseDTO>> obtenerMovimientosPaginadosMe(
            @ModelAttribute MovimientoFiltroDTO filtro,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String rut = securityUtils.getAuthenticatedRut();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fecha"));
        return ResponseEntity.ok(movimientoService.obtenerMovimientosPaginados(rut, pageable, filtro));
    }

    /**
     * Edita descripción, monto y categoría de un movimiento propio (T4). La fecha
     * no es editable. Tipos no permitidos o ingreso con Smart-Split → 409.
     */
    @PutMapping("/{id}")
    public ResponseEntity<MovimientoResponseDTO> editarMovimiento(
            @PathVariable Long id, @Valid @RequestBody MovimientoEdicionDTO dto) {
        return ResponseEntity.ok(movimientoService.editarMovimiento(id, dto));
    }

    /**
     * Elimina un movimiento propio (T4) con las mismas reglas que la edición.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMovimiento(@PathVariable Long id) {
        movimientoService.eliminarMovimiento(id);
        return ResponseEntity.noContent().build();
    }
}
