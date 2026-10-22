package com.example.saveup.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contratos de GET /api/v1/app/version (T9.4): estados de actualización por
 * SemVer con los umbrales del perfil de test (min=2.0.0, latest=2.4.0), y
 * acceso público sin token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppVersionControllerTest {

    private static final String RUTA = "/api/v1/app/version";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Versión por debajo del mínimo (1.9.9 < 2.0.0) → actualizacionObligatoria=true con enlace a la tienda")
    void versionMuyAntigua_actualizacionObligatoria() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "ANDROID")
                        .param("version", "1.9.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizacionObligatoria").value(true))
                .andExpect(jsonPath("$.actualizacionSugerida").value(false))
                .andExpect(jsonPath("$.mensaje").value(
                        "Tu versión ya no es compatible con SaveUp Lite. Actualiza la app desde la tienda para continuar."))
                .andExpect(jsonPath("$.urlTienda").value(
                        "https://play.google.com/store/apps/details?id=cl.saveup.lite"))
                .andExpect(jsonPath("$.versionTerminos").exists())
                .andExpect(jsonPath("$.versionPrivacidad").exists());
    }

    @Test
    @DisplayName("Versión entre el mínimo y la reciente (2.2.0) → actualizacionSugerida=true sin bloquear")
    void versionIntermedia_actualizacionSugerida() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "ANDROID")
                        .param("version", "2.2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizacionObligatoria").value(false))
                .andExpect(jsonPath("$.actualizacionSugerida").value(true))
                .andExpect(jsonPath("$.mensaje").value(
                        "Hay una nueva versión de SaveUp Lite disponible. Te recomendamos actualizarla."))
                .andExpect(jsonPath("$.urlTienda").exists());
    }

    @Test
    @DisplayName("Versión al día (2.4.0) e incluso mayor (3.0.0) → ambos flags en false")
    void versionAlDia_sinActualizaciones() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "ANDROID")
                        .param("version", "2.4.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizacionObligatoria").value(false))
                .andExpect(jsonPath("$.actualizacionSugerida").value(false))
                .andExpect(jsonPath("$.mensaje").value("Tu app está al día."));

        mockMvc.perform(get(RUTA)
                        .param("plataforma", "IOS")
                        .param("version", "3.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizacionObligatoria").value(false))
                .andExpect(jsonPath("$.actualizacionSugerida").value(false))
                .andExpect(jsonPath("$.urlTienda").value("https://apps.apple.com/app/saveup-lite"));
    }

    @Test
    @DisplayName("IOS usa sus propios umbrales: 1.9.9 en iOS también bloquea")
    void ios_umbralPorPlataforma() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "IOS")
                        .param("version", "1.9.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizacionObligatoria").value(true))
                .andExpect(jsonPath("$.actualizacionSugerida").value(false));
    }

    @Test
    @DisplayName("Plataforma desconocida (WINDOWS) → 400 PARAMETRO_INVALIDO")
    void plataformaInvalida_parametroInvalido() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "WINDOWS")
                        .param("version", "2.4.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("Versión mal formada (1.2) → 400 DATOS_INVALIDOS")
    void versionMalFormada_datosInvalidos() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "ANDROID")
                        .param("version", "1.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value(
                        containsString("La versión debe tener el formato MAJOR.MINOR.PATCH")));
    }

    @Test
    @DisplayName("Falta el parámetro version → 400 PARAMETRO_INVALIDO")
    void faltaParametroVersion() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "ANDROID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("Falta el parámetro plataforma → 400 PARAMETRO_INVALIDO")
    void faltaParametroPlataforma() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("version", "2.4.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("El endpoint es público: responde sin cabecera Authorization (no 401 NO_AUTENTICADO)")
    void accesoPublicoSinToken() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("plataforma", "IOS")
                        .param("version", "2.4.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizacionObligatoria").value(false))
                .andExpect(jsonPath("$.actualizacionSugerida").value(false));
    }
}
