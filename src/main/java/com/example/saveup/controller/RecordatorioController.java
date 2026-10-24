package com.example.saveup.controller;

import com.example.saveup.dto.ResultadoRecordatoriosDTO;
import com.example.saveup.service.RecordatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Disparador externo del job de recordatorios (T9.6). No usa JWT: lo llama un
 * cron (cron-job.org o un Render Cron Job), no una persona. La credencial es
 * {@code X-Cron-Secret} comparada en tiempo constante contra
 * {@code app.push.cron-secret}.
 *
 * <p>Si el secreto no está configurado el endpoint queda cerrado: es preferible
 * un job que no corre a un endpoint abierto que cualquiera puede invocar.
 */
@RestController
@RequestMapping("/api/v1/notificaciones/recordatorios")
@Tag(name = "Notificaciones", description = "Dispositivos para recordatorios push de vencimiento.")
public class RecordatorioController {

    private static final String CABECERA_SECRETO = "X-Cron-Secret";

    private final RecordatorioService recordatorioService;

    @Value("${app.push.cron-secret:}")
    private String cronSecret;

    public RecordatorioController(RecordatorioService recordatorioService) {
        this.recordatorioService = recordatorioService;
    }

    @PostMapping("/ejecutar")
    @Operation(summary = "Ejecuta la corrida de recordatorios (cron externo)",
            operationId = "ejecutarRecordatorios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Corrida aceptada y ejecutada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResultadoRecordatoriosDTO.class)))
    })
    public ResponseEntity<ResultadoRecordatoriosDTO> ejecutar(
            @RequestHeader(value = CABECERA_SECRETO, required = false) String secreto) {
        if (cronSecret == null || cronSecret.isBlank() || !coincide(secreto, cronSecret)) {
            throw new AccessDeniedException("Secreto de cron inválido.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recordatorioService.ejecutar());
    }

    /** Comparación en tiempo constante: no filtra el secreto por longitud del prefijo. */
    private boolean coincide(String recibido, String esperado) {
        if (recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                recibido.getBytes(StandardCharsets.UTF_8),
                esperado.getBytes(StandardCharsets.UTF_8));
    }
}
