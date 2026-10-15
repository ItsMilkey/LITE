-- V5__movimiento_origen_id.sql  |  PostgreSQL (Supabase)
--
-- T4: vínculo trazable entre un ingreso y los ABONO_META que genera Smart-Split.
--
-- Antes, SmartSplitProcessor creaba los abonos automáticos sin dejar rastro del
-- INGRESO_GENERAL que los originó. Esta columna nullable (autoreferencia a
-- MOVIMIENTO) cierra esa brecha: un ingreso con movimientos derivados vinculados
-- no se puede editar ni borrar desde /api/movimientos (409), porque los abonos
-- dependen de él.
--
-- Nullable a propósito: los movimientos históricos (y todo movimiento que no
-- provenga de Smart-Split) quedan con movimiento_origen_id = NULL y, por lo
-- tanto, SIN la restricción de edición. Ver docs/CONTEXT.md (regla T4).

ALTER TABLE movimiento
    ADD COLUMN movimiento_origen_id BIGINT;

ALTER TABLE movimiento
    ADD CONSTRAINT FK_MOVIMIENTO_ORIGEN
    FOREIGN KEY (movimiento_origen_id) REFERENCES movimiento(id_movimiento);

CREATE INDEX IDX_MOVIMIENTO_ORIGEN_ID ON movimiento(movimiento_origen_id);
