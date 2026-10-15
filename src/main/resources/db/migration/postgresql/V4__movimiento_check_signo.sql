-- V4__movimiento_check_signo.sql  |  PostgreSQL (Supabase)
--
-- T1d: integridad del signo de los movimientos.
-- El signo ya no lo decide quien escribe: lo impone SignoMovimiento
-- (com.example.saveup.service.movimiento.SignoMovimiento) antes de guardar.
-- Este CHECK es la red de seguridad a nivel de base de datos.
--
-- Tipos con signo positivo : INGRESO_GENERAL, RETIRO_META
-- Tipos con signo negativo : GASTO_GENERAL, PAGO_DEUDA, ABONO_META
--
-- NOT VALID: el constraint se crea sin validar las filas existentes, para no
-- bloquear el arranque si el histórico contiene filas con signo invertido.
-- OJO: aunque sea NOT VALID, PostgreSQL SÍ rechaza desde ya todo INSERT o
-- UPDATE que viole la condición. Un UPDATE sobre una fila antigua con signo
-- invertido fallará hasta que se corrija el dato.

ALTER TABLE MOVIMIENTO
    ADD CONSTRAINT CHK_MOVIMIENTO_SIGNO
    CHECK (
        (tipo_movimiento IN ('INGRESO_GENERAL', 'RETIRO_META') AND monto > 0)
        OR (tipo_movimiento IN ('GASTO_GENERAL', 'PAGO_DEUDA', 'ABONO_META') AND monto < 0)
    ) NOT VALID;

-- Diagnóstico: filas históricas que violan el CHECK, agrupadas por tipo.
-- Correr antes de validar el constraint. Si devuelve filas, hay que corregir
-- el signo de esos movimientos antes de ejecutar el VALIDATE.
--
-- SELECT tipo_movimiento,
--        CASE WHEN monto > 0 THEN 'positivo' ELSE 'negativo' END AS signo_actual,
--        COUNT(*) AS filas,
--        SUM(monto) AS suma_montos
-- FROM MOVIMIENTO
-- WHERE NOT (
--     (tipo_movimiento IN ('INGRESO_GENERAL', 'RETIRO_META') AND monto > 0)
--     OR (tipo_movimiento IN ('GASTO_GENERAL', 'PAGO_DEUDA', 'ABONO_META') AND monto < 0)
-- )
-- GROUP BY tipo_movimiento,
--          CASE WHEN monto > 0 THEN 'positivo' ELSE 'negativo' END
-- ORDER BY filas DESC;

-- Una vez revisado y corregido el histórico, validar el constraint:
--
-- ALTER TABLE MOVIMIENTO VALIDATE CONSTRAINT CHK_MOVIMIENTO_SIGNO;

-- Nota: si se agrega un valor al enum TipoMovimiento, este CHECK debe
-- actualizarse en la misma tarea (ver .agents/rules/20-java-spring.md).
