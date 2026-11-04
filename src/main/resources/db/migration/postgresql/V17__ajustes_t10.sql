-- ============================================================
-- V17__ajustes_t10.sql  |  PostgreSQL
-- T10: ajustes surgidos del code review.
--   * grupo_plan.orden no puede ser negativo (CONTEXT: orden >= 0).
--   * chk_categoria_codigo se recrea con NOT VALID + VALIDATE para
--     cumplir la regla de migraciones sobre tablas con datos previos.
-- No modifica scripts anteriores.
-- ============================================================

-- 1) Orden de los grupos: nunca negativo.
ALTER TABLE grupo_plan
    ADD CONSTRAINT chk_grupo_orden CHECK (orden >= 0) NOT VALID;
ALTER TABLE grupo_plan VALIDATE CONSTRAINT chk_grupo_orden;

-- 2) El CHECK de formato de código ya existía (V13); se recrea con el
--    patrón NOT VALID + VALIDATE que exige la regla de migraciones.
ALTER TABLE categoria DROP CONSTRAINT chk_categoria_codigo;
ALTER TABLE categoria
    ADD CONSTRAINT chk_categoria_codigo CHECK (codigo ~ '^[A-Z][A-Z0-9_]*$') NOT VALID;
ALTER TABLE categoria VALIDATE CONSTRAINT chk_categoria_codigo;
