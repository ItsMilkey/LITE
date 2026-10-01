-- ============================================================
-- V2__seed_categories.sql  |  Oracle 23c
-- Inserta las 10 categorias iniciales de forma idempotente.
-- Usa MERGE INTO (DML estandar Oracle) para no duplicar datos
-- si la migracion se reintenta sobre una BD existente.
-- ============================================================

MERGE INTO CATEGORIA tgt
USING (SELECT 'Sueldo' AS nombre, 'ic_work' AS icon_id, '#009688' AS color_hex, 'OTROS' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Comida' AS nombre, 'ic_fastfood' AS icon_id, '#FFC107' AS color_hex, 'NECESIDAD' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Transporte' AS nombre, 'ic_directions_car' AS icon_id, '#2196F3' AS color_hex, 'NECESIDAD' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Cuentas y Servicios' AS nombre, 'ic_receipt_long' AS icon_id, '#4CAF50' AS color_hex, 'NECESIDAD' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Ocio y Entretenimiento' AS nombre, 'ic_sports_esports' AS icon_id, '#E91E63' AS color_hex, 'DESEO' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Salud y Bienestar' AS nombre, 'ic_medical_services' AS icon_id, '#F44336' AS color_hex, 'NECESIDAD' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Ropa y Accesorios' AS nombre, 'ic_checkroom' AS icon_id, '#9C27B0' AS color_hex, 'DESEO' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Deudas' AS nombre, 'ic_payment' AS icon_id, '#795548' AS color_hex, 'NECESIDAD' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Ahorro' AS nombre, 'ic_savings' AS icon_id, '#3F51B5' AS color_hex, 'AHORRO' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);

MERGE INTO CATEGORIA tgt
USING (SELECT 'Otro' AS nombre, 'ic_label' AS icon_id, '#607D8B' AS color_hex, 'OTROS' AS tipo FROM DUAL) src
ON (tgt.NOMBRE = src.nombre)
WHEN NOT MATCHED THEN INSERT (NOMBRE, ICON_ID, COLOR_HEX, TIPO_PRESUPUESTO) VALUES (src.nombre, src.icon_id, src.color_hex, src.tipo);
