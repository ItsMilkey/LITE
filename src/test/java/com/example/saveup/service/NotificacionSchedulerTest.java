package com.example.saveup.service;

import com.example.saveup.dto.ResultadoRecordatoriosDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9.6 — El scheduler solo debe ser un disparador tonto: respeta el apagado por
 * configuración y no deja que un fallo del job tumbe la aplicación.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionSchedulerTest {

    @Mock
    private RecordatorioService recordatorioService;

    private NotificacionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificacionScheduler(recordatorioService);
    }

    @Test
    @DisplayName("Con app.push.habilitado=false no ejecuta el job")
    void deshabilitado_noEjecuta() {
        ReflectionTestUtils.setField(scheduler, "habilitado", false);

        scheduler.recordatoriosDiarios();

        verify(recordatorioService, never()).ejecutar();
    }

    @Test
    @DisplayName("Habilitado: delega en el servicio")
    void habilitado_ejecuta() {
        ReflectionTestUtils.setField(scheduler, "habilitado", true);
        when(recordatorioService.ejecutar()).thenReturn(new ResultadoRecordatoriosDTO(1, 1, 0));

        scheduler.recordatoriosDiarios();

        verify(recordatorioService).ejecutar();
    }

    @Test
    @DisplayName("Un fallo del job se registra pero no se propaga")
    void fallo_noPropaga() {
        ReflectionTestUtils.setField(scheduler, "habilitado", true);
        when(recordatorioService.ejecutar()).thenThrow(new IllegalStateException("FCM caído"));

        scheduler.recordatoriosDiarios();

        verify(recordatorioService).ejecutar();
    }
}
