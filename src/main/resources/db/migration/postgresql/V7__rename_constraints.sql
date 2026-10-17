-- V7__rename_constraints.sql  |  PostgreSQL (Supabase)
--
-- Estandariza los nombres de constraint con los prefijos EN MAYÚSCULAS que pide
-- .agents/rules/30-flyway.md (PK_*, UQ_*, FK_*, CHK_*).
--
-- OJO con PostgreSQL: los identificadores sin comillas se pliegan a minúsculas.
-- Por eso V3/V6 (escritos en minúsculas) y V4/V5 (CHK_*/FK_* sin comillas) tienen
-- hoy el mismo nombre almacenado: en minúsculas. Para que el nombre quede
-- realmente en mayúsculas hay que citarlo entre comillas dobles; sin comillas,
-- RENAME CONSTRAINT no cambiaría nada.
--
-- Los scripts V1..V6 ya están aplicados: no se editan (Flyway valida checksums).
-- Esta migración solo renombra constraints de las tablas creadas/alteradas en
-- V3..V6; no toca datos, columnas ni definiciones.

-- V3__deuda_condiciones_credito.sql
ALTER TABLE deuda RENAME CONSTRAINT chk_tipo_deuda TO "CHK_TIPO_DEUDA";
ALTER TABLE deuda RENAME CONSTRAINT chk_modalidad_calculo TO "CHK_MODALIDAD_CALCULO";
ALTER TABLE deuda RENAME CONSTRAINT chk_cuotas_pagadas_previas TO "CHK_CUOTAS_PAGADAS_PREVIAS";

-- V4__movimiento_check_signo.sql
ALTER TABLE movimiento RENAME CONSTRAINT chk_movimiento_signo TO "CHK_MOVIMIENTO_SIGNO";

-- V5__movimiento_origen_id.sql
ALTER TABLE movimiento RENAME CONSTRAINT fk_movimiento_origen TO "FK_MOVIMIENTO_ORIGEN";

-- V6__presupuesto_categoria.sql
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT pk_presupuesto_categoria TO "PK_PRESUPUESTO_CATEGORIA";
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT fk_presup_cat_usuario TO "FK_PRESUP_CAT_USUARIO";
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT fk_presup_cat_categoria TO "FK_PRESUP_CAT_CATEGORIA";
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT uq_presup_cat_usuario_cat TO "UQ_PRESUP_CAT_USUARIO_CAT";
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT chk_presup_cat_limite TO "CHK_PRESUP_CAT_LIMITE";
