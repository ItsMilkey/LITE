# Migraciones Flyway (Activation: Glob  **/db/migration/**)
- Motor único: PostgreSQL (Supabase, session pooler, SSL). Scripts en src/main/resources/db/migration/postgresql.
- Nombre: V{n}__descripcion_snake_case.sql. Calcula n mirando el último script existente.
- Nunca modifiques un script ya aplicado (Flyway valida checksums).
- Agregar columnas: ALTER TABLE t ADD COLUMN col tipo. Dinero NUMERIC(19,2); enteros INTEGER; booleanos BOOLEAN.
- Constraints con nombre: PK_*, UQ_*, FK_*, CHK_*. Sobre tablas con datos antiguos, agrega el CHECK con NOT VALID y valídalo después.
- Columna NOT NULL sobre tabla con filas: usa DEFAULT, o agrégala nullable, haz backfill y luego SET NOT NULL.
- Datos semilla idempotentes (ON CONFLICT DO NOTHING).
- Toda tabla nueva lleva ENABLE ROW LEVEL SECURITY (Supabase expone public por API).
