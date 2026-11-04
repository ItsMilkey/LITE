package com.example.saveup.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10d (sesión 2) — D50 sobre PostgreSQL real: {@code V16} se aplica sobre
 * datos existentes sin perder filas y deja correctos el RLS, los índices, el
 * índice único parcial de deuda y las FK con ON DELETE SET NULL.
 *
 * <p>Etiqueta {@code postgres}: se ejecuta con {@code -Ppostgres} y requiere
 * Docker. La suite normal no la ejecuta.
 */
@Tag("postgres")
@Testcontainers
class GrupoDeudaPostgresTest {

    @Container
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("D50: V16 conserva las filas existentes y aplica RLS, índices y constraints")
    void d50_migracionGruposYDeudas() throws Exception {
        flywayHasta("15").migrate();

        long plan;
        long partida;
        long deuda;
        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO usuario (id_usuario, nombre, apellido, email, contrasena_hash, fecha_registro)
                    VALUES ('33333333-3', 'Datos', 'Reales', 'deudas@example.com', 'hash', now())
                    """);
            long categoria = idDeCategoria(st, "ALIMENTACION_BASE");
            deuda = insertarDeuda(st, "33333333-3");
            plan = insertarPlan(st, "33333333-3", "2026-11-01");
            partida = insertarPartida(st, plan, categoria, "Super", "super", "1000.00");
        }

        flywayHasta(null).migrate();

        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            // Las filas existentes siguen intactas y sin vínculos nuevos.
            try (ResultSet rs = st.executeQuery("""
                    SELECT nombre, monto_planificado, grupo_id, deuda_id
                      FROM partida_plan WHERE id_partida_plan = %d
                    """.formatted(partida))) {
                assertTrue(rs.next(), "La partida histórica desapareció al aplicar V16");
                assertEquals("Super", rs.getString("nombre"));
                assertEquals(0, new java.math.BigDecimal("1000.00")
                        .compareTo(rs.getBigDecimal("monto_planificado")));
                assertNull(rs.getObject("grupo_id", Long.class));
                assertNull(rs.getObject("deuda_id", Long.class));
            }

            assertTrue(rlsActivo(st, "grupo_plan"), "grupo_plan debe tener RLS activo");
            assertEquals(0, contar(st, """
                    SELECT count(*) FROM pg_policies
                     WHERE schemaname = 'public' AND tablename = 'grupo_plan'
                    """), "grupo_plan no debe tener políticas RLS");

            Set<String> indices = new HashSet<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")) {
                while (rs.next()) {
                    indices.add(rs.getString(1));
                }
            }
            assertTrue(indices.containsAll(Set.of(
                    "idx_grupo_usuario", "idx_partida_grupo", "idx_partida_deuda",
                    "uq_partida_plan_deuda")), "Faltan índices de T10d: " + indices);

            String definicion = definicionIndice(st, "uq_partida_plan_deuda");
            assertTrue(definicion.contains("UNIQUE"), definicion);
            assertTrue(definicion.contains("WHERE (deuda_id IS NOT NULL)"), definicion);

            // Nombre de grupo duplicado por usuario.
            long grupo = insertarGrupo(st, "33333333-3", "Fijos", "fijos");
            assertThrows(SQLException.class,
                    () -> insertarGrupo(st, "33333333-3", "fijos", "fijos"));

            // Borrar el grupo deja la partida sin grupo (ON DELETE SET NULL).
            st.executeUpdate("UPDATE partida_plan SET grupo_id = %d WHERE id_partida_plan = %d"
                    .formatted(grupo, partida));
            st.executeUpdate("DELETE FROM grupo_plan WHERE id_grupo_plan = " + grupo);
            assertNull(grupoDe(st, partida), "El grupo debería quedar NULL tras borrarlo");

            // Una sola partida por deuda y plan.
            long categoria = idDeCategoria(st, "ALIMENTACION_BASE");
            st.executeUpdate("UPDATE partida_plan SET deuda_id = %d WHERE id_partida_plan = %d"
                    .formatted(deuda, partida));
            assertThrows(SQLException.class,
                    () -> insertarPartidaConDeuda(st, plan, categoria, "Cuota 2", "cuota-2", "1.00", deuda));
            // El índice es parcial: una partida sin deuda no colisiona.
            insertarPartida(st, plan, categoria, "Cuota", "cuota", "1.00");

            // FK de deuda inexistente.
            assertThrows(SQLException.class,
                    () -> insertarPartidaConDeuda(st, plan, categoria, "Fantasma", "fantasma",
                            "1.00", 999_999L));

            // Borrar la deuda deja la partida sin deuda (ON DELETE SET NULL).
            st.executeUpdate("DELETE FROM deuda WHERE id_deuda = " + deuda);
            assertNull(deudaDe(st, partida), "La deuda debería quedar NULL tras borrarla");
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

    private long insertarPartida(Statement st, long planId, long categoriaId,
                                 String nombre, String normalizado, String monto) throws SQLException {
        try (ResultSet rs = st.executeQuery("""
                INSERT INTO partida_plan (plan_id, categoria_id, nombre, nombre_normalizado,
                                          monto_planificado, orden, actualizado_en)
                VALUES (%d, %d, '%s', '%s', %s, 0, now())
                RETURNING id_partida_plan
                """.formatted(planId, categoriaId, nombre, normalizado, monto))) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private void insertarPartidaConDeuda(Statement st, long planId, long categoriaId,
                                         String nombre, String normalizado, String monto,
                                         long deudaId) throws SQLException {
        st.executeUpdate("""
                INSERT INTO partida_plan (plan_id, categoria_id, nombre, nombre_normalizado,
                                          monto_planificado, orden, actualizado_en, deuda_id)
                VALUES (%d, %d, '%s', '%s', %s, 0, now(), %d)
                """.formatted(planId, categoriaId, nombre, normalizado, monto, deudaId));
    }

    /** Inserta una partida con deuda NULL (el índice parcial no la considera). */
    private long insertarDeudaNula(Statement st, long planId, long categoriaId,
                                   String nombre, String normalizado, String monto) throws SQLException {
        return insertarPartida(st, planId, categoriaId, nombre, normalizado, monto);
    }

    private long insertarDeuda(Statement st, String usuario) throws SQLException {
        try (ResultSet rs = st.executeQuery("""
                INSERT INTO deuda (nombre, monto_total, cantidad_cuotas, estado, fecha_creacion,
                                   usuario_id, tipo_deuda, modalidad_calculo, monto_capital,
                                   valor_cuota, costo_total_credito, fecha_primera_cuota)
                VALUES ('Crédito', 43000.00, 1, 'PENDIENTE', now(), '%s', 'OTRO', 'SIN_INTERES',
                        43000.00, 43000.00, 43000.00, DATE '2026-11-05')
                RETURNING id_deuda
                """.formatted(usuario))) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private long insertarGrupo(Statement st, String usuario, String nombre, String normalizado)
            throws SQLException {
        try (ResultSet rs = st.executeQuery("""
                INSERT INTO grupo_plan (usuario_id, nombre, nombre_normalizado, orden)
                VALUES ('%s', '%s', '%s', 0)
                RETURNING id_grupo_plan
                """.formatted(usuario, nombre, normalizado))) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private Long grupoDe(Statement st, long partidaId) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT grupo_id FROM partida_plan WHERE id_partida_plan = " + partidaId)) {
            assertTrue(rs.next());
            return rs.getObject(1, Long.class);
        }
    }

    private Long deudaDe(Statement st, long partidaId) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT deuda_id FROM partida_plan WHERE id_partida_plan = " + partidaId)) {
            assertTrue(rs.next());
            return rs.getObject(1, Long.class);
        }
    }

    private String definicionIndice(Statement st, String indice) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                        + indice + "'")) {
            assertTrue(rs.next(), "No existe el índice " + indice);
            return rs.getString(1);
        }
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
