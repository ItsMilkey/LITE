package com.example.saveup.service.report;

import com.example.saveup.model.Movimiento;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class CsvReporteGenerator implements ReporteGenerator {

    private static final String CSV_SEPARATOR = ";";
    private static final String LINE_SEPARATOR = "\r\n";
    // BOM para UTF-8 para que Excel reconozca tildes automáticamente
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    @Override
    public byte[] generarReporte(List<Movimiento> movimientos) {
        StringBuilder csv = new StringBuilder();

        // Encabezados
        csv.append("Fecha").append(CSV_SEPARATOR)
                .append("Tipo").append(CSV_SEPARATOR)
                .append("Categoría").append(CSV_SEPARATOR)
                .append("Descripción").append(CSV_SEPARATOR)
                .append("Monto").append(LINE_SEPARATOR);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (Movimiento m : movimientos) {
            // Fecha
            csv.append(m.getFecha().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .format(dateFormatter))
                    .append(CSV_SEPARATOR);

            // Tipo (Ajustado a texto amigable)
            csv.append(m.getTipoMovimiento()).append(CSV_SEPARATOR);

            // Categoría
            String categoria = (m.getCategoria() != null) ? m.getCategoria().getNombre() : "Sin Categoría";
            csv.append(escapeSpecialCharacters(categoria)).append(CSV_SEPARATOR);

            // Descripción
            csv.append(escapeSpecialCharacters(m.getDescripcion())).append(CSV_SEPARATOR);

            // Monto (Sin formato de moneda para facilitar cálculos en Excel, solo entero)
            csv.append((int) m.getMonto()).append(LINE_SEPARATOR);
        }

        // Combinar BOM + Contenido
        byte[] contentBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];

        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);

        return result;
    }

    @Override
    public String getContentType() {
        return "text/csv";
    }

    @Override
    public String getFileExtension() {
        return ".csv";
    }

    @Override
    public String getFormato() {
        return "CSV";
    }

    // Escapar comillas dobles y puntos y comas si existen en el texto
    private String escapeSpecialCharacters(String data) {
        if (data == null)
            return "";
        String escapedData = data.replaceAll("\"", "\"\"");
        if (data.contains(CSV_SEPARATOR) || data.contains("\"") || data.contains("\n")) {
            data = "\"" + escapedData + "\"";
        }
        return data;
    }
}
