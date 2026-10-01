-- ============================================================
-- V2__seed_categories.sql  |  PostgreSQL (Render - Produccion)
-- Inserta las 10 categorias iniciales de forma idempotente.
-- ON CONFLICT (nombre) DO NOTHING aprovecha la restriccion
-- UNIQUE de la columna para evitar duplicados sin condiciones
-- adicionales.
-- ============================================================

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Sueldo', 'ic_work', '#009688', 'OTROS')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Comida', 'ic_fastfood', '#FFC107', 'NECESIDAD')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Transporte', 'ic_directions_car', '#2196F3', 'NECESIDAD')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Cuentas y Servicios', 'ic_receipt_long', '#4CAF50', 'NECESIDAD')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Ocio y Entretenimiento', 'ic_sports_esports', '#E91E63', 'DESEO')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Salud y Bienestar', 'ic_medical_services', '#F44336', 'NECESIDAD')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Ropa y Accesorios', 'ic_checkroom', '#9C27B0', 'DESEO')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Deudas', 'ic_payment', '#795548', 'NECESIDAD')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Ahorro', 'ic_savings', '#3F51B5', 'AHORRO')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria (nombre, icon_id, color_hex, tipo_presupuesto)
VALUES ('Otro', 'ic_label', '#607D8B', 'OTROS')
ON CONFLICT (nombre) DO NOTHING;
