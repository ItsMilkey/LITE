package com.example.saveup.service.report;

import com.example.saveup.model.Movimiento;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.service.movimiento.FechasMovimiento;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReporteService {

    private final MovimientoRepository movimientoRepository;
    private final Map<String, ReporteGenerator> strategies;

    public ReporteService(MovimientoRepository movimientoRepository, List<ReporteGenerator> generators) {
        this.movimientoRepository = movimientoRepository;
        // Mapa: "CSV" -> CsvReporteGenerator
        this.strategies = generators.stream()
                .collect(Collectors.toMap(ReporteGenerator::getFormato, g -> g));
    }

    public ReporteGenerado exportarMovimientos(String rut, String alcance, String formato, Integer mes, Integer anio) {

        // 1. Obtener Datos
        List<Movimiento> movimientos;
        if ("MENSUAL".equalsIgnoreCase(alcance) && mes != null && anio != null) {
            // Ventana semiabierta [inicio del mes, inicio del mes siguiente) en la
            // zona del negocio: el último día entra completo sin importar la hora.
            YearMonth mesReporte = YearMonth.of(anio, mes);
            movimientos = movimientoRepository.findByUsuarioRutAndFechaGreaterThanEqualAndFechaLessThan(
                    rut, FechasMovimiento.inicioDeMes(mesReporte), FechasMovimiento.inicioDeMesSiguiente(mesReporte));
        } else {
            // COMPLETO
            movimientos = movimientoRepository.findByUsuarioRutOrderByFechaDesc(rut);
        }

        // 2. Seleccionar Estrategia
        ReporteGenerator generator = strategies.get(formato.toUpperCase());
        if (generator == null) {
            // Fallback a CSV si no encuentra
            generator = strategies.get("CSV");
        }
        if (generator == null)
            throw new IllegalArgumentException("Formato no soportado: " + formato);

        // 3. Generar
        byte[] content = generator.generarReporte(movimientos);

        // 4. Wrap result
        String filename = "reporte_saveup_" + (alcance.equalsIgnoreCase("MENSUAL") ? mes + "-" + anio : "completo")
                + generator.getFileExtension();

        return new ReporteGenerado(filename, generator.getContentType(), content);
    }

    // Inner DTO helper
    public static class ReporteGenerado {
        public String filename;
        public String contentType;
        public byte[] content;

        public ReporteGenerado(String f, String c, byte[] b) {
            this.filename = f;
            this.contentType = c;
            this.content = b;
        }
    }
}
