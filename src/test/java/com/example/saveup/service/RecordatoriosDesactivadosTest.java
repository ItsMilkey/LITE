package com.example.saveup.service;

import com.example.saveup.dto.ResultadoRecordatoriosDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.DispositivoPush;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.Plataforma;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.DispositivoPushRepository;
import com.example.saveup.repository.RecordatorioEnviadoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.service.movimiento.FechasMovimiento;
import com.example.saveup.service.push.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * La bandera maestra {@code app.push.enabled=false} (estado del proyecto: el
 * envío está PENDIENTE de activación, requiere Firebase): {@code ejecutar(...)}
 * es no-op total — no envía y NO persiste {@code RECORDATORIO_ENVIADO}, para
 * no perder avisos cuando la función se active. El registro y la baja de
 * dispositivos siguen funcionando (los cubre {@code NotificacionControllerTest}).
 */
@SpringBootTest
@Transactional
class RecordatoriosDesactivadosTest {

    private static final String RUT = "11111111-1";
    private static final String TOKEN = "token-de-prueba-1234567890";

    /** Instante dentro de la ventana permitida ([08:00, 22:00)). */
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
    }

    @Test
    @DisplayName("app.push.enabled=false: no-op total con una cuota en ventana (ni envío ni persistencia)")
    void ejecutar_esNoOpTotal() {
        deuda(FechasMovimiento.hoy().plusDays(3), "Notebook");

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(VENTANA);

        assertEquals(0, resultado.evaluados());
        assertEquals(0, resultado.enviados());
        assertEquals(0, resultado.omitidos());
        assertEquals(0, recordatorioRepository.count());
        verifyNoInteractions(pushSender);
        // El dispositivo no se toca: sigue activo (un token muerto lo habría desactivado).
        DispositivoPush persistido = dispositivoRepository.findByToken(TOKEN).orElseThrow();
        assertTrue(persistido.estaActivo());
    }

    @Test
    @DisplayName("app.push.enabled=false fuera de ventana: mismo resultado (0, 0, 0)")
    void fueraDeVentana_mismoNoOp() {
        ZonedDateTime deMadrugada = ZonedDateTime.of(2026, 10, 24, 3, 15, 0, 0, FechasMovimiento.zona());

        ResultadoRecordatoriosDTO resultado = recordatorioService.ejecutar(deMadrugada);

        assertEquals(0, resultado.evaluados());
        assertEquals(0, resultado.enviados());
        assertEquals(0, resultado.omitidos());
        verifyNoInteractions(pushSender);
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private Usuario nuevoUsuario() {
        Usuario nuevo = new Usuario();
        nuevo.setRut(RUT);
        nuevo.setNombre("Ana");
        nuevo.setApellido("Pérez");
        nuevo.setEmail("desactivada@test.cl");
        nuevo.setContrasena("hash");
        return nuevo;
    }

    private void deuda(LocalDate primeraCuota, String nombre) {
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
        deudaRepository.save(deuda);
    }
}
