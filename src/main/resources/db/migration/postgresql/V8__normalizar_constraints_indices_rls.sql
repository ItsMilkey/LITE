-- V8: deja el esquema coherente y seguro.
-- 1) Revierte V7: nombres de constraint en minúscula (PostgreSQL pliega los identificadores sin comillas).
-- 2) Valida el CHECK de signo de MOVIMIENTO (la tabla estaba vacía al revisar; si hubiera filas inválidas, esta sentencia falla y lo hace visible).
-- 3) Índices en las columnas de filtro y claves foráneas más usadas (PostgreSQL no indexa las FK automáticamente).
-- 4) RLS en todas las tablas del esquema public (Supabase las expone por API). El backend conecta con el rol postgres, que lo ignora.

-- 1) Nombres en minúscula
ALTER TABLE deuda RENAME CONSTRAINT "CHK_CUOTAS_PAGADAS_PREVIAS" TO chk_cuotas_pagadas_previas;
ALTER TABLE deuda RENAME CONSTRAINT "CHK_MODALIDAD_CALCULO" TO chk_modalidad_calculo;
ALTER TABLE deuda RENAME CONSTRAINT "CHK_TIPO_DEUDA" TO chk_tipo_deuda;
ALTER TABLE movimiento RENAME CONSTRAINT "CHK_MOVIMIENTO_SIGNO" TO chk_movimiento_signo;
ALTER TABLE movimiento RENAME CONSTRAINT "FK_MOVIMIENTO_ORIGEN" TO fk_movimiento_origen;
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT "CHK_PRESUP_CAT_LIMITE" TO chk_presup_cat_limite;
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT "FK_PRESUP_CAT_CATEGORIA" TO fk_presup_cat_categoria;
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT "FK_PRESUP_CAT_USUARIO" TO fk_presup_cat_usuario;
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT "PK_PRESUPUESTO_CATEGORIA" TO pk_presupuesto_categoria;
ALTER TABLE presupuesto_categoria RENAME CONSTRAINT "UQ_PRESUP_CAT_USUARIO_CAT" TO uq_presup_cat_usuario_cat;

-- 2) Validar el CHECK de signo
ALTER TABLE movimiento VALIDATE CONSTRAINT chk_movimiento_signo;

-- 3) Índices
CREATE INDEX IF NOT EXISTS idx_movimiento_usuario_fecha ON movimiento (usuario_id, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_movimiento_deuda ON movimiento (deuda_id);
CREATE INDEX IF NOT EXISTS idx_movimiento_meta ON movimiento (meta_id);
CREATE INDEX IF NOT EXISTS idx_movimiento_categoria ON movimiento (categoria_id);
CREATE INDEX IF NOT EXISTS idx_deuda_usuario ON deuda (usuario_id);
CREATE INDEX IF NOT EXISTS idx_meta_usuario ON meta_ahorro (usuario_id);

-- 4) RLS (idempotente)
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE meta_ahorro ENABLE ROW LEVEL SECURITY;
ALTER TABLE deuda ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE configuracion_presupuesto ENABLE ROW LEVEL SECURITY;
ALTER TABLE asignacion_meta_presupuesto ENABLE ROW LEVEL SECURITY;
ALTER TABLE presupuesto_categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;
