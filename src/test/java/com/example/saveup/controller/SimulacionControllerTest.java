package com.example.saveup.controller;

import com.example.saveup.dto.SimulacionCreditoRequestDTO;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private DeudaRepository deudaRepository;

    @MockBean
    private MovimientoRepository movimientoRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private MetaAhorroRepository metaAhorroRepository;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = jwtService.generateToken("12345678-5", "test@example.com");
    }

    @Test
    @DisplayName("Petición sin token JWT → 401 Unauthorized")
    void simularCredito_sinToken_retorna401() throws Exception {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(4)
                .build();

        mockMvc.perform(post("/api/simulaciones/credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Petición con token JWT → 200 OK y no interactúa con ningún repositorio (no persiste)")
    void simularCredito_conToken_retorna200YNoPersiste() throws Exception {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(4)
                .build();

        mockMvc.perform(post("/api/simulaciones/credito")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorCuota").value(25000.00))
                .andExpect(jsonPath("$.condiciones.modalidad").value("SIN_INTERES"))
                .andExpect(jsonPath("$.condiciones.cantidadCuotas").value(4))
                .andExpect(jsonPath("$.resumenEducativo.costoPorCada100").value(100.00))
                .andExpect(jsonPath("$.advertencias[0]").exists());

        // Verificación de criterio de aceptación: no se persiste nada en base de datos
        verifyNoInteractions(deudaRepository, movimientoRepository, usuarioRepository, metaAhorroRepository);
    }

    @Test
    @DisplayName("Plazo por fechas en endpoint → 200 OK con cuotas calculadas")
    void simularCredito_plazoPorFechas() throws Exception {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("140000"))
                .fechaPrimeraCuota(LocalDate.of(2026, 10, 7))
                .fechaUltimaCuota(LocalDate.of(2027, 11, 8))
                .build();

        mockMvc.perform(post("/api/simulaciones/credito")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condiciones.cantidadCuotas").value(14))
                .andExpect(jsonPath("$.condiciones.fechaPrimeraCuota").value("2026-10-07"))
                .andExpect(jsonPath("$.condiciones.fechaUltimaCuota").value("2027-11-07"));

        verifyNoInteractions(deudaRepository, movimientoRepository, usuarioRepository, metaAhorroRepository);
    }

    @Test
    @DisplayName("Validación cruzada inválida (cantidadCuotas + fechaUltimaCuota) → 400 Bad Request con mensaje de error")
    void simularCredito_validacionCruzadaInvalida_retorna400() throws Exception {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .fechaUltimaCuota(LocalDate.of(2027, 10, 7))
                .build();

        mockMvc.perform(post("/api/simulaciones/credito")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(deudaRepository, movimientoRepository, usuarioRepository, metaAhorroRepository);
    }

    @Test
    @DisplayName("Validación Bean Validation (modalidad nula o montoCapital <= 0) → 400 Bad Request")
    void simularCredito_beanValidationInvalido_retorna400() throws Exception {
        // Monto capital negativo
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(new BigDecimal("-100"))
                .cantidadCuotas(12)
                .build();

        mockMvc.perform(post("/api/simulaciones/credito")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deudaRepository, movimientoRepository, usuarioRepository, metaAhorroRepository);
    }

    @Test
    @DisplayName("Simulación con plazos alternativos e ingreso mensual → 200 OK con comparaciones y advertencias")
    void simularCredito_conAlternativasEIngreso() throws Exception {
        SimulacionCreditoRequestDTO request = SimulacionCreditoRequestDTO.builder()
                .modalidad(ModalidadCalculo.TASA_CONOCIDA)
                .montoCapital(new BigDecimal("100000"))
                .cantidadCuotas(12)
                .tasaMensual(new BigDecimal("1.5"))
                .cantidadCuotasAlternativas(List.of(6, 12, 24))
                .ingresoMensual(new BigDecimal("20000")) // cuota superará el 30% del ingreso
                .build();

        mockMvc.perform(post("/api/simulaciones/credito")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparacionPlazos.length()").value(3))
                .andExpect(jsonPath("$.advertencias.length()").value(2))
                .andExpect(jsonPath("$.resumenEducativo.porcentajeIngresoComprometido").exists());

        verifyNoInteractions(deudaRepository, movimientoRepository, usuarioRepository, metaAhorroRepository);
    }
}
