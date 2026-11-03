package com.example.saveup.controller;

import com.example.saveup.model.Categoria;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.model.PlanMensual;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.PlanMensualRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.example.saveup.service.plan.NombrePartida;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10c — C56: el plan completo se arma con un número fijo de consultas. Con
 * 100 partidas y 500 movimientos no debe haber N+1: como máximo 5 consultas.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class PlanConsultasTest {

    private static final String RUT = "33333333-3";
    private static final YearMonth MES = YearMonth.of(2026, 11);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private PlanMensualRepository planMensualRepository;
    @Autowired
    private PartidaPlanRepository partidaPlanRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String token;
    private Usuario usuario;
    private Categoria alimentacion;

    @BeforeEach
    void setUp() {
        token = jwtService.generarTokenConExpiracion(RUT, "c56@example.com").token();

        usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Carla");
        usuario.setApellido("Rojas");
        usuario.setEmail("c56@example.com");
        usuario.setContrasena("$2a$10$abcdefghijklmnopqrstuv");
        usuario.setFechaRegistro(new Date());
        usuario = usuarioRepository.save(usuario);

        alimentacion = categoriaRepository.findByCodigo("ALIMENTACION_BASE")
                .orElseGet(() -> categoriaRepository.save(new Categoria(
                        null, "ALIMENTACION_BASE", "Alimentación Base", null, null,
                        TipoPresupuesto.NECESIDAD)));

        PlanMensual plan = new PlanMensual();
        plan.setUsuario(usuario);
        plan.setMes(MES.atDay(1));
        plan.setIngresoReferencia(new BigDecimal("1000000.00"));
        plan = planMensualRepository.save(plan);

        List<PartidaPlan> partidas = new ArrayList<>(100);
        for (int i = 1; i <= 100; i++) {
            PartidaPlan partida = new PartidaPlan();
            partida.setPlan(plan);
            partida.setCategoria(alimentacion);
            partida.setNombre("Partida " + i);
            partida.setNombreNormalizado(NombrePartida.normalizar("Partida " + i));
            partida.setMontoPlanificado(new BigDecimal("1000.00"));
            partida.setOrden(i);
            partidas.add(partida);
        }
        partidaPlanRepository.saveAll(partidas);

        List<Movimiento> movimientos = new ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            Movimiento movimiento = new Movimiento();
            movimiento.setUsuario(usuario);
            movimiento.setFecha(fecha(i % 28 + 1));
            movimiento.setTipoMovimiento(TipoMovimiento.GASTO_GENERAL);
            movimiento.setMonto(new BigDecimal("-100.00"));
            movimiento.setCategoria(alimentacion);
            movimiento.setDescripcion("Gasto " + i);
            movimientos.add(movimiento);
        }
        movimientoRepository.saveAll(movimientos);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("C56: 100 partidas y 500 movimientos se leen con un máximo de 5 consultas")
    void c56_numeroDeConsultasFijo() throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        mockMvc.perform(get("/api/v1/planes/{mes}", MES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true))
                .andExpect(jsonPath("$.partidas.length()").value(100))
                .andExpect(jsonPath("$.resumen.sinPlanificar.movimientos").value(500));

        long consultas = estadisticas.getPrepareStatementCount();
        assertTrue(consultas <= 5,
                "El plan debe armarse con un máximo de 5 consultas, pero se ejecutaron " + consultas);
    }

    private static Date fecha(int dia) {
        return Date.from(LocalDate.of(2026, 11, dia).atTime(12, 0)
                .atZone(ZoneId.of("America/Santiago")).toInstant());
    }
}
