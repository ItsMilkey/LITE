package com.example.saveup.service.report;

import com.example.saveup.model.Movimiento;
import com.example.saveup.repository.MovimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReporteService {

    @Autowired
    private MovimientoRepository movimientoRepository;

    private final Map<String, ReporteGenerator> strategies;

    @Autowired
    public ReporteService(List<ReporteGenerator> generators) {
        // Mapa: "CSV" -> CsvReporteGenerator
        this.strategies = generators.stream()
                .collect(Collectors.toMap(ReporteGenerator::getFormato, g -> g));
    }

    public ReporteGenerado exportarMovimientos(String rut, String alcance, String formato, Integer mes, Integer anio) {

        // 1. Obtener Datos
        List<Movimiento> movimientos;
        if ("MENSUAL".equalsIgnoreCase(alcance) && mes != null && anio != null) {
            LocalDate startLocal = LocalDate.of(anio, mes, 1);
            LocalDate endLocal = startLocal.plusMonths(1); // Inicio del próximo mes (exclusivo en lógica, inclusivo si
                                                           // usamos between depende de JPA)
            // Ajustamos check: Between suele ser inclusivo-inclusivo. Restamos 1 segundo o
            // día.
            // Para seguridad, usaremos primer dia del mes a las 00:00 y ultimo dia a las
            // 23:59

            Date startDate = Date.from(startLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());
            // Fin del mes: Primer dia del OTRO mes menos 1 segundo? O simplemente usamos
            // dia 31?
            // Mejor: startLocal.plusMonths(1).minusDays(1) -> atTime(Max)
            Date endDate = Date
                    .from(startLocal.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1).toInstant());

            movimientos = movimientoRepository.findByUsuarioRutAndFechaBetween(rut, startDate, endDate);
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
