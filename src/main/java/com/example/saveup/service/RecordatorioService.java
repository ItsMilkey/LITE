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
import java.time.ZonedDateTime;
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
 *
 * <p>BANDERA MAESTRA {@code app.push.enabled=false} (por defecto): la corrida
 * es un no-op total — no envía y NO persiste {@code RECORDATORIO_ENVIADO},
 * para no perder avisos cuando la función se active (requiere proveedor
 * externo; PENDIENTE de activación). El registro y la baja de tokens de
 * dispositivo (T9.6) funcionan igual con la bandera apagada.
 */
@Service
public class RecordatorioService {

    private static final Logger LOG = LoggerFactory.getLogger(RecordatorioService.class);

    /** Payload que la app usa para navegar al tocar la notificación. */
    static final String TIPO_PAYLOAD = "RECORDATORIO_CUOTA";

    /**
     * Ventana horaria permitida del aviso (T9.6 D4): [08:00, 22:00) en la zona
     * del negocio. Defendida aquí y no solo en el horario del cron: el trigger
     * externo puede disparar cada hora y así nunca sale una push de madrugada
     * (o antes de las 08:00) aunque cambie la configuración del cron.
     */
    static final int HORA_INICIO = 8;

    static final int HORA_FIN = 22;

    private final DispositivoPushRepository dispositivoRepository;

    private final RecordatorioEnviadoRepository recordatorioRepository;

    private final DeudaRepository deudaRepository;

    private final DeudaService deudaService;

    private final PushSender pushSender;

    @Value("${app.push.dias-antes:3}")
    private int diasAntes;

    /**
     * Bandera maestra de la feature (T9.6, PENDIENTE de activación): con
     * {@code app.push.enabled=false} la corrida no envía ni persiste nada.
     */
    @Value("${app.push.enabled:false}")
    private boolean pushHabilitado;

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
     * llamadas el mismo día producen el mismo estado final. No envía nada fuera
     * de la ventana permitida ({@link #HORA_INICIO}–{@link #HORA_FIN}).
     */
    @Transactional
    public ResultadoRecordatoriosDTO ejecutar() {
        return ejecutar(ZonedDateTime.now(FechasMovimiento.zona()));
    }

    /**
     * Corrida con el instante inyectado (seam para tests). Es el punto único de
     * entrada del scheduler interno y del endpoint de cron: cualquiera que sea
     * la configuración del disparador, la regla de horario vale igual.
     */
    @Transactional
    public ResultadoRecordatoriosDTO ejecutar(ZonedDateTime ahora) {
        if (!pushHabilitado) {
            LOG.info("Recordatorios deshabilitados (app.push.enabled=false): no-op total; "
                    + "no se envía NI se persiste RECORDATORIO_ENVIADO para no perder avisos "
                    + "cuando la función se active.");
            return new ResultadoRecordatoriosDTO(0, 0, 0);
        }

        int hora = ahora.getHour();
        if (hora < HORA_INICIO || hora >= HORA_FIN) {
            LOG.info("Recordatorios fuera de la ventana permitida [{}:00, {}:00); "
                    + "no se evalúa ni envía nada (hora={}:00 {}", HORA_INICIO, HORA_FIN, hora,
                    ahora.getZone());
            return new ResultadoRecordatoriosDTO(0, 0, 0);
        }

        LocalDate hoy = FechasMovimiento.hoy();
        int ventana = Math.max(1, diasAntes);

        int evaluados = 0;
        int envios = 0;
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

            boolean algunEnvio = enviar(rut, avisos, destinos, ventana);
            if (!algunEnvio) {
                omitidos += avisos.size();
                continue;
            }

            envios += persistir(destinos.get(0).getUsuario(), avisos);
        }

        if (envios > 0 || omitidos > 0) {
            LOG.info("Recordatorios T9.6: evaluados={} enviados={} omitidos={}",
                    evaluados, envios, omitidos);
        }
        return new ResultadoRecordatoriosDTO(evaluados, envios, omitidos);
    }

    /** Envía la push agrupada; devuelve true si al menos un dispositivo la aceptó. */
    private boolean enviar(String rut, List<Aviso> avisos, List<DispositivoPush> destinos, int ventana) {
        String cuerpo = componerCuerpo(avisos, ventana);
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
     * Persiste la bitácora de deduplicación de cada aviso. La deuda pudo haber
     * sido eliminada mientras la push volaba: en ese caso se registra y se
     * continúa (la corrida no falla por un borrado concurrente).
     *
     * @return la cantidad de recordatorios realmente persistidos.
     */
    private int persistir(Usuario usuario, List<Aviso> avisos) {
        Map<Long, Deuda> deudasExistentes = deudaRepository
                .findAllById(avisos.stream().map(Aviso::deudaId).toList())
                .stream()
                .collect(Collectors.toMap(Deuda::getId, deuda -> deuda));

        int persistidos = 0;
        for (Aviso aviso : avisos) {
            Deuda deuda = deudasExistentes.get(aviso.deudaId());
            if (deuda == null) {
                LOG.info("Deuda {} eliminada durante la corrida; no se persiste su recordatorio",
                        aviso.deudaId());
                continue;
            }
            recordatorioRepository.save(nuevoRecordatorio(usuario, deuda, aviso));
            persistidos++;
        }
        return persistidos;
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

    private String componerCuerpo(List<Aviso> avisos, int ventana) {
        if (avisos.size() == 1) {
            Aviso aviso = avisos.get(0);
            String cuando = switch (aviso.tipo()) {
                case PROXIMA_VENCER -> "vence en " + ventana + " días";
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
            // Texto aprobado en T9.6 §5 ("Tienes 3 cuotas que vencen hoy").
            cuando = "que vencen hoy";
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

    private RecordatorioEnviado nuevoRecordatorio(Usuario usuario, Deuda deuda, Aviso aviso) {
        RecordatorioEnviado recordatorio = new RecordatorioEnviado();
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
