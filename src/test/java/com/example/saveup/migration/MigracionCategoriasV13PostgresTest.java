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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T10a — A02/A16: aplica V1–V12, siembra datos con el esquema antiguo (una
 * categoría y un movimiento que la referencia) y luego aplica V13. Comprueba
 * que los ids se conservan, los nombres se actualizan, el movimiento sigue
 * apuntando a su categoría y el RLS permanece activo.
 *
 * <p>Etiqueta {@code postgres}: se ejecuta con {@code -Ppostgres}, igual que
 * {@link MigracionesPostgresTest}.
 */
@Tag("postgres")
@Testcontainers
class MigracionCategoriasV13PostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("A02/A16: base con datos antiguos migra sin perder ids ni movimientos y conserva RLS")
    void migraBaseConDatosConservandoIdsYMovimientos() throws Exception {
        flywayHasta("12").migrate();

        long idComida;
        long idMovimiento;
        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO usuario (id_usuario, nombre, apellido, email, contrasena_hash, fecha_registro)
                    VALUES ('11111111-1', 'Test', 'Migración', 'migracion@example.com', 'hash', now())
                    """);
            idComida = idDeCategoria(st, "Comida");
            st.executeUpdate("""
                    INSERT INTO movimiento (monto, descripcion, fecha, tipo_movimiento, usuario_id, categoria_id)
                    VALUES (-1000.00, 'Compra antigua', now(), 'GASTO_GENERAL', '11111111-1', %d)
                    """.formatted(idComida));
            idMovimiento = ultimoMovimiento(st);
        }

        flywayHasta(null).migrate();

        try (Connection conexion = conectar(); Statement st = conexion.createStatement()) {
            assertEquals(18, contar(st, "SELECT count(*) FROM categoria"));
            assertEquals(0, contar(st, "SELECT count(*) FROM categoria WHERE codigo IS NULL"));

            assertEquals("Alimentación Base", nombreDeCategoria(st, idComida));
            assertEquals("Pago de Deudas", nombreDeCategoriaPorCodigo(st, "DEUDAS"));
            assertEquals("Ahorro y Fondo de Reserva", nombreDeCategoriaPorCodigo(st, "AHORRO"));
            assertEquals("Otros", nombreDeCategoriaPorCodigo(st, "OTROS"));

            try (ResultSet rs = st.executeQuery(
                    "SELECT categoria_id FROM movimiento WHERE id_movimiento = " + idMovimiento)) {
                assertTrue(rs.next(), "El movimiento antiguo desapareció");
                assertEquals(idComida, rs.getLong(1), "El movimiento cambió de categoría");
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT relrowsecurity FROM pg_class WHERE relname = 'categoria'")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1), "El RLS de categoria quedó desactivado");
            }
        }
    }

    private Flyway flywayHasta(String version) {
        var configuracion = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql");
        if (version != null) {
            configuracion = configuracion.target(MigrationVersion.fromVersion(version));
        }
        return configuracion.load();
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long idDeCategoria(Statement st, String nombre) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT id_categoria FROM categoria WHERE nombre = '" + nombre + "'")) {
            assertTrue(rs.next(), "No existe la categoría " + nombre);
            return rs.getLong(1);
        }
    }

    private long ultimoMovimiento(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT max(id_movimiento) FROM movimiento")) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private int contar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private String nombreDeCategoria(Statement st, long id) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT nombre FROM categoria WHERE id_categoria = " + id)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private String nombreDeCategoriaPorCodigo(Statement st, String codigo) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT nombre FROM categoria WHERE codigo = '" + codigo + "'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
