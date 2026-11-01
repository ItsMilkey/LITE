package com.example.saveup.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el esquema REAL sobre PostgreSQL: aplica todas las migraciones de
 * Flyway en una base vacía y deja que Hibernate valide sus entidades contra el
 * resultado ({@code ddl-auto=validate}).
 *
 * <p>Motivo: la suite normal corre sobre H2 con {@code ddl-auto=create-drop} y
 * {@code spring.flyway.enabled=false}, así que las migraciones nunca se
 * ejecutan. Un desajuste entre una migración y una entidad (por ejemplo, el
 * nombre de la PK en V9/V11) solo se descubría al desplegar. Este test
 * reproduce el arranque de producción antes de publicar.
 *
 * <p>Se etiqueta como {@code postgres} y queda excluido de {@code mvn test}
 * para no exigir Docker en el ciclo local. CI lo ejecuta con
 * {@code -Ppostgres}; en local:
 * <pre>
 * .\mvnw.cmd test -Ppostgres -Dtest=MigracionesPostgresTest
 * </pre>
 */
@Testcontainers
@Tag("postgres")
@SpringBootTest(properties = {
        // Sustituyen la configuración H2 de src/test/resources/application.properties.
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        // Igual que prod: Flyway crea el esquema y Hibernate solo lo valida.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.validate-on-migrate=true",
        // Sin proveedores externos: el contexto debe arrancar sin credenciales.
        "app.mail.enabled=false",
        "app.push.enabled=false",
        "app.push.habilitado=false"
})
class MigracionesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway aplica todas las migraciones y Hibernate valida el esquema resultante")
    void aplicaTodasLasMigracionesYValidaElEsquema() {
        // Si Hibernate detectó un desajuste, el contexto no habría arrancado:
        // llegar aquí ya prueba que entidades y migraciones son compatibles.
        MigrationInfo[] migraciones = flyway.info().all();
        assertTrue(migraciones.length > 0, "Flyway no encontró migraciones");
        assertEquals(0, flyway.info().pending().length, "Quedaron migraciones pendientes");

        for (MigrationInfo migracion : migraciones) {
            assertEquals(MigrationState.SUCCESS, migracion.getState(),
                    () -> "Migración no aplicada: " + migracion.getVersion());
        }

        MigrationInfo actual = flyway.info().current();
        assertNotNull(actual, "Flyway no reporta versión actual");
        assertEquals(migraciones[migraciones.length - 1].getVersion(), actual.getVersion(),
                "La versión actual no es la última migración disponible");
    }

    @Test
    @DisplayName("A01/A03: el catálogo tiene 18 categorías con código único y los tipos finales")
    void catalogoT10aTiene18CategoriasConCodigoUnico() {
        assertEquals(18, jdbc.queryForObject("SELECT count(*) FROM categoria", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM categoria WHERE codigo IS NULL", Integer.class));

        List<String> codigos = jdbc.queryForList(
                "SELECT codigo FROM categoria ORDER BY codigo", String.class);
        assertEquals(18, new HashSet<>(codigos).size(), "Hay códigos repetidos: " + codigos);
        assertTrue(codigos.containsAll(List.of(
                "SUELDO", "ALIMENTACION_BASE", "TRANSPORTE_MOVILIDAD", "VIVIENDA_SUMINISTROS",
                "SALUD_BIENESTAR", "OCIO_RECREACION", "CUIDADO_PERSONAL_VESTUARIO", "DEUDAS",
                "AHORRO", "OTROS", "EDUCACION_FORMACION", "SEGUROS_IMPUESTOS", "MASCOTAS",
                "HOGAR_CONSUMIBLES", "EQUIPAMIENTO_TECNOLOGIA", "SUSCRIPCIONES_DIGITAL",
                "RESTAURANTES_SALIDAS", "REGALOS_CELEBRACIONES")),
                "Faltan códigos del catálogo: " + codigos);

        assertEquals("DESEO", jdbc.queryForObject(
                "SELECT tipo_presupuesto FROM categoria WHERE codigo = 'CUIDADO_PERSONAL_VESTUARIO'", String.class));
        assertEquals("NECESIDAD", jdbc.queryForObject(
                "SELECT tipo_presupuesto FROM categoria WHERE codigo = 'DEUDAS'", String.class));
        assertEquals("AHORRO", jdbc.queryForObject(
                "SELECT tipo_presupuesto FROM categoria WHERE codigo = 'AHORRO'", String.class));
        assertEquals("OTROS", jdbc.queryForObject(
                "SELECT tipo_presupuesto FROM categoria WHERE codigo = 'OTROS'", String.class));
    }

    @Test
    @DisplayName("A04: código repetido o en minúsculas falla por constraint")
    void codigoRepetidoOMinusculasFalla() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO categoria (nombre, codigo, tipo_presupuesto) VALUES ('Duplicada', 'DEUDAS', 'NECESIDAD')"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO categoria (nombre, codigo, tipo_presupuesto) VALUES ('Minusculas', 'minusculas', 'NECESIDAD')"));
    }

    @Test
    @DisplayName("Todas las tablas de dominio tienen RLS activado (Supabase expone el esquema public)")
    void todasLasTablasDeDominioTienenRls() {
        // flyway_schema_history queda fuera: su RLS se activa a mano fuera de Flyway.
        List<String> sinRls = jdbc.queryForList("""
                SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public'
                   AND c.relkind = 'r'
                   AND c.relname <> 'flyway_schema_history'
                   AND NOT c.relrowsecurity
                 ORDER BY c.relname
                """, String.class);

        assertTrue(sinRls.isEmpty(), "Tablas sin RLS: " + sinRls);
    }
}
