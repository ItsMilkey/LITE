-- ============================================================
-- V13__catalogo_categorias_codigo.sql  |  PostgreSQL
-- T10a: catálogo de 18 categorías y `codigo` estable.
-- No modifica migraciones anteriores. RLS y chk_categoria_tipo
-- quedan intactos (solo se agrega una columna y constraints).
-- ============================================================

-- 1) Columna nueva, primero nula para poder rellenarla.
ALTER TABLE categoria ADD COLUMN IF NOT EXISTS codigo VARCHAR(40);

-- 2) Código de las 10 categorías sembradas en V2, por su nombre actual.
UPDATE categoria SET codigo = 'SUELDO'                    WHERE nombre = 'Sueldo'                 AND codigo IS NULL;
UPDATE categoria SET codigo = 'ALIMENTACION_BASE'         WHERE nombre = 'Comida'                 AND codigo IS NULL;
UPDATE categoria SET codigo = 'TRANSPORTE_MOVILIDAD'      WHERE nombre = 'Transporte'             AND codigo IS NULL;
UPDATE categoria SET codigo = 'VIVIENDA_SUMINISTROS'      WHERE nombre = 'Cuentas y Servicios'    AND codigo IS NULL;
UPDATE categoria SET codigo = 'SALUD_BIENESTAR'           WHERE nombre = 'Salud y Bienestar'      AND codigo IS NULL;
UPDATE categoria SET codigo = 'OCIO_RECREACION'           WHERE nombre = 'Ocio y Entretenimiento' AND codigo IS NULL;
UPDATE categoria SET codigo = 'CUIDADO_PERSONAL_VESTUARIO' WHERE nombre = 'Ropa y Accesorios'     AND codigo IS NULL;
UPDATE categoria SET codigo = 'DEUDAS'                    WHERE nombre = 'Deudas'                 AND codigo IS NULL;
UPDATE categoria SET codigo = 'AHORRO'                    WHERE nombre = 'Ahorro'                 AND codigo IS NULL;
UPDATE categoria SET codigo = 'OTROS'                     WHERE nombre = 'Otro'                   AND codigo IS NULL;

-- 3) Renombrar las 8 existentes que cambian de nombre, buscando por código
--    (el id, el ícono y el color se conservan; los movimientos no cambian).
UPDATE categoria SET nombre = 'Alimentación Base'            WHERE codigo = 'ALIMENTACION_BASE';
UPDATE categoria SET nombre = 'Transporte y Movilidad'       WHERE codigo = 'TRANSPORTE_MOVILIDAD';
UPDATE categoria SET nombre = 'Vivienda y Suministros'       WHERE codigo = 'VIVIENDA_SUMINISTROS';
UPDATE categoria SET nombre = 'Ocio y Recreación'            WHERE codigo = 'OCIO_RECREACION';
UPDATE categoria SET nombre = 'Cuidado Personal y Vestuario' WHERE codigo = 'CUIDADO_PERSONAL_VESTUARIO';
UPDATE categoria SET nombre = 'Pago de Deudas'               WHERE codigo = 'DEUDAS';
UPDATE categoria SET nombre = 'Ahorro y Fondo de Reserva'    WHERE codigo = 'AHORRO';
UPDATE categoria SET nombre = 'Otros'                        WHERE codigo = 'OTROS';

-- 4) Ocho categorías nuevas (íconos y colores aprobados en T10a).
INSERT INTO categoria (nombre, codigo, icon_id, color_hex, tipo_presupuesto)
VALUES ('Educación y Formación',    'EDUCACION_FORMACION',     'ic_school',             '#00BCD4', 'NECESIDAD'),
       ('Seguros e Impuestos',      'SEGUROS_IMPUESTOS',       'ic_policy',             '#FF9800', 'NECESIDAD'),
       ('Mascotas',                 'MASCOTAS',                'ic_pets',               '#8BC34A', 'NECESIDAD'),
       ('Hogar y Consumibles',      'HOGAR_CONSUMIBLES',       'ic_cleaning_services',  '#03A9F4', 'NECESIDAD'),
       ('Equipamiento y Tecnología', 'EQUIPAMIENTO_TECNOLOGIA', 'ic_devices',            '#673AB7', 'DESEO'),
       ('Suscripciones y Digital',  'SUSCRIPCIONES_DIGITAL',   'ic_subscriptions',      '#FF5722', 'DESEO'),
       ('Restaurantes y Salidas',   'RESTAURANTES_SALIDAS',    'ic_restaurant',         '#CDDC39', 'DESEO'),
       ('Regalos y Celebraciones',  'REGALOS_CELEBRACIONES',   'ic_card_giftcard',      '#EC407A', 'DESEO')
ON CONFLICT DO NOTHING;

-- 5) Categorías con nombres desconocidos (datos de otro entorno): no fallar en
--    silencio; reciben un código derivado único y la migración lo reporta.
DO $$
DECLARE
    sin_codigo INTEGER;
BEGIN
    SELECT count(*) INTO sin_codigo FROM categoria WHERE codigo IS NULL;
    IF sin_codigo > 0 THEN
        UPDATE categoria SET codigo = 'CAT_' || id_categoria WHERE codigo IS NULL;
        RAISE NOTICE 'V13: % categoría(s) con nombre desconocido recibieron codigo CAT_<id>', sin_codigo;
    END IF;
END $$;

-- 6) Código obligatorio, único y con formato estable.
ALTER TABLE categoria ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE categoria ADD CONSTRAINT uq_categoria_codigo UNIQUE (codigo);
ALTER TABLE categoria ADD CONSTRAINT chk_categoria_codigo CHECK (codigo ~ '^[A-Z][A-Z0-9_]*$');
