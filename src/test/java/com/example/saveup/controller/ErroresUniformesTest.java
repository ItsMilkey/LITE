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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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

    private static ResultMatcher problemJson() {
        return content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("401 sin token → NO_AUTENTICADO en problem+json")
    void sinToken_noAutenticado() throws Exception {
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail").value("No se pudo autenticar la solicitud."))
                .andExpect(jsonPath("$.instance").value("/api/v1/categorias"));
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

        mockMvc.perform(get("/api/v1/categorias")
                        .header("Authorization", "Bearer " + expirado))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("TOKEN_EXPIRADO"))
                .andExpect(jsonPath("$.detail").value("Tu sesión ha expirado. Inicia sesión nuevamente."));
    }

    @Test
    @DisplayName("401 con email inexistente → CREDENCIALES_INVALIDAS con mensaje genérico")
    void credencialesInvalidas() throws Exception {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@example.com","contrasena":"Password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value("El correo o la contraseña no son correctos."));
    }

    @Test
    @DisplayName("Validación con dos campos inválidos → errores[] con ambos y nombres JSON")
    void validacionDosCampos() throws Exception {
        mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","condiciones":{"modalidad":"SIN_INTERES","montoCapital":0,"cantidadCuotas":10}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores[*].campo",
                        hasItems("nombre", "condiciones.montoCapital")));
    }

    @Test
    @DisplayName("JSON malformado → 400 SOLICITUD_INVALIDA")
    void jsonMalformado_solicitudInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("Parámetro con tipo incorrecto → 400 PARAMETRO_INVALIDO")
    void parametroInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos/ejecucion/me")
                        .param("month", "abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("Regla del motor financiero → 400 DATOS_INVALIDOS con mensaje legible")
    void datosInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/simulaciones/credito")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modalidad":"SIN_INTERES","montoCapital":100000,"cantidadCuotas":12,
                                 "fechaUltimaCuota":"2027-10-07"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("Deuda inexistente → 404 NO_ENCONTRADO")
    void noEncontrado() throws Exception {
        when(deudaRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deudas/77")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(problemJson())
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

        mockMvc.perform(get("/api/v1/deudas/78")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(problemJson())
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

        mockMvc.perform(put("/api/v1/movimientos/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Editado\"}"))
                .andExpect(status().isConflict())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString("deudas o metas")));
    }

    @Test
    @DisplayName("Registro duplicado por RUT → 409 USUARIO_YA_REGISTRADO con mensaje genérico")
    void usuarioYaRegistradoPorRut() throws Exception {
        when(usuarioRepository.existsById(RUT)).thenReturn(true);

        mockMvc.perform(post("/api/v1/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rut":"12345678-5","nombre":"Ana","apellido":"Pérez",
                                 "email":"ana@example.com","contrasena":"Password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con esos datos."));
    }

    @Test
    @DisplayName("Registro duplicado por email → 409 USUARIO_YA_REGISTRADO con el mismo mensaje")
    void usuarioYaRegistradoPorEmail() throws Exception {
        when(usuarioRepository.existsById(RUT)).thenReturn(false);
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/v1/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rut":"12345678-5","nombre":"Ana","apellido":"Pérez",
                                 "email":"ana@example.com","contrasena":"Password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con esos datos."));
    }

    @Test
    @DisplayName("GET /api/v1/presupuestos/me sin configuración → 404 NO_ENCONTRADO")
    void presupuestoSinConfiguracion() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(problemJson());
    }

    @Test
    @DisplayName("Excepción inesperada → 500 ERROR_INTERNO con idError y sin mensaje interno")
    void errorInterno() throws Exception {
        when(categoriaService.obtenerTodas()).thenThrow(new RuntimeException("secreto interno"));

        mockMvc.perform(get("/api/v1/categorias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.idError").exists())
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @Test
    @DisplayName("Ruta inexistente → 404 NO_ENCONTRADO en problem+json")
    void rutaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-que-no-existe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Método no permitido → 405 con SOLICITUD_INVALIDA")
    void metodoNoPermitido() throws Exception {
        mockMvc.perform(delete("/api/v1/categorias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("Content-Type no soportado → 415 con SOLICITUD_INVALIDA")
    void tipoContenidoNoSoportado() throws Exception {
        mockMvc.perform(post("/api/v1/deudas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("Contraseña incorrecta → mismo CREDENCIALES_INVALIDAS que email inexistente")
    void credencialesInvalidas_passwordIncorrecta() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setEmail("test@example.com");
        usuario.setContrasena(new BCryptPasswordEncoder().encode("Correcta123"));
        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(usuario));

        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","contrasena":"Incorrecta123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value("El correo o la contraseña no son correctos."));
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409 sin mensaje interno")
    void dataIntegrityViolation_conflictoSinMensajeInterno() throws Exception {
        when(categoriaService.obtenerTodas())
                .thenThrow(new DataIntegrityViolationException("detalle interno SQL"));

        mockMvc.perform(get("/api/v1/categorias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail").value("La operación no se puede completar por una regla de negocio."))
                .andExpect(content().string(not(containsString("detalle interno SQL"))));
    }

    @Test
    @DisplayName("IllegalStateException interna → 500 sin mensaje y con idError también en el log")
    void illegalStateEsErrorInterno(CapturedOutput output) throws Exception {
        when(categoriaService.obtenerTodas())
                .thenThrow(new IllegalStateException("invariante interna"));

        MvcResult result = mockMvc.perform(get("/api/v1/categorias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.idError").exists())
                .andExpect(content().string(not(containsString("invariante interna"))))
                .andReturn();

        String idError = JsonPath.read(result.getResponse().getContentAsString(), "$.idError");
        assertTrue(output.getAll().contains("idError=" + idError),
                "El log del 500 debe incluir el mismo idError que la respuesta");
    }

    @Test
    @DisplayName("Enum desconocido en el cuerpo JSON → 400 SOLICITUD_INVALIDA")
    void enumDesconocidoEnBody_solicitudInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":1000,"descripcion":"x","tipoMovimiento":"NO_EXISTE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(content().string(not(containsString("TipoMovimiento"))));
    }

    @Test
    @DisplayName("Parámetro obligatorio ausente → 400 PARAMETRO_INVALIDO")
    void parametroFaltante_parametroInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos/historial/me")
                        .param("hasta", "2026-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("Fecha o enum inválido en @ModelAttribute → PARAMETRO_INVALIDO sin mensajes de Spring")
    void bindingInvalidoEnFiltros_parametroInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("desde", "abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("desde"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El valor no tiene el formato esperado"));

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("tipoMovimiento", "FOO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("tipoMovimiento"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El valor no tiene el formato esperado"));
    }

    @Test
    @DisplayName("Paginación fuera de rango → 400 DATOS_INVALIDOS con mensaje en español")
    void paginacionInvalida_datosInvalidos() throws Exception {
        String detalle = "Los parámetros de paginación no son válidos: page debe ser 0 o mayor "
                + "y size debe estar entre 1 y 100.";

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("page", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value(detalle));

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value(detalle));

        mockMvc.perform(get("/api/v1/movimientos/paginados/me")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value(detalle));
    }

    @Test
    @DisplayName("Pagos de deuda usan la misma validación de paginación")
    void paginacionInvalidaEnPagosDeuda_datosInvalidos() throws Exception {
        mockMvc.perform(get("/api/v1/deudas/1/pagos")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value(
                        "Los parámetros de paginación no son válidos: page debe ser 0 o mayor "
                                + "y size debe estar entre 1 y 100."));
    }

    @Test
    @DisplayName("Reporte mensual con mes inválido → 400 DATOS_INVALIDOS")
    void reporteMesInvalido_datosInvalidos() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/movimientos/exportar")
                        .param("alcance", "MENSUAL")
                        .param("mes", "13")
                        .param("anio", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value("El mes debe estar entre 1 y 12."));
    }

    @Test
    @DisplayName("Reporte mensual con año fuera de rango → 400 DATOS_INVALIDOS")
    void reporteAnioInvalido_datosInvalidos() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/movimientos/exportar")
                        .param("alcance", "MENSUAL")
                        .param("mes", "1")
                        .param("anio", "1999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value("El año debe estar entre 2000 y 2100."));
    }

    @Test
    @DisplayName("Accept: application/json conserva problem+json y $.codigo")
    void acceptJson_devuelveProblemJson() throws Exception {
        when(deudaRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deudas/77")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Accept: */* conserva problem+json y $.codigo")
    void acceptWildcard_devuelveProblemJson() throws Exception {
        when(deudaRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deudas/77")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", MediaType.ALL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Los 4xx registran INFO con status, código, método y path sin query")
    void error4xxRegistraInfoSinQuery(CapturedOutput output) throws Exception {
        when(deudaRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deudas/77")
                        .param("secreto", "no")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        assertTrue(output.getAll().contains(
                        "Error 4xx status=404 codigo=NO_ENCONTRADO metodo=GET path=/api/v1/deudas/77"),
                "El log 4xx debe incluir status, código, método y path");
        assertFalse(output.getAll().contains("/api/v1/deudas/77?secreto=no"),
                "El log no debe incluir el query string");
    }
}
