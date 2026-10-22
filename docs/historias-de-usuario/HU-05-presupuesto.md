# HU-05 — Presupuesto

Casos de uso: **UC19–UC23** · Endpoints: `/api/v1/presupuestos` · Diagrama de flujo:
[`13-flujo-presupuesto.md`](../diagramas/13-flujo-presupuesto.md).

### HU-05-01 — Configurar mi presupuesto 50/30/20
**Como** usuario, **quiero** definir qué porcentaje de mis ingresos va a necesidades, deseos y ahorro, **para** ordenar mi mes.
- Criterios de aceptación:
  - `GET /api/v1/presupuestos/me` devuelve la configuración (o su ausencia) del usuario del token.
  - `POST /api/v1/presupuestos/me` guarda y valida que `necesidades + deseos + ahorro = 100.00` (con `compareTo`) y que **ningún** porcentaje sea negativo; si no → **400**.
  - Responde un DTO (`ConfiguracionPresupuestoResponseDTO`), **no** la entidad.
  - `activo` y `automatizarAhorroEnMetas` controlan el Smart-Split.
- Trazabilidad: UC19 · `GET/POST /api/v1/presupuestos/me` · base
- Acción en la app (Flutter): pantalla "Presupuesto" con sliders que suman 100 %.
- Estado: **implementada**

### HU-05-02 — Ver la ejecución por tipo
**Como** usuario, **quiero** ver cuánto llevo gastado y ahorrado frente a mi presupuesto, **para** saber si voy en camino.
- Criterios de aceptación:
  - `GET /api/v1/presupuestos/ejecucion/me?month&year` calcula la ventana mensual `[inicioDeMes, inicioDeMesSiguiente)` (`FechasMovimiento`).
  - `presupuesto = ingresos del mes × %`; `gasto real = GASTO_GENERAL y PAGO_DEUDA` clasificados por `categoria.tipoPresupuesto`; `ahorroRealizado = SUM` de `ABONO_META` del mes.
  - Un mes **sin ingresos** devuelve ceros (sin dividir por 0).
- Trazabilidad: UC20 · `GET /api/v1/presupuestos/ejecucion/me` · base
- Acción en la app (Flutter): gráfico de barras por tipo (necesidad/deseo/ahorro).
- Estado: **implementada**

### HU-05-03 — Definir límites por categoría
**Como** usuario, **quiero** poner un límite mensual a una categoría, **para** frenar un gasto que se me va de las manos.
- Criterios de aceptación:
  - `GET /api/v1/presupuestos/categorias/me` lee los límites del token; `PUT` **reemplaza** el conjunto.
  - El `PUT` valida que cada categoría **exista**, que el límite sea **> 0** y que no haya categorías repetidas; si no → **400**.
  - Un `PRESUPUESTO_CATEGORIA` es `UNIQUE(usuario, categoria)` y `CHECK(limite_mensual > 0)`.
- Trazabilidad: UC21 · `GET/PUT /api/v1/presupuestos/categorias/me` · T5
- Acción en la app (Flutter): lista de categorías con campo de límite.
- Estado: **implementada**

### HU-05-04 — Ver la ejecución por categoría
**Como** usuario, **quiero** ver cuánto he gastado de cada límite y su semáforo, **para** reaccionar a tiempo.
- Criterios de aceptación:
  - `GET /api/v1/presupuestos/categorias/ejecucion/me?month&year` devuelve por categoría `{categoria, limite, gastado, porcentajeUsado, estado}`.
  - `gastado` = suma en **valor absoluto** de `GASTO_GENERAL` y `PAGO_DEUDA` de esa categoría en el mes.
  - `estado = EstadoEjecucion.desdePorcentaje(...)` con `UMBRAL_ALERTA = 80` y `UMBRAL_EXCEDIDO = 100`: `OK` (< 80), `ALERTA` (≥ 80), `EXCEDIDO` (≥ 100). Los bordes se comparan con `compareTo`.
  - El estado es **derivado, no persistido**.
- Trazabilidad: UC22 · `GET /api/v1/presupuestos/categorias/ejecucion/me` · T5
- Acción en la app (Flutter): barras de progreso con color según el semáforo.
- Estado: **implementada**

### HU-05-05 — Ver mi historial mensual
**Como** usuario, **quiero** comparar mi presupuesto mes a mes, **para** ver mi evolución.
- Criterios de aceptación:
  - `GET /api/v1/presupuestos/historial/me?desde=YYYY-MM&hasta=YYYY-MM` (inclusivos) devuelve una lista de ejecuciones por mes.
  - **Máximo 24 meses**; rango invertido o formato inválido → **400**.
  - Reutiliza el cálculo por mes de la ejecución general (**no** duplica la lógica).
- Trazabilidad: UC23 · `GET /api/v1/presupuestos/historial/me` · T5
- Acción en la app (Flutter): gráfico de líneas del histórico.
- Estado: **implementada**

### HU-05-06 — Reparto automático del ahorro (Smart-Split)
**Como** usuario con presupuesto activo, **quiero** que un ingreso reparta automáticamente mi porcentaje de ahorro entre mis metas, **para** no hacerlo a mano.
- Criterios de aceptación:
  - Se dispara con un `INGRESO_GENERAL` con **`aplicarPresupuesto=true`**, config **`activo`**, **`automatizarAhorroEnMetas`** y **`%ahorro > 0`**.
  - Crea un `ABONO_META` (**negativo**) por cada asignación de meta.
  - Deja el vínculo `movimiento_origen_id` hacia el ingreso que lo originó (el ingreso se guarda antes de repartir).
  - Respeta la invariante de metas.
- Trazabilidad: (extiende UC4/UC19) · interno de `POST /api/v1/movimientos` · base
- Acción en la app (Flutter): se refleja al registrar un ingreso con "Aplicar presupuesto".
- Estado: **implementada**

### HU-05-07 — Porcentajes que no suman 100 rechazados
**Como** usuario, **quiero** que mi configuración se rechace si no suma 100, **para** no dejar el presupuesto inconsistente.
- Criterios de aceptación:
  - `necesidades + deseos + ahorro ≠ 100.00` o algún porcentaje negativo → **400**.
- Trazabilidad: UC19 · `POST /api/v1/presupuestos/me` · base
- Acción en la app (Flutter): sliders que no permiten guardar hasta sumar 100 %.
- Estado: **implementada**

### HU-05-08 — Límite por categoría inválido rechazado
**Como** usuario, **quiero** que un límite ≤ 0, una categoría inexistente o repetida falle, **para** no guardar límites basura.
- Criterios de aceptación:
  - Categoría inexistente, límite ≤ 0 o categorías repetidas en el `PUT` → **400**.
- Trazabilidad: UC21 · `PUT /api/v1/presupuestos/categorias/me` · T5
- Acción en la app (Flutter): valida el límite antes de enviar.
- Estado: **implementada**

### HU-05-09 — Historial con rango inválido rechazado
**Como** usuario, **quiero** que un rango invertido o demasiado largo falle, **para** no pedir datos absurdos.
- Criterios de aceptación:
  - Rango invertido, formato inválido o **más de 24 meses** → **400**.
- Trazabilidad: UC23 · `GET /api/v1/presupuestos/historial/me` · T5
- Acción en la app (Flutter): selector de rango acotado a 24 meses.
- Estado: **implementada**

> La categoría **"Deudas"** (`NECESIDAD`) se asigna automáticamente a los pagos de deuda.
