-- Diagnostico V4 (READ-ONLY): filas de MOVIMIENTO con signo invalido.
-- Ejecutar en Supabase ANTES de desplegar V8. Si devuelve filas, el
-- ALTER TABLE movimiento VALIDATE CONSTRAINT chk_movimiento_signo fallaria
-- y bloquearia la migracion (Flyway revierte V8 completo).
--
-- Copia literal de la consulta de diagnostico de V4__movimiento_check_signo.sql
-- (no ejecuta DDL, no modifica datos).

SELECT tipo_movimiento,
       CASE WHEN monto > 0 THEN 'positivo' ELSE 'negativo' END AS signo_actual,
       COUNT(*) AS filas,
       SUM(monto) AS suma_montos
FROM movimiento
WHERE NOT (
    (tipo_movimiento IN ('INGRESO_GENERAL', 'RETIRO_META') AND monto > 0)
    OR (tipo_movimiento IN ('GASTO_GENERAL', 'PAGO_DEUDA', 'ABONO_META') AND monto < 0)
)
GROUP BY tipo_movimiento,
         CASE WHEN monto > 0 THEN 'positivo' ELSE 'negativo' END
ORDER BY filas DESC;
