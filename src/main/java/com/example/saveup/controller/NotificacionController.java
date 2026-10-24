package com.example.saveup.controller;

import com.example.saveup.dto.DispositivoPushBajaDTO;
import com.example.saveup.dto.DispositivoPushRequestDTO;
import com.example.saveup.dto.DispositivoPushResponseDTO;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.DispositivoPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Registro de dispositivos para notificaciones push (T9.6). La app lo llama tras
 * aceptar el permiso del sistema operativo y lo deshace en el logout.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@Tag(name = "Notificaciones", description = "Dispositivos para recordatorios push de vencimiento.")
@SecurityRequirement(name = "bearerAuth")
public class NotificacionController {

    private final DispositivoPushService dispositivoPushService;

    private final SecurityUtils securityUtils;

    public NotificacionController(DispositivoPushService dispositivoPushService,
                                  SecurityUtils securityUtils) {
        this.dispositivoPushService = dispositivoPushService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/dispositivos")
    @Operation(summary = "Registra o refresca el dispositivo del usuario autenticado",
            operationId = "registrarDispositivo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dispositivo registrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DispositivoPushResponseDTO.class))),
            @ApiResponse(responseCode = "200", description = "El dispositivo ya existía y se refrescó",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DispositivoPushResponseDTO.class)))
    })
    public ResponseEntity<DispositivoPushResponseDTO> registrar(@Valid @RequestBody DispositivoPushRequestDTO dto) {
        DispositivoPushService.Registro registro =
                dispositivoPushService.registrar(securityUtils.getAuthenticatedRut(), dto);
        return registro.creado()
                ? ResponseEntity.status(HttpStatus.CREATED).body(registro.dispositivo())
                : ResponseEntity.ok(registro.dispositivo());
    }

    @DeleteMapping("/dispositivos")
    @Operation(summary = "Deja de notificar a un dispositivo (idempotente)",
            operationId = "eliminarDispositivo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Dispositivo eliminado (idempotente)")
    })
    public ResponseEntity<Void> eliminar(@Valid @RequestBody DispositivoPushBajaDTO dto) {
        dispositivoPushService.baja(securityUtils.getAuthenticatedRut(), dto.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dispositivos")
    @Operation(summary = "Lista los dispositivos activos del usuario autenticado",
            operationId = "listarDispositivos")
    public ResponseEntity<List<DispositivoPushResponseDTO>> listar() {
        return ResponseEntity.ok(dispositivoPushService.listar(securityUtils.getAuthenticatedRut()));
    }
}
