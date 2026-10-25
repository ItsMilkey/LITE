package com.example.saveup.service;

import com.example.saveup.dto.ResultadoRecordatoriosDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.DispositivoPush;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.Plataforma;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoRecordatorio;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.DispositivoPushRepository;
import com.example.saveup.repository.RecordatorioEnviadoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.service.movimiento.FechasMovimiento;
import com.example.saveup.service.push.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9.6 — Reglas del job de recordatorios contra H2 real. El {@link PushSender}
 * se mockea: los tests no hablan con FCM ni dependen del log.
 */
@SpringBootTest
@Transactional
class RecordatorioServiceTest {

    private static final String RUT = "12345678-9";
    private static final String TOKEN = "token-de-prueba-1234567890";

    /** Instante dentro de la ventana permitida ([08:00, 22:00)) para las corridas. */
    private static final ZonedDateTime VENTANA =
            ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, FechasMovimiento.zona());

    @Autowired
    private RecordatorioService recordatorioService;

    @Autowired
    private RecordatorioEnviadoRepository recordatorioRepository;

    @Autowired
    private DispositivoPushRepository dispositivoRepository;

    @Autowired
    private DeudaRepository deudaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockBean
    private PushSender pushSender;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(nuevoUsuario());

        DispositivoPush dispositivo = new DispositivoPush();
        dispositivo.setUsuario(usuario);
        dispositivo.setToken(TOKEN);
        dispositivo.setPlataforma(Plataforma.ANDROID);
        dispositivo.setActivo(true);
        dispositivo.setFechaRegistro(new Date());
        dispositivo.setFechaUltimoUso(new Date());
        dispositivoRepository.save(dispositivo);

        when(pushSender.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(PushSender.ResultadoEnvio.ok());
    }

    @Test
    @DisplayName("Cuota a 3 días: envía PROXIMA_VENCER y registra el aviso")
    void proximaVencer_enviaYRegistra() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(1, resultado.evaluados());
        assertEquals(1, resultado.enviados());
        assertEquals(0, resultado.omitidos());
        assertEquals(1, recordatorioRepository.count());

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(pushSender).enviar(eq(TOKEN), eq("SaveUp Lite"), cuerpo.capture(), anyMap());
        assertTrue(cuerpo.getValue().contains("Cuota 1 de «Notebook» vence en 3 días"));
    }

    @Test
    @DisplayName("Cuota que vence hoy: envía VENCE_HOY")
    void venceHoy_envia() {
        deuda(FechasMovimiento.hoy(), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(1, resultado.enviados());
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(pushSender).enviar(eq(TOKEN), anyString(), cuerpo.capture(), anyMap());
        assertTrue(cuerpo.getValue().contains("vence hoy"));
    }

    @Test
    @DisplayName("Cuota vencida ayer: envía VENCIDA")
    void vencidaAyer_envia() {
        deuda(FechasMovimiento.hoy().minusDays(1), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(1, resultado.enviados());
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(pushSender).enviar(eq(TOKEN), anyString(), cuerpo.capture(), anyMap());
        assertTrue(cuerpo.getValue().contains("venció ayer"));
    }

    @Test
    @DisplayName("Fuera de las ventanas (T-10) no envía nada")
    void fueraDeVentana_noEnvia() {
        deuda(FechasMovimiento.hoy().plusDays(10), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(1, resultado.evaluados());
        assertEquals(0, resultado.enviados());
        verify(pushSender, times(0)).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Dos corridas el mismo día no duplican la notificación")
    void segundaCorrida_noDuplica() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        recordatorioService.ejecutar(VENTANA);
        ResultadoRecordatoriosDTO segunda = recordatorioService.ejecutar(VENTANA);

        assertEquals(0, segunda.enviados());
        assertEquals(1, segunda.omitidos());
        assertEquals(1, recordatorioRepository.count());
        verify(pushSender, times(1)).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Varias cuotas del mismo usuario se agrupan en una sola push")
    void variasDeudas_unaSolaPush() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");
        deuda(FechasMovimiento.hoy().plusDays(3), "Auto");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(2, resultado.evaluados());
        assertEquals(2, resultado.enviados());
        verify(pushSender, times(1)).enviar(anyString(), anyString(), anyString(), anyMap());

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(pushSender).enviar(eq(TOKEN), anyString(), cuerpo.capture(), anyMap());
        assertEquals("Tienes 2 cuotas por vencer", cuerpo.getValue());
        assertEquals(2, recordatorioRepository.count());
    }

    @Test
    @DisplayName("El payload incluye el deep link a la deuda")
    void payloadIncluyeDeepLink() {
        Deuda deuda = deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        recordatorioService.ejecutar(VENTANA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(pushSender).enviar(eq(TOKEN), anyString(), anyString(), datos.capture());
        assertEquals("RECORDATORIO_CUOTA", datos.getValue().get("tipo"));
        assertEquals(String.valueOf(deuda.getId()), datos.getValue().get("deudaId"));
        assertEquals("saveup://deudas/" + deuda.getId(), datos.getValue().get("deepLink"));
    }

    @Test
    @DisplayName("Deuda pagada no genera aviso")
    void deudaPagada_noAvisa() {
        Deuda deuda = deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");
        deuda.setEstado(EstadoDeuda.PAGADA);
        deudaRepository.save(deuda);

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(0, resultado.enviados());
        verify(pushSender, times(0)).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Token rechazado por FCM: desactiva el dispositivo y no registra el aviso")
    void tokenMuerto_desactivaDispositivo() {
        when(pushSender.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(PushSender.ResultadoEnvio.tokenMuerto());
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(0, resultado.enviados());
        assertEquals(1, resultado.omitidos());
        assertEquals(0, recordatorioRepository.count());
        assertFalse(dispositivoRepository.findByToken(TOKEN).orElseThrow().estaActivo());
    }

    @Test
    @DisplayName("Sin dispositivos activos no se evalúa ninguna deuda")
    void sinDispositivos_noEvalua() {
        dispositivoRepository.findByToken(TOKEN).orElseThrow().setActivo(false);
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(0, resultado.evaluados());
        assertEquals(0, resultado.enviados());
    }

    @Test
    @DisplayName("tipoDeAviso: solo T-3, T-0 y T+1 tienen aviso")
    void tipoDeAviso_ventanas() {
        LocalDate hoy = LocalDate.of(2026, 10, 24);

        assertEquals(TipoRecordatorio.PROXIMA_VENCER, RecordatorioService.tipoDeAviso(hoy, hoy.plusDays(3), 3));
        assertEquals(TipoRecordatorio.VENCE_HOY, RecordatorioService.tipoDeAviso(hoy, hoy, 3));
        assertEquals(TipoRecordatorio.VENCIDA, RecordatorioService.tipoDeAviso(hoy, hoy.minusDays(1), 3));
        assertNull(RecordatorioService.tipoDeAviso(hoy, hoy.plusDays(2), 3));
        assertNull(RecordatorioService.tipoDeAviso(hoy, hoy.minusDays(2), 3));
    }

    @Test
    @DisplayName("03:15 está fuera de la ventana 08:00–22:00: no envía aunque haya cuotas en ventana")
    void madrugada_noEnvia() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ZonedDateTime deMadrugada = ZonedDateTime.of(2026, 10, 24, 3, 15, 0, 0, FechasMovimiento.zona());
        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(deMadrugada);

        assertEquals(0, resultado.evaluados());
        assertEquals(0, resultado.enviados());
        assertEquals(0, resultado.omitidos());
        verify(pushSender, times(0)).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("23:15 está fuera de la ventana 08:00–22:00: no envía aunque haya cuotas en ventana")
    void noche_noEnvia() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ZonedDateTime deNoche = ZonedDateTime.of(2026, 10, 24, 23, 15, 0, 0, FechasMovimiento.zona());
        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(deNoche);

        assertEquals(0, resultado.evaluados());
        assertEquals(0, resultado.enviados());
        assertEquals(0, resultado.omitidos());
        verify(pushSender, times(0)).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("08:00 es el límite incluido de la ventana: envía")
    void inicioVentana_envia() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ZonedDateTime inicio = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, FechasMovimiento.zona());
        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(inicio);

        assertEquals(1, resultado.enviados());
        assertEquals(1, recordatorioRepository.count());
    }

    @Test
    @DisplayName("21:59 es el último minuto dentro de la ventana: envía")
    void finVentana_envia() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ZonedDateTime limitrofe = ZonedDateTime.of(2026, 10, 24, 21, 59, 0, 0, FechasMovimiento.zona());
        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(limitrofe);

        assertEquals(1, resultado.enviados());
        assertEquals(1, recordatorioRepository.count());
    }

    @Test
    @DisplayName("Deuda eliminada durante el envío: la corrida no falla y no persiste su recordatorio")
    void deudaEliminadaDuranteEnvio_noFalla() {
        Deuda deuda = deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");
        when(pushSender.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(invocacion -> {
                    // Simula el borrado concurrente: la deuda desaparece después
                    // de evaluarse y de enviarse la push, antes de persistir.
                    deudaRepository.deleteById(deuda.getId());
                    return PushSender.ResultadoEnvio.ok();
                });

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(0, resultado.enviados());
        assertEquals(0, recordatorioRepository.count());
        verify(pushSender, times(1)).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("N cuotas con mezcla VENCE_HOY: texto plural aprobado (que vencen hoy)")
    void variasCuotas_textoPluralAprobado() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");
        deuda(FechasMovimiento.hoy(), "Auto");

        recordatorioService.ejecutar(VENTANA);

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(pushSender, times(1)).enviar(anyString(), anyString(), cuerpo.capture(), anyMap());
        assertEquals("Tienes 2 cuotas que vencen hoy", cuerpo.getValue());
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private Usuario nuevoUsuario() {
        Usuario nuevo = new Usuario();
        nuevo.setRut(RUT);
        nuevo.setNombre("Ana");
        nuevo.setApellido("Pérez");
        nuevo.setEmail("ana@test.cl");
        nuevo.setContrasena("hash");
        return nuevo;
    }

    private Deuda deuda(LocalDate primeraCuota, String nombre) {
        Deuda deuda = new Deuda();
        deuda.setNombre(nombre);
        deuda.setMontoTotal(new BigDecimal("6000.00"));
        deuda.setMontoCapital(new BigDecimal("6000.00"));
        deuda.setCantidadCuotas(6);
        deuda.setEstado(EstadoDeuda.PENDIENTE);
        deuda.setTipoDeuda(TipoDeuda.OTRO);
        deuda.setModalidadCalculo(ModalidadCalculo.SIN_INTERES);
        deuda.setTasaMensual(BigDecimal.ZERO);
        deuda.setValorCuota(new BigDecimal("1000.00"));
        deuda.setGastosIniciales(BigDecimal.ZERO);
        deuda.setCostoAdicionalPorCuota(BigDecimal.ZERO);
        deuda.setCostoTotalCredito(new BigDecimal("6000.00"));
        deuda.setCargaAnualEquivalente(BigDecimal.ZERO);
        deuda.setCuotasPagadasPrevias(0);
        deuda.setMontoPagadoPrevio(BigDecimal.ZERO);
        deuda.setFechaPrimeraCuota(primeraCuota);
        deuda.setFechaCreacion(new Date());
        deuda.setUsuario(usuario);
        return deudaRepository.save(deuda);
    }
}
