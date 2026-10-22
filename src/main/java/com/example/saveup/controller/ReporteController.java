package com.example.saveup.controller;

import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.report.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes", description = "Exportación de movimientos a CSV o PDF.")
@SecurityRequirement(name = "bearerAuth")
public class ReporteController {

    private final ReporteService reporteService;
    private final SecurityUtils securityUtils;

    public ReporteController(ReporteService reporteService, SecurityUtils securityUtils) {
        this.reporteService = reporteService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/movimientos/exportar")
    @Operation(summary = "Exporta los movimientos a CSV o PDF", operationId = "exportarMovimientos")
    public ResponseEntity<byte[]> exportarMovimientos(
            @RequestParam(value = "alcance", defaultValue = "COMPLETO") String alcance,
            @RequestParam(value = "formato", defaultValue = "CSV") String formato,
            @RequestParam(value = "mes", required = false) Integer mes,
            @RequestParam(value = "anio", required = false) Integer anio) {

        String targetRut = securityUtils.getAuthenticatedRut();

        ReporteService.ReporteGenerado reporte = reporteService.exportarMovimientos(targetRut, alcance, formato, mes, anio);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reporte.filename + "\"")
                .contentType(MediaType.parseMediaType(reporte.contentType))
                .body(reporte.content);
    }
}
