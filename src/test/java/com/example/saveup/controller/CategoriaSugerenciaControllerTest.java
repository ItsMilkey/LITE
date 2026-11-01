package com.example.saveup.controller;

import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.security.JwtService;
import com.example.saveup.service.categoria.CatalogoDePrueba;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T10b — Contrato HTTP de GET /api/v1/categorias/sugerencia. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CategoriaSugerenciaControllerTest {

    private static final String RUT = "11111111-1";
    private static final String URL = "/api/v1/categorias/sugerencia";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private CategoriaRepository categoriaRepository;

    private String token;

    @BeforeEach
    void setUp() {
        lenient().when(categoriaRepository.findAll()).thenReturn(CatalogoDePrueba.categorias());
        token = jwtService.generarTokenConExpiracion(RUT, "test@example.com").token();
    }

    @Test
    @DisplayName("U01: texto de 2 caracteres → 200 con lista vacía")
    void u01_dosCaracteres() throws Exception {
        mockMvc.perform(get(URL).param("texto", "ab").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("ab"))
                .andExpect(jsonPath("$.sugerencias").isEmpty())
                .andExpect(jsonPath("$.ambigua").value(false));
    }

    @Test
    @DisplayName("U02: texto sin coincidencias → 200 con lista vacía")
    void u02_sinCoincidencias() throws Exception {
        mockMvc.perform(get(URL).param("texto", "xyzqwerty").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias").isEmpty());
    }

    @Test
    @DisplayName("U03: frase sin coincidencia exacta → 200 con lista vacía")
    void u03_fraseSinCoincidencia() throws Exception {
        mockMvc.perform(get(URL).param("texto", "gasto personal libre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias").isEmpty());
    }

    @Test
    @DisplayName("U04: texto de 81 caracteres → 400 PARAMETRO_INVALIDO")
    void u04_textoDemasiadoLargo() throws Exception {
        mockMvc.perform(get(URL).param("texto", "a".repeat(81))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("80 caracteres exactos se aceptan")
    void ochentaCaracteresSeAceptan() throws Exception {
        mockMvc.perform(get(URL).param("texto", "a".repeat(80))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias").isEmpty());
    }

    @Test
    @DisplayName("U05: sin parámetro texto → 400 PARAMETRO_INVALIDO")
    void u05_sinTexto() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("U06: contexto desconocido → 400 PARAMETRO_INVALIDO")
    void u06_contextoInvalido() throws Exception {
        mockMvc.perform(get(URL).param("texto", "supermercado").param("contexto", "XYZ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("U17: sin token → 401 problem+json")
    void u17_sinToken() throws Exception {
        mockMvc.perform(get(URL).param("texto", "supermercado"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("U18: el log de la llamada no contiene el texto enviado")
    void u18_noRegistraElTexto(CapturedOutput salida) throws Exception {
        String texto = "zztextosecretozz";

        mockMvc.perform(get(URL).param("texto", texto).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertFalse(salida.getAll().contains(texto),
                "El texto del usuario no debe aparecer en los logs (G9)");
    }

    @Test
    @DisplayName("La respuesta devuelve el texto original exacto, con espacios y mayúsculas")
    void ecoDelTextoOriginal() throws Exception {
        mockMvc.perform(get(URL).param("texto", "  Netflx  ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("  Netflx  "))
                .andExpect(jsonPath("$.sugerencias[0].codigo").value("SUSCRIPCIONES_DIGITAL"))
                .andExpect(jsonPath("$.sugerencias[0].motivo").value("APROXIMADA"));
    }

    @Test
    @DisplayName("Sin contexto se asume GASTO (SUELDO excluido)")
    void contextoPorDefectoGasto() throws Exception {
        mockMvc.perform(get(URL).param("texto", "sueldo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias").isEmpty());
    }

    @Test
    @DisplayName("contexto=INGRESO permite SUELDO")
    void contextoIngreso() throws Exception {
        mockMvc.perform(get(URL).param("texto", "sueldo").param("contexto", "INGRESO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias[0].codigo").value("SUELDO"))
                .andExpect(jsonPath("$.sugerencias[0].confianza").value(0.90));
    }

    @Test
    @DisplayName("G10: comida devuelve dos sugerencias y ambigua=true")
    void g10_comidaAmbigua() throws Exception {
        mockMvc.perform(get(URL).param("texto", "comida").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias.length()").value(2))
                .andExpect(jsonPath("$.sugerencias[0].codigo").value("ALIMENTACION_BASE"))
                .andExpect(jsonPath("$.sugerencias[1].codigo").value("RESTAURANTES_SALIDAS"))
                .andExpect(jsonPath("$.ambigua").value(true));
    }
}
