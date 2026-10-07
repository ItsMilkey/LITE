# T5 — Presupuesto por categoría e historial
Depende de: T0. Modelo sugerido: Gemini Flash (Sonnet si surgen dudas).

## Cambios
1. Migración (ambos dialectos): PRESUPUESTO_CATEGORIA(id, usuario_rut FK, categoria_id FK, limite_mensual NUMERIC(19,2) con CHECK > 0, UNIQUE(usuario_rut, categoria_id)). El límite es mensual y recurrente.
2. Endpoints:
   - GET /api/presupuestos/categorias/me
   - PUT /api/presupuestos/categorias/me (reemplaza el conjunto de límites; valida que las categorías existan y los límites sean > 0)
   - GET /api/presupuestos/categorias/ejecucion/me?month&year → por categoría {categoria, limite, gastado, porcentajeUsado, estado: OK | ALERTA (≥ 80 %) | EXCEDIDO (≥ 100 %)}. gastado = suma (valor absoluto) de GASTO_GENERAL y PAGO_DEUDA del mes en esa categoría.
3. GET /api/presupuestos/historial/me?desde=YYYY-MM&hasta=YYYY-MM (máx. 24 meses, si no 400): lista de EjecucionPresupuestoDTO por mes. Extrae la lógica de /ejecucion/me a un método por mes y reutilízalo; no dupliques el cálculo.
4. POST /api/presupuestos/me: valida que porcentajeNecesidades + porcentajeDeseos + porcentajeAhorro = 100 (400 si no; usa compareTo).
5. Umbrales 80 y 100 como constantes.

## Criterios de aceptación (tests)
- Estados en los bordes (79.99 / 80 / 100); historial con un mes sin ingresos (ceros, sin dividir por 0); suma de porcentajes ≠ 100 → 400.
