package com.example.saveup.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "jwt.secret=test_secret_para_openapi_prod_123456789",
        "jwt.expirationInMs=86400000"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class OpenApiProdDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("En prod, /v3/api-docs y Swagger UI no están expuestos")
    void documentacionNoExpuestaEnProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
