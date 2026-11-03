package com.example.saveup.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10c — C59 y C60 sobre postgresQL real: constraints de {@code V14},
 * RLS, índices y migración sobre datos existentes.
 *
 * <p>Etiqueta {@code postgres}: se ejecuta con {@code -Ppostgres}. Cada test
 * usa un contenedor nuevo para partir siempre de una base vacía.
 */
@Tag("postgres")
@Testcontainers
class PlanPostgresTest {

    @Container
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("C59: nombre duplicado, monto negativo, mes con día distinto de 1 y FK fallan en postgresQL")
    void c59_constraints() throws Exception {
        flywayHasta(null).migrate();

        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO usuario (id_usuario, nombre, apellido, email, contrasena_hash, fecha_registro)
                    VALUES ('11111111-1', 'Test', 'Plan', 'plan@example.com', 'hash', now())
                    """);
            long alimentacion = idDeCategoria(st, "ALIMENTACION_BASE");

            // Un plan válido: mes día 1.
            long plan = insertarPlan(st, "11111111-1", "2026-11-01");

            // mes con día distinto de 1 → chk_plan_mes_dia1
            assertThrows(SQLException.class,
                    () -> insertarPlan(st, "11111111-1", "2026-11-02"));

            // Partida válida y luego nombre normalizado duplicado en el mismo plan.
            insertarPartida(st, plan, alimentacion, "Super", "super", "1000.00");
            assertThrows(SQLException.class,
                    () -> insertarPartida(st, plan, alimentacion, "Súper", "super", "2000.00"));

            // monto negativo → chk_partida_monto
            assertThrows(SQLException.class,
                    () -> insertarPartida(st, plan, alimentacion, "Negativa", "negativa", "-1.00"));

            // FK de partida a un plan inexistente.
            assertThrows(SQLException.class,
                    () -> insertarPartida(st, 999_999L, alimentacion, "Fantasma", "fantasma", "1.00"));

            // FK de movimiento a una partida inexistente.
            assertThrows(SQLException.class, () -> st.executeUpdate("""
                    INSERT INTO movimiento (monto, descripcion, fecha, tipo_movimiento,
                                            usuario_id, categoria_id, partida_id)
                    VALUES (-1000.00, 'Fantasma', now(), 'GASTO_GENERAL',
                            '11111111-1', %d, 999999)
                    """.formatted(alimentacion)));
        }
    }

    @Test
    @DisplayName("C60: V14 conserva las filas existentes y deja RLS, índices y columnas nuevas correctos")
    void c60_migracionConservaDatosYActivaRls() throws Exception {
        flywayHasta("13").migrate();

        long idCategoria;
        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO usuario (id_usuario, nombre, apellido, email, contrasena_hash, fecha_registro)
                    VALUES ('22222222-2', 'Datos', 'Reales', 'datos@example.com', 'hash', now())
                    """);
            idCategoria = idDeCategoria(st, "ALIMENTACION_BASE");
            st.executeUpdate("""
                    INSERT INTO movimiento (monto, descripcion, fecha, tipo_movimiento,
                                            usuario_id, categoria_id)
                    VALUES (-4321.09, 'Compra histórica', now(), 'GASTO_GENERAL',
                            '22222222-2', %d)
                    """.formatted(idCategoria));
        }

        flywayHasta(null).migrate();

        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            try (ResultSet rs = st.executeQuery("""
                    SELECT monto, categoria_id, partida_id
                      FROM movimiento
                     WHERE usuario_id = '22222222-2' AND descripcion = 'Compra histórica'
                    """)) {
                assertTrue(rs.next(), "El movimiento histórico desapareció al aplicar V14");
                assertEquals(0, new BigDecimal("-4321.09").compareTo(rs.getBigDecimal("monto")));
                assertEquals(idCategoria, rs.getLong("categoria_id"));
                assertEquals(null, rs.getObject("partida_id", Long.class));
            }

            assertTrue(rlsActivo(st, "plan_mensual"), "plan_mensual debe tener RLS activo");
            assertTrue(rlsActivo(st, "partida_plan"), "partida_plan debe tener RLS activo");
            assertEquals(0, contar(st, """
                    SELECT count(*) FROM pg_policies
                     WHERE schemaname = 'public'
                       AND tablename IN ('plan_mensual', 'partida_plan')
                    """), "Las tablas nuevas no deben tener políticas RLS");

            Set<String> indices = new HashSet<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")) {
                while (rs.next()) {
                    indices.add(rs.getString(1));
                }
            }
            assertTrue(indices.containsAll(Set.of(
                    "idx_partida_plan", "idx_partida_categoria",
                    "idx_partida_nombre", "idx_movimiento_partida")),
                    "Faltan índices de T10c: " + indices);
        }
    }

    private Flyway flywayHasta(String version) {
        var configuracion = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgresql");
        if (version != null) {
            configuracion = configuracion.target(MigrationVersion.fromVersion(version));
        }
        return configuracion.load();
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private long insertarPlan(Statement st, String usuario, String mes) throws SQLException {
        try (ResultSet rs = st.executeQuery("""
                INSERT INTO plan_mensual (usuario_id, mes, creado_en, actualizado_en)
                VALUES ('%s', DATE '%s', now(), now())
                RETURNING id_plan_mensual
                """.formatted(usuario, mes))) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private void insertarPartida(Statement st, long planId, long categoriaId,
                                        String nombre, String normalizado, String monto)
            throws SQLException {
        st.executeUpdate("""
                INSERT INTO partida_plan (plan_id, categoria_id, nombre, nombre_normalizado,
                                          monto_planificado, orden, actualizado_en)
                VALUES (%d, %d, '%s', '%s', %s, 0, now())
                """.formatted(planId, categoriaId, nombre, normalizado, monto));
    }

    private long idDeCategoria(Statement st, String codigo) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT id_categoria FROM categoria WHERE codigo = '" + codigo + "'")) {
            assertTrue(rs.next(), "No existe la categoría " + codigo);
            return rs.getLong(1);
        }
    }

    private boolean rlsActivo(Statement st, String tabla) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT relrowsecurity FROM pg_class WHERE relname = '" + tabla + "'")) {
            assertTrue(rs.next(), "No existe la tabla " + tabla);
            return rs.getBoolean(1);
        }
    }

    private int contar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
