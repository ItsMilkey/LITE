package com.example.saveup.service;

import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.ResultadoRecordatoriosDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.DispositivoPush;
import com.example.saveup.model.RecordatorioEnviado;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.TipoRecordatorio;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.DispositivoPushRepository;
import com.example.saveup.repository.RecordatorioEnviadoRepository;
import com.example.saveup.service.push.PushSender;
import com.example.saveup.service.movimiento.FechasMovimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Job de recordatorios de vencimiento (T9.6).
 *
 * <p>Recorre los usuarios con al menos un dispositivo activo, calcula la próxima
 * cuota impaga de cada deuda pendiente y avisa en tres ventanas: T-3
 * ({@code PROXIMA_VENCER}), T-0 ({@code VENCE_HOY}) y T+1 ({@code VENCIDA}).
 *
 * <p>Decisiones que conviene no perder de vista:
 * <ul>
 *   <li>Los avisos se <b>agrupan por usuario</b>: una push por corrida, no una
 *       por deuda. El detalle va en el payload de datos.</li>
 *   <li>La próxima cuota impaga se reutiliza de {@link DeudaService} (T3); aquí
 *       no se reimplementa la imputación de pagos.</li>
 *   <li>La deduplicación vive en {@code RECORDATORIO_ENVIADO}; si el job corre
 *       dos veces el mismo día, la segunda no envía nada.</li>
 *   <li>Se persiste el aviso solo si al menos un dispositivo aceptó el envío.
 *       Si todos fallaron, mañana se reintenta.</li>
 * </ul>
 */
@Service
public class RecordatorioService {

    private static final Logger LOG = LoggerFactory.getLogger(RecordatorioService.class);

    /** Payload que la app usa para navegar al tocar la notificación. */
    static final String TIPO_PAYLOAD = "RECORDATORIO_CUOTA";

    private final DispositivoPushRepository dispositivoRepository;

    private final RecordatorioEnviadoRepository recordatorioRepository;

    private final DeudaRepository deudaRepository;

    private final DeudaService deudaService;

    private final PushSender pushSender;

    @Value("${app.push.dias-antes:3}")
    private int diasAntes;

    public RecordatorioService(DispositivoPushRepository dispositivoRepository,
                               RecordatorioEnviadoRepository recordatorioRepository,
                               DeudaRepository deudaRepository,
                               DeudaService deudaService,
                               PushSender pushSender) {
        this.dispositivoRepository = dispositivoRepository;
        this.recordatorioRepository = recordatorioRepository;
        this.deudaRepository = deudaRepository;
        this.deudaService = deudaService;
        this.pushSender = pushSender;
    }

    /**
     * Ejecuta una corrida completa y devuelve el resumen. Es idempotente: dos
     * llamadas el mismo día producen el mismo estado final.
     *
     * <p>El envío ocurre dentro de la transacción para que la bitácora de
     * deduplicación y el resultado del envío sean consistentes. Con el volumen
     * esperado del MVP (una corrida diaria) es aceptable; si el número de
     * dispositivos crece, conviene separar cálculo, envío y persistencia.
     */
    @Transactional
    public ResultadoRecordatoriosDTO ejecutar() {
        LocalDate hoy = FechasMovimiento.hoy();
        int ventana = Math.max(1, diasAntes);

        int evaluados = 0;
        int enviados = 0;
        int omitidos = 0;

        for (String rut : dispositivoRepository.findRutsConDispositivosActivos()) {
            List<Aviso> avisos = new ArrayList<>();

            for (DeudaResponseDTO deuda : deudaService.obtenerDeudasPorUsuario(rut)) {
                if (deuda.getEstado() != EstadoDeuda.PENDIENTE
                        || deuda.getProximaFechaVencimiento() == null
                        || deuda.getProximaCuotaNumero() == null) {
                    continue;
                }

                evaluados++;
                TipoRecordatorio tipo = tipoDeAviso(hoy, deuda.getProximaFechaVencimiento(), ventana);
                if (tipo == null) {
                    continue;
                }
                if (recordatorioRepository.existsByDeudaIdAndCuotaNumeroAndTipo(
                        deuda.getId(), deuda.getProximaCuotaNumero(), tipo)) {
                    omitidos++;
                    continue;
                }
                avisos.add(new Aviso(deuda.getId(), deuda.getProximaCuotaNumero(), tipo, deuda.getNombre()));
            }

            if (avisos.isEmpty()) {
                continue;
            }

            List<DispositivoPush> destinos =
                    dispositivoRepository.findByUsuarioRutAndActivoTrueOrderByFechaUltimoUsoDesc(rut);
            if (destinos.isEmpty()) {
                omitidos += avisos.size();
                continue;
            }

            boolean algunEnvio = enviar(rut, avisos, destinos);
            if (!algunEnvio) {
                omitidos += avisos.size();
                continue;
            }

            Usuario usuario = destinos.get(0).getUsuario();
            avisos.forEach(aviso -> recordatorioRepository.save(nuevoRecordatorio(usuario, aviso)));
            enviados += avisos.size();
        }

        if (enviados > 0 || omitidos > 0) {
            LOG.info("Recordatorios T9.6: evaluados={} enviados={} omitidos={}",
                    evaluados, enviados, omitidos);
        }
        return new ResultadoRecordatoriosDTO(evaluados, enviados, omitidos);
    }

    /** Envía la push agrupada; devuelve true si al menos un dispositivo la aceptó. */
    private boolean enviar(String rut, List<Aviso> avisos, List<DispositivoPush> destinos) {
        String cuerpo = componerCuerpo(avisos);
        Map<String, String> datos = componerDatos(avisos);

        boolean algunEnvio = false;
        for (DispositivoPush destino : destinos) {
            PushSender.ResultadoEnvio resultado =
                    pushSender.enviar(destino.getToken(), "SaveUp Lite", cuerpo, datos);

            if (resultado.enviado()) {
                algunEnvio = true;
                destino.setFechaUltimoUso(new Date());
            }
            if (resultado.tokenInvalido()) {
                destino.setActivo(false);
                LOG.info("Dispositivo de {} desactivado: FCM rechazó el token", rut);
            }
        }
        return algunEnvio;
    }

    /**
     * Ventana del aviso. T-{@code ventana}, mismo día y un día después. Fuera de
     * esas tres fechas no hay notificación: no se insiste todos los días.
     */
    static TipoRecordatorio tipoDeAviso(LocalDate hoy, LocalDate vencimiento, int ventana) {
        long dias = ChronoUnit.DAYS.between(hoy, vencimiento);
        if (dias == ventana) {
            return TipoRecordatorio.PROXIMA_VENCER;
        }
        if (dias == 0) {
            return TipoRecordatorio.VENCE_HOY;
        }
        if (dias == -1) {
            return TipoRecordatorio.VENCIDA;
        }
        return null;
    }

    private String componerCuerpo(List<Aviso> avisos) {
        if (avisos.size() == 1) {
            Aviso aviso = avisos.get(0);
            String cuando = switch (aviso.tipo()) {
                case PROXIMA_VENCER -> "vence en " + Math.max(1, diasAntes) + " días";
                case VENCE_HOY -> "vence hoy";
                case VENCIDA -> "venció ayer";
            };
            return "Cuota " + aviso.cuota() + " de «" + aviso.deudaNombre() + "» " + cuando;
        }

        Set<TipoRecordatorio> tipos = avisos.stream().map(Aviso::tipo).collect(Collectors.toSet());
        String cuando;
        if (tipos.equals(Set.of(TipoRecordatorio.VENCIDA))) {
            cuando = "vencidas";
        } else if (tipos.contains(TipoRecordatorio.VENCE_HOY)) {
            cuando = "por vencer o vencidas hoy";
        } else {
            cuando = "por vencer";
        }
        return "Tienes " + avisos.size() + " cuotas " + cuando;
    }

    private Map<String, String> componerDatos(List<Aviso> avisos) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("tipo", TIPO_PAYLOAD);
        datos.put("cantidad", String.valueOf(avisos.size()));

        if (avisos.size() == 1) {
            Aviso aviso = avisos.get(0);
            datos.put("deudaId", String.valueOf(aviso.deudaId()));
            datos.put("cuota", String.valueOf(aviso.cuota()));
            datos.put("deepLink", "saveup://deudas/" + aviso.deudaId());
        } else {
            datos.put("deepLink", "saveup://deudas");
        }
        return datos;
    }

    private RecordatorioEnviado nuevoRecordatorio(Usuario usuario, Aviso aviso) {
        RecordatorioEnviado recordatorio = new RecordatorioEnviado();
        Deuda deuda = deudaRepository.getReferenceById(aviso.deudaId());
        recordatorio.setUsuario(usuario);
        recordatorio.setDeuda(deuda);
        recordatorio.setCuotaNumero(aviso.cuota());
        recordatorio.setTipo(aviso.tipo());
        return recordatorio;
    }

    /** Cuota concreta que motiva el aviso. */
    private record Aviso(Long deudaId, int cuota, TipoRecordatorio tipo, String deudaNombre) {
    }
}
