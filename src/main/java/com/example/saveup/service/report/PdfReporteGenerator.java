package com.example.saveup.service.report;

import com.example.saveup.model.Movimiento;
import com.example.saveup.model.enums.TipoMovimiento;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class PdfReporteGenerator implements ReporteGenerator {

    @Override
    public byte[] generarReporte(List<Movimiento> movimientos) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // --- Estilos ---
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.DARK_GRAY);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

            // --- Título ---
            Paragraph title = new Paragraph("Reporte de Movimientos - SaveUp", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("Generado el: " + java.time.LocalDate.now().toString(), subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20f);
            document.add(subtitle);

            // --- Tabla ---
            PdfPTable table = new PdfPTable(5); // 5 Columnas
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2f, 2f, 2.5f, 4f, 2.5f }); // Anchos relativos

            // Encabezados
            String[] headers = { "Fecha", "Tipo", "Categoría", "Descripción", "Monto" };
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new Color(51, 102, 153)); // Azul corporativo
                cell.setPadding(8f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Datos
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));

            boolean alternate = false;
            Color lightGray = new Color(245, 245, 245);

            for (Movimiento m : movimientos) {
                Color bgColor = alternate ? lightGray : Color.WHITE;
                alternate = !alternate;

                addCell(table, m.getFecha().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        .format(dateFormatter), dataFont, bgColor, Element.ALIGN_CENTER);

                String tipo = "Otro";
                boolean isIngreso = false;

                switch (m.getTipoMovimiento()) {
                    case INGRESO_GENERAL:
                        tipo = "Ingreso";
                        isIngreso = true;
                        break;
                    case GASTO_GENERAL:
                        tipo = "Gasto";
                        break;
                    case PAGO_DEUDA:
                        tipo = "Pago Deuda";
                        break;
                    case ABONO_META:
                        tipo = "Abono Ahorro";
                        break;
                    case RETIRO_META:
                        tipo = "Retiro Ahorro";
                        isIngreso = true;
                        break;
                }
                addCell(table, tipo, dataFont, bgColor, Element.ALIGN_CENTER);

                String cat = (m.getCategoria() != null) ? m.getCategoria().getNombre() : "-";
                addCell(table, cat, dataFont, bgColor, Element.ALIGN_LEFT);

                addCell(table, m.getDescripcion(), dataFont, bgColor, Element.ALIGN_LEFT);

                // Monto con color: Verde para Ingresos/RetirosAhorro, Rojo para Gastos/Abonos
                Color amountColor = isIngreso ? new Color(34, 139, 34) : new Color(220, 20, 60);

                PdfPCell amountCell = new PdfPCell(new Phrase(currencyFormat.format(Math.abs(m.getMonto())),
                        FontFactory.getFont(FontFactory.HELVETICA, 10, amountColor)));
                amountCell.setBackgroundColor(bgColor);
                amountCell.setPadding(6f);
                amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(amountCell);
            }

            document.add(table);

            // --- Resumen ---
            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Total de movimientos: " + movimientos.size(), subtitleFont);
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }

        return out.toByteArray();
    }

    private void addCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    @Override
    public String getContentType() {
        return "application/pdf";
    }

    @Override
    public String getFileExtension() {
        return ".pdf";
    }

    @Override
    public String getFormato() {
        return "PDF";
    }
}
