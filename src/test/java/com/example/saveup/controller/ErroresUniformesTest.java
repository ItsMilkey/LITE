package com.example.saveup.controller;

import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.JwtService;
import com.example.saveup.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ErroresUniformesTest {

    private static final String RUT = "12345678-5";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private CategoriaService categoriaService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private DeudaRepository deudaRepository;

    @MockBean
    private MovimientoRepository movimientoRepository;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtService.generateToken(RUT, "test@example.com");
    }

    @Test
    @DisplayName("401 sin token → NO_AUTENTICADO en problem+json")
    void sinToken_noAutenticado() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail").value("No se pudo autenticar la solicitud."))
                .andExpect(jsonPath("$.instance").value("/api/categorias"));
    }

    @Test
    @DisplayName("401 con token expirado → TOKEN_EXPIRADO, distinto de NO_AUTENTICADO")
    void tokenExpirado_tokenExpirado() throws Exception {
        Object original = ReflectionTestUtils.getField(jwtService, "expirationInMs");
        String expirado;
        try {
            ReflectionTestUtils.setField(jwtService, "expirationInMs", -1_000L);
            expirado = jwtService.generateToken(RUT, "test@example.com");
        } finally {
            ReflectionTestUtils.setField(jwtService, "expirationInMs", original);
        }

        mockMvc.perform(get("/api/categorias")
                        .header("Authorization", "Bearer " + expirado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_EXPIRADO"))
                .andExpect(jsonPath("$.detail").value("Tu sesión ha expirado. Inicia sesión nuevamente."));
    }

    @Test
    @DisplayName("401 con email inexistente → CREDENCIALES_INVALIDAS con mensaje genérico")
    void credencialesInvalidas() throws Exception {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@example.com","contrasena":"Password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value("El correo o la contraseña no son correctos."));
    }

    @Test
    @DisplayName("Validación con dos campos inválidos → errores[] con ambos y nombres JSON")
    void validacionDosCampos() throws Exception {
        mockMvc.perform(post("/api/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","condiciones":{"modalidad":"SIN_INTERES","montoCapital":0,"cantidadCuotas":10}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[*].campo",
                        hasItems("nombre", "condiciones.montoCapital")));
    }

    @Test
    @DisplayName("JSON malformado → 400 SOLICITUD_INVALIDA")
    void jsonMalformado_solicitudInvalida() throws Exception {
        mockMvc.perform(post("/api/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("Parámetro con tipo incorrecto → 400 PARAMETRO_INVALIDO")
    void parametroInvalido() throws Exception {
        mockMvc.perform(get("/api/presupuestos/ejecucion/me")
                        .param("month", "abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("Regla del motor financiero → 400 DATOS_INVALIDOS con mensaje legible")
    void datosInvalidos() throws Exception {
        mockMvc.perform(post("/api/simulaciones/credito")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":12,
                                 "fechaUltimaCuota":"2027-10-07"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("Deuda inexistente → 404 NO_ENCONTRADO")
    void noEncontrado() throws Exception {
        when(deudaRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/deudas/77")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Deuda ajena → 403 PROHIBIDO con detail fijo")
    void prohibido() throws Exception {
        Usuario otro = new Usuario();
        otro.setRut("99999999-9");
        otro.setNombre("Otro");

        Deuda ajena = new Deuda();
        ajena.setId(78L);
        ajena.setUsuario(otro);
        when(deudaRepository.findById(78L)).thenReturn(Optional.of(ajena));

        mockMvc.perform(get("/api/deudas/78")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.detail").value("No tienes permisos para acceder a este recurso."));
    }

    @Test
    @DisplayName("Movimiento no editable → 409 CONFLICTO_REGLA_NEGOCIO")
    void conflictoReglaNegocio() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setRut(RUT);
        usuario.setNombre("Dueño");

        Movimiento abono = new Movimiento();
        abono.setId(1L);
        abono.setUsuario(usuario);
        abono.setTipoMovimiento(TipoMovimiento.ABONO_META);
        when(movimientoRepository.findById(1L)).thenReturn(Optional.of(abono));

        mockMvc.perform(put("/api/movimientos/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Editado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString("deudas o metas")));
    }

    @Test
    @DisplayName("Registro duplicado por RUT → 409 USUARIO_YA_REGISTRADO con mensaje genérico")
    void usuarioYaRegistradoPorRut() throws Exception {
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rut":"12345678-5","nombre":"Ana","apellido":"Pérez",
                                 "email":"ana@example.com","contrasena":"Password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con esos datos."));
    }

    @Test
    @DisplayName("Registro duplicado por email → 409 USUARIO_YA_REGISTRADO con el mismo mensaje")
    void usuarioYaRegistradoPorEmail() throws Exception {
        when(usuarioRepository.existsById(RUT)).thenReturn(false);
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rut":"12345678-5","nombre":"Ana","apellido":"Pérez",
                                 "email":"ana@example.com","contrasena":"Password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con esos datos."));
    }

    @Test
    @DisplayName("GET /api/presupuestos/me sin configuración → 404 NO_ENCONTRADO")
    void presupuestoSinConfiguracion() throws Exception {
        mockMvc.perform(get("/api/presupuestos/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Excepción inesperada → 500 ERROR_INTERNO con idError y sin mensaje interno")
    void errorInterno() throws Exception {
        when(categoriaService.obtenerTodas()).thenThrow(new RuntimeException("secreto interno"));

        mockMvc.perform(get("/api/categorias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.idError").exists())
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @Test
    @DisplayName("Ruta inexistente → 404 NO_ENCONTRADO en problem+json")
    void rutaInexistente() throws Exception {
        mockMvc.perform(get("/api/ruta-que-no-existe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Método no permitido → 405 con SOLICITUD_INVALIDA")
    void metodoNoPermitido() throws Exception {
        mockMvc.perform(delete("/api/categorias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("Content-Type no soportado → 415 con SOLICITUD_INVALIDA")
    void tipoContenidoNoSoportado() throws Exception {
        mockMvc.perform(post("/api/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
