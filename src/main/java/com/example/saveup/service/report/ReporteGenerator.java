package com.example.saveup.service.report;

import com.example.saveup.model.Movimiento;
import java.util.List;

public interface ReporteGenerator {
    byte[] generarReporte(List<Movimiento> movimientos);

    String getContentType();

    String getFileExtension();

    String getFormato(); // "CSV", "PDF", etc.
}
