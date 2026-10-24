package com.example.saveup.service;

import com.example.saveup.dto.ResultadoRecordatoriosDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparador diario de los recordatorios de cuota (T9.6), a las 09:00 de
 * America/Santiago (la zona del negocio, no la de la JVM).
 *
 * <p>No es la única vía: en Render free la instancia duerme y un
 * {@code @Scheduled} interno no corre de forma fiable. Por eso existe además
 * {@code POST /api/v1/notificaciones/recordatorios/ejecutar}, que un cron
 * externo puede llamar cada hora; el servicio es idempotente, así que ambos
 * caminos no se pisan.
 */
@Component
public class NotificacionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionScheduler.class);

    private final RecordatorioService recordatorioService;

    @Value("${app.push.habilitado:true}")
    private boolean habilitado;

    public NotificacionScheduler(RecordatorioService recordatorioService) {
        this.recordatorioService = recordatorioService;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
    public void recordatoriosDiarios() {
        if (!habilitado) {
            return;
        }
        try {
            ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar();
            LOG.info("Recordatorios diarios ejecutados: {}", resultado);
        } catch (RuntimeException e) {
            // Un fallo del job no debe tumbar la aplicación; el cron externo reintenta.
            LOG.error("Falló la corrida diaria de recordatorios", e);
        }
    }
}
