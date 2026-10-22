-- ============================================================
-- V10__cascada_usuario.sql  |  PostgreSQL (Supabase/Render)
-- T9.2: borrado de cuenta desde la app (DELETE /api/v1/usuarios/me).
-- ============================================================
-- Las FKs hacia USUARIO quedaban en NO ACTION: no había borrado de cuenta.
-- Con esta migración, eliminar un usuario elimina en cascada sus metas,
-- deudas, movimientos, presupuestos, límites por categoría y tokens de
-- sesión, como exigen las políticas de Apple y Google.
--
-- Notas de diseño:
--   * Solo cascada en FKs que apuntan a USUARIO (hijos directos de la
--     cuenta). Las FKs entre hijos (movimiento→deuda, movimiento→meta,
--     asignación→config/meta) siguen en NO ACTION: el orden seguro lo fija
--     EliminacionDeCuentaService (hijo→padre); cascada ahí borraría, por
--     ejemplo, el historial de pagos si mañana se eliminara una deuda.
--   * Los pagos de deuda son movimientos (V5), así que quedan cubiertos por
--     fk_mov_usuario.
--   * Idempotente: DROP CONSTRAINT IF EXISTS reproduce el estado exacto
--     aunque la BD haya migrado con otro orden. RLS ya activado en V8/V9.
-- ============================================================

ALTER TABLE meta_ahorro DROP CONSTRAINT IF EXISTS fk_meta_usuario;
ALTER TABLE meta_ahorro
    ADD CONSTRAINT fk_meta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id_usuario) ON DELETE CASCADE;

ALTER TABLE deuda DROP CONSTRAINT IF EXISTS fk_deuda_usuario;
ALTER TABLE deuda
    ADD CONSTRAINT fk_deuda_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id_usuario) ON DELETE CASCADE;

ALTER TABLE movimiento DROP CONSTRAINT IF EXISTS fk_mov_usuario;
ALTER TABLE movimiento
    ADD CONSTRAINT fk_mov_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id_usuario) ON DELETE CASCADE;

ALTER TABLE configuracion_presupuesto DROP CONSTRAINT IF EXISTS fk_conf_usuario;
ALTER TABLE configuracion_presupuesto
    ADD CONSTRAINT fk_conf_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id_usuario) ON DELETE CASCADE;

ALTER TABLE presupuesto_categoria DROP CONSTRAINT IF EXISTS fk_presup_cat_usuario;
ALTER TABLE presupuesto_categoria
    ADD CONSTRAINT fk_presup_cat_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id_usuario) ON DELETE CASCADE;

ALTER TABLE auth_refresh_token DROP CONSTRAINT IF EXISTS fk_auth_rt_usuario;
ALTER TABLE auth_refresh_token
    ADD CONSTRAINT fk_auth_rt_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id_usuario) ON DELETE CASCADE;
