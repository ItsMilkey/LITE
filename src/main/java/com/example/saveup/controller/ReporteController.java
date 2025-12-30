package com.example.saveup.controller;

import com.example.saveup.service.report.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    @GetMapping("/movimientos/exportar")
    public ResponseEntity<byte[]> exportarMovimientos(
            @RequestParam("rut") String rut,
            @RequestParam(value = "alcance", defaultValue = "COMPLETO") String alcance,
            @RequestParam(value = "formato", defaultValue = "CSV") String formato,
            @RequestParam(value = "mes", required = false) Integer mes,
            @RequestParam(value = "anio", required = false) Integer anio) {
        ReporteService.ReporteGenerado reporte = reporteService.exportarMovimientos(rut, alcance, formato, mes, anio);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reporte.filename + "\"")
                .contentType(MediaType.parseMediaType(reporte.contentType))
                .body(reporte.content);
    }
}
