package com.example.saveup.controller;

import com.example.saveup.dto.UsuarioRegistroDTO;
import com.example.saveup.model.AsignacionMetaPresupuesto;
import com.example.saveup.model.AuthRefreshToken;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.MetaAhorro;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PresupuestoCategoria;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.AuthRefreshTokenRepository;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PresupuestoCategoriaRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integración REAL (H2 en modo PostgreSQL, sin mocks) del borrado de cuenta
 * (T9.2): registra un usuario con datos en todas las tablas del dominio
 * (meta, deuda, movimientos con pagos, presupuesto 50/30/20, límite por
 * categoría, asignación de meta al presupuesto y sesiones), lo elimina,
 * verifica que no queda rastro y que el mismo RUT/correo vuelve a estar
 * disponible para un re-registro.
 *
 * <p>No sustituye la validación final sobre PostgreSQL/Supabase (el agente no
 * tiene credenciales, como documenta DeudaRepositorySmokeTest), pero ejercita
 * de verdad las FKs, el orden hijo→padre y la transaccionalidad.
 */
@SpringBootTest
@Transactional // rollback al final del test: H2 queda limpio y sin colisiones
class UsuarioEliminacionIntegracionTest {

    private static final String RUT = "99999999-9";
    private static final String EMAIL = "borrado.integracion@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MetaAhorroRepository metaAhorroRepository;

    @Autowired
    private DeudaRepository deudaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;

    @Autowired
    private PresupuestoCategoriaRepository presupuestoCategoriaRepository;

    @Autowired
    private AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;

    @Autowired
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    @DisplayName("Eliminar la cuenta borra todo el dominio del usuario y libera RUT/correo para re-registro")
    void eliminarCuenta_borraTodoYLiberaElRegistro() {
        registrarConDatos();

        Long configuracionId = configuracionPresupuestoRepository.findByUsuarioRut(RUT).orElseThrow().getId();
        List<AsignacionMetaPresupuesto> asignaciones =
                asignacionMetaPresupuestoRepository.findByConfiguracionId(configuracionId);
        assertEquals(1, asignaciones.size(), "Precondición: hay una asignación que morirá con la cuenta.");

        usuarioService.eliminarCuenta(RUT, PASSWORD);

        assertFalse(usuarioRepository.findById(RUT).isPresent(), "El usuario ya no existe.");
        assertTrue(metaAhorroRepository.findByUsuarioRut(RUT).isEmpty(), "Sin metas.");
        assertTrue(deudaRepository.findByUsuarioRut(RUT).isEmpty(), "Sin deudas.");
        assertTrue(movimientoRepository.findByUsuarioRutOrderByFechaDesc(RUT).isEmpty(),
                "Sin movimientos (incluido el historial de pagos).");
        assertTrue(configuracionPresupuestoRepository.findByUsuarioRut(RUT).isEmpty(),
                "Sin presupuesto 50/30/20.");
        assertTrue(presupuestoCategoriaRepository.findByUsuarioRut(RUT).isEmpty(),
                "Sin límites por categoría.");
        assertTrue(asignacionMetaPresupuestoRepository.findByConfiguracionId(configuracionId).isEmpty(),
                "La asignación murió con su configuración.");
        assertTrue(authRefreshTokenRepository.findByUsuario_RutAndFechaRevocacionIsNull(RUT).isEmpty(),
                "Sin sesiones (refresh tokens borrados).");

        // Re-registro con los mismos datos (Apple/Google: poder crear cuenta de nuevo).
        registrarConDatos();
        assertTrue(usuarioRepository.findById(RUT).isPresent(), "El RUT vuelve a estar disponible.");
        List<MetaAhorro> metasRecreadas = metaAhorroRepository.findByUsuarioRut(RUT);
        assertEquals(1, metasRecreadas.size(), "El re-registro crea la meta por defecto 'Ahorros'.");
        assertEquals("Ahorros", metasRecreadas.get(0).getNombre());
    }

    // ──────────────────────── helpers ────────────────────────

    /** Registra al usuario y crea deudas, movimientos, presupuesto, límites, asignación y sesiones. */
    private void registrarConDatos() {
        UsuarioRegistroDTO registro = new UsuarioRegistroDTO();
        registro.setRut(RUT);
        registro.setNombre("Borra");
        registro.setApellido("Integracion");
        registro.setEmail(EMAIL);
        registro.setContrasena(PASSWORD);
        usuarioService.registrarUsuario(registro);

        Usuario usuario = usuarioRepository.findById(RUT).orElseThrow();
        assertNotNull(usuario, "El usuario quedó registrado.");

        // El registro crea la meta por defecto "Ahorros"; la reutilizo.
        MetaAhorro meta = metaAhorroRepository.findByUsuarioRut(RUT).get(0);

        Deuda deuda = deudaRepository.save(nuevaDeuda(usuario));

        Movimiento pago = pagoDeDeuda(usuario, deuda);
        movimientoRepository.save(pago);
        Movimiento ingreso = ingresoGeneral(usuario);
        movimientoRepository.save(ingreso);

        ConfiguracionPresupuesto configuracion = nuevoPresupuesto(usuario);
        configuracionPresupuestoRepository.save(configuracion);

        Categoria categoria = categoriaReutilizable();
        presupuestoCategoriaRepository.save(new PresupuestoCategoria(null, usuario, categoria,
                new BigDecimal("100000.00")));
        asignacionMetaPresupuestoRepository.save(new AsignacionMetaPresupuesto(
                null, configuracion, meta, new BigDecimal("100.00")));

        authRefreshTokenRepository.save(nuevoRefreshToken(usuario));
    }

    private Deuda nuevaDeuda(Usuario usuario) {
        Deuda d = new Deuda();
        d.setUsuario(usuario);
        d.setNombre("Cuenta de prueba");
        d.setDescripcion("T9.2 borrado de cuenta");
        d.setMontoTotal(new BigDecimal("100000.00"));
        d.setCantidadCuotas(10);
        d.setEstado(EstadoDeuda.PENDIENTE);
        d.setTipoDeuda(TipoDeuda.CREDITO_CONSUMO);
        d.setModalidadCalculo(ModalidadCalculo.TASA_CONOCIDA);
        d.setMontoCapital(new BigDecimal("100000.00"));
        d.setTasaMensual(new BigDecimal("0.015"));
        d.setValorCuota(new BigDecimal("9166.86"));
        d.setGastosIniciales(new BigDecimal("2000.00"));
        d.setCostoAdicionalPorCuota(new BigDecimal("150.00"));
        d.setCuotasPagadasPrevias(0);
        d.setMontoPagadoPrevio(BigDecimal.ZERO);
        d.setCostoTotalCredito(new BigDecimal("102000.00"));
        d.setCargaAnualEquivalente(new BigDecimal("0.1956"));
        d.setFechaPrimeraCuota(LocalDate.of(2026, 10, 7));
        return d;
    }

    private Movimiento pagoDeDeuda(Usuario usuario, Deuda deuda) {
        Movimiento m = new Movimiento();
        m.setUsuario(usuario);
        m.setDeuda(deuda);
        m.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        m.setMonto(new BigDecimal("-10000.00"));
        m.setDescripcion("Pago deuda T9.2");
        return m;
    }

    private Movimiento ingresoGeneral(Usuario usuario) {
        Movimiento m = new Movimiento();
        m.setUsuario(usuario);
        m.setTipoMovimiento(TipoMovimiento.INGRESO_GENERAL);
        m.setMonto(new BigDecimal("500000.00"));
        m.setDescripcion("Sueldo T9.2");
        return m;
    }

    private ConfiguracionPresupuesto nuevoPresupuesto(Usuario usuario) {
        ConfiguracionPresupuesto c = new ConfiguracionPresupuesto();
        c.setUsuario(usuario);
        c.setPorcentajeNecesidades(new BigDecimal("50.00"));
        c.setPorcentajeDeseos(new BigDecimal("30.00"));
        c.setPorcentajeAhorro(new BigDecimal("20.00"));
        return c;
    }

    /** Categoria reutilizable: el nombre es único y el re-registro no debe insertarla de nuevo. */
    private Categoria categoriaReutilizable() {
        String nombre = "CATEG_BORRADO_" + Integer.toHexString(RUT.hashCode());
        return categoriaRepository.findByNombre(nombre)
                .orElseGet(() -> categoriaRepository.save(nuevaCategoria(nombre)));
    }

    private Categoria nuevaCategoria(String nombre) {
        Categoria c = new Categoria();
        // Valor único: el modelo exige nombre único y vive más allá del borrado (no es del usuario).
        c.setNombre(nombre);
        c.setTipoPresupuesto(TipoPresupuesto.NECESIDAD);
        return c;
    }

    private AuthRefreshToken nuevoRefreshToken(Usuario usuario) {
        AuthRefreshToken t = new AuthRefreshToken();
        t.setUsuario(usuario);
        t.setTokenHash("hash-de-prueba-integracion");
        t.setFechaExpiracion(new Date(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000));
        return t;
    }
}
