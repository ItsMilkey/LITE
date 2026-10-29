package com.example.saveup.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * En producción el contrato no se expone: el cliente consume docs/openapi.json.
 * Se usa H2 solo para levantar el contexto sin depender de las variables de Supabase.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi-prod;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=c2F2ZXVwLWxpdGUtdGVzdC1zZWNyZXQtMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expirationInMs=86400000"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class OpenApiProdDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("En prod, la documentación exige autenticación y no está expuesta")
    void documentacionNoExpuestaEnProd() throws Exception {
        // Sin token: la documentación no es pública en prod.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());

        // Autenticado: springdoc está deshabilitado, así que las rutas no existen.
        mockMvc.perform(get("/v3/api-docs").with(user("prod-user")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html").with(user("prod-user")))
                .andExpect(status().isNotFound());
    }
}
