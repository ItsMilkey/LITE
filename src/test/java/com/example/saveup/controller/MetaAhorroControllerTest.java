package com.example.saveup.controller;

import com.example.saveup.dto.MetaAhorroResponseDTO;
import com.example.saveup.security.JwtService;
import com.example.saveup.service.MetaAhorroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetaAhorroControllerTest {

    private static final String RUT = "12345678-5";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MetaAhorroService metaAhorroService;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtService.generateToken(RUT, "test@example.com");
    }

    @Test
    @DisplayName("POST /api/metas → 201 Created")
    void crearMeta_retorna201() throws Exception {
        MetaAhorroResponseDTO creada = new MetaAhorroResponseDTO();
        creada.setId(1L);
        creada.setNombre("Viaje");
        when(metaAhorroService.crearMeta(any())).thenReturn(creada);

        mockMvc.perform(post("/api/metas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Viaje","montoObjetivo":1000.00,"fechaLimite":"2099-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Viaje"));
    }

    @Test
    @DisplayName("DELETE /api/metas/{id} → 204 No Content")
    void eliminarMeta_retorna204() throws Exception {
        doNothing().when(metaAhorroService).eliminarMeta(1L);

        mockMvc.perform(delete("/api/metas/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
