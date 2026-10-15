package com.example.saveup.repository;

import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test de las consultas con proyección JPQL de {@link DeudaRepository}
 * contra una base de datos real (H2 embebida en modo PostgreSQL, perfil test).
 *
 * <p>No Sustituye la validación final sobre PostgreSQL/Supabase (regla: el agente
 * no tiene credenciales), pero sí ejecuta el JPQL de verdad: valida el GROUP BY,
 * el mapeo al constructor del DTO y el COALESCE/SUM sobre el LEFT JOIN, que es
 * justo lo que no se puede verificar con repositorios simulados.
 */
@DataJpaTest
class DeudaRepositorySmokeTest {

    @Autowired
    private DeudaRepository deudaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("La proyección JPQL agrupa, suma los pagos y mapea al DTO sin error")
    void proyeccion_conPagos_mapeaTodosLosCampos() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("11111111-1"));
        Deuda deuda = deudaRepository.save(nuevaDeuda(usuario, "Con pagos"));
        guardarPago(usuario, deuda, new BigDecimal("-10000.00"));
        guardarPago(usuario, deuda, new BigDecimal("-5000.50"));

        List<DeudaResponseDTO> lista = deudaRepository.findDeudasDTOByUsuarioRut(usuario.getRut());

        assertEquals(1, lista.size(), "Una sola fila por deuda pese a tener 2 pagos (GROUP BY)");
        DeudaResponseDTO dto = lista.get(0);

        assertEquals(deuda.getId(), dto.getId());
        assertEquals("Con pagos", dto.getNombre());
        assertEquals(0, new BigDecimal("100000.00").compareTo(dto.getMontoTotal()));
        assertEquals(10, dto.getCantidadCuotas());
        assertEquals(EstadoDeuda.PENDIENTE, dto.getEstado());
        assertEquals(TipoDeuda.CREDITO_CONSUMO, dto.getTipoDeuda());
        assertEquals(ModalidadCalculo.TASA_CONOCIDA, dto.getModalidadCalculo());
        assertEquals(0, new BigDecimal("100000.00").compareTo(dto.getMontoCapital()));
        assertEquals(0, new BigDecimal("0.015").compareTo(dto.getTasaMensualFraccion()));
        assertEquals(0, new BigDecimal("15000.50").compareTo(dto.getMontoPagadoReal()),
                "La suma de los PAGO_DEUDA viaja en positivo y sin las previas");
        assertEquals(0, new BigDecimal("0.00").compareTo(dto.getMontoPagadoPrevio()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(dto.getGastosIniciales()));
        assertEquals(0, new BigDecimal("150.00").compareTo(dto.getCostoAdicionalPorCuota()));
        assertEquals(0, dto.getCuotasPagadasPrevias());
        assertEquals(LocalDate.of(2026, 10, 7), dto.getFechaPrimeraCuota());
        assertNotNull(dto.getCostoTotalCredito());
        assertNotNull(dto.getFechaCreacion());
        // El DTO es solo datos: estos dos los calcula el servicio, no la consulta
        assertEquals(0, dto.getCuotasPagadas());
        assertNull(dto.getTotalPorPagar());
    }

    @Test
    @DisplayName("La consulta por ID devuelve la misma fila que el listado")
    void proyeccionPorId_igualAlListado() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("22222222-2"));
        Deuda deuda = deudaRepository.save(nuevaDeuda(usuario, "Por ID"));

        Optional<DeudaResponseDTO> porId = deudaRepository.findDeudaDTOById(deuda.getId());

        assertTrue(porId.isPresent());
        assertEquals(0, BigDecimal.ZERO.compareTo(porId.get().getMontoPagadoReal()));
        assertEquals(deuda.getNombre(), porId.get().getNombre());
    }

    @Test
    @DisplayName("Una deuda sin pagos devuelve 0 (no null) en la suma del LEFT JOIN")
    void proyeccion_sinPagos_sumaEnCero() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("33333333-3"));
        deudaRepository.save(nuevaDeuda(usuario, "Sin pagos"));

        List<DeudaResponseDTO> lista = deudaRepository.findDeudasDTOByUsuarioRut(usuario.getRut());

        assertEquals(1, lista.size());
        DeudaResponseDTO dto = lista.get(0);
        assertNotNull(dto.getMontoPagadoReal(), "COALESCE debe evitar el null del LEFT JOIN");
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.getMontoPagadoReal()));
    }

    @Test
    @DisplayName("Solo se suman los PAGO_DEUDA: ingresos y gastos de la misma deuda no cuentan")
    void proyeccion_ignoraMovimientosQueNoSonPago() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("44444444-4"));
        Deuda deuda = deudaRepository.save(nuevaDeuda(usuario, "Mixta"));
        guardar(usuario, deuda, TipoMovimiento.GASTO_GENERAL, new BigDecimal("-999.00"));

        DeudaResponseDTO dto = deudaRepository.findDeudaDTOById(deuda.getId()).orElseThrow();

        assertEquals(0, BigDecimal.ZERO.compareTo(dto.getMontoPagadoReal()));
    }

    @Test
    @DisplayName("Las deudas de otro usuario no aparecen en la proyección")
    void proyeccion_otroUsuario_noAparece() {
        Usuario dueno = usuarioRepository.save(nuevoUsuario("55555555-5"));
        Usuario otro = usuarioRepository.save(nuevoUsuario("66666666-6"));
        deudaRepository.save(nuevaDeuda(otro, "Ajena"));

        assertTrue(deudaRepository.findDeudasDTOByUsuarioRut(dueno.getRut()).isEmpty());
        assertEquals(1, deudaRepository.findDeudasDTOByUsuarioRut(otro.getRut()).size());
    }

    // ──────────────────────── helpers ────────────────────────

    private Usuario nuevoUsuario(String rut) {
        Usuario u = new Usuario();
        u.setRut(rut);
        u.setNombre("Usuario " + rut);
        u.setApellido("Test");
        u.setEmail(rut + "@test.cl");
        u.setContrasena("hash");
        return u;
    }

    private Deuda nuevaDeuda(Usuario usuario, String nombre) {
        Deuda d = new Deuda();
        d.setUsuario(usuario);
        d.setNombre(nombre);
        d.setDescripcion("Smoke test");
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

    private void guardarPago(Usuario usuario, Deuda deuda, BigDecimal monto) {
        guardar(usuario, deuda, TipoMovimiento.PAGO_DEUDA, monto);
    }

    private void guardar(Usuario usuario, Deuda deuda, TipoMovimiento tipo, BigDecimal monto) {
        Movimiento m = new Movimiento();
        m.setUsuario(usuario);
        m.setDeuda(deuda);
        m.setTipoMovimiento(tipo);
        m.setMonto(monto);
        m.setDescripcion("Movimiento " + tipo);
        m.setFecha(new java.util.Date());
        movimientoRepository.save(m);
        entityManager.flush();
        entityManager.clear();
    }
}
