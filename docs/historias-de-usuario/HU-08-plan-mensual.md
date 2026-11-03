# HU-08 — Plan del mes y partidas

Casos de uso: **UC25** (armar el plan del mes y partidas) → Endpoints: `/api/v1/planes` → Diagrama de flujo:
[`17-flujo-plan-mensual.md`](../diagramas/17-flujo-plan-mensual.md). Fórmulas y vectores:
[`PLAN_FORMULAS.md`](../PLAN_FORMULAS.md) y [`plan-vectores.json`](../plan-vectores.json).

### HU-08-01 — Ver el plan de un mes
**Como** usuario, **quiero** abrir el plan de cualquier mes y ver cuánto tengo planificado frente a lo que ya gasté,
**para** saber cómo voy sin cerrar el mes.
- Criterios de aceptación:
  - `GET /api/v1/planes/{mes}` responde **200** con el plan completo del usuario del token; `mes` es `yyyy-MM` entre
    `2000-01` y `2100-12` (otro formato o rango → **400** `PARAMETRO_INVALIDO`).
  - Un mes **sin plan** responde **200** con `existe:false`, `ingresoReferencia:null`, `partidas:[]`,
    `planificadoTotal:0`, `remanenteLibre:null` y todo el gasto real del mes en `sinPlanificar` con motivo
    `SIN_PARTIDA`. Nunca es **404**.
  - La respuesta incluye `resumen`, `partidas` y `porCategoria`; los importes son `BigDecimal` escala 2 y los
    estados (`BAJO_EL_PLAN`, `CERCA_DEL_PLAN`, `SOBRE_EL_PLAN`) son informativos, nunca errores.
  - La identidad sale **solo** del JWT; no se acepta `usuarioRut` en la petición. Un plan ajeno no aparece.
- Trazabilidad: UC25 → `GET /api/v1/planes/{mes}` → T10c
- Acción en la app (Flutter): pestaña "Plan" con selector de mes; si `existe:false`, estado vacío con el botón
  "Armar plan".
- Estado: **implementada**

### HU-08-02 — Definir el ingreso de referencia del mes
**Como** usuario, **quiero** indicar cuánto ingreso espero este mes, **para** comparar mi plan contra ese monto.
- Criterios de aceptación:
  - `PUT /api/v1/planes/{mes}/ingreso-referencia` con `{ "monto": 1000000.00 }` define el ingreso; con
    `{ "monto": null }` lo borra.
  - El monto es opcional, ≥ 0, con hasta 2 decimales y 13 enteros; un valor negativo o con más decimales → **400**
    `VALIDACION_FALLIDA`.
  - Definir el ingreso **crea el plan** si el mes no existía y responde **200** con el plan recalculado.
  - Sin ingreso, `remanenteLibre` y `margenSeguridadPct` son `null`; con ingreso 0, el margen también es `null`.
- Trazabilidad: UC25 → `PUT /api/v1/planes/{mes}/ingreso-referencia` → T10c
- Acción en la app (Flutter): campo "Ingreso del mes" en la cabecera del plan; guarda al confirmar.
- Estado: **implementada**

### HU-08-03 — Crear y editar partidas del plan
**Como** usuario, **quiero** crear partidas con nombre libre, categoría del catálogo y monto planificado, **para**
repartir mi mes en lo que de verdad me importa.
- Criterios de aceptación:
  - `POST /api/v1/planes/{mes}/partidas` con `{nombre, categoriaId, montoPlanificado}` responde **201** con el plan
    completo; crea el plan si no existía.
  - `PUT /api/v1/planes/{mes}/partidas/{id}` cambia `nombre` y/o `categoriaId` (al menos uno; `{}` → **400**
    `VALIDACION_FALLIDA`) y responde **200** con el plan completo.
  - El nombre se recorta: 1 a 80 caracteres, sin caracteres de control, y único dentro del plan comparando
    `nombre_normalizado` (minúsculas, sin tildes, espacios colapsados). Duplicado → **409**
    `CONFLICTO_REGLA_NEGOCIO`.
  - El monto es ≥ 0, escala 2 y hasta 13 enteros. La categoría debe existir y **no** puede ser `SUELDO` ni
    `AHORRO` (**400** `DATOS_INVALIDOS`).
  - Máximo **100** partidas por plan (**409**). Una partida nueva recibe `orden = máximo actual + 1` (empieza en 1).
  - Cambiar la categoría de una partida con movimientos vinculados → **409** `CONFLICTO_REGLA_NEGOCIO`; cambiar
    solo el nombre siempre se permite.
- Trazabilidad: UC25 → `POST /api/v1/planes/{mes}/partidas`, `PUT /api/v1/planes/{mes}/partidas/{id}` → T10c
- Acción en la app (Flutter): formulario "Nueva partida" y edición en línea; la categoría se elige del catálogo por
  `codigo`.
- Estado: **implementada**

### HU-08-04 — Ajustar montos con sliders y guardar en un lote
**Como** usuario, **quiero** mover los montos de varias partidas y guardarlos juntos, **para** repartir mi ingreso
sin enviar una petición por cada movimiento del slider.
- Criterios de aceptación:
  - `PATCH /api/v1/planes/{mes}/montos` recibe `{ "cambios": [{ "partidaId": 1, "monto": 420000.00 }] }` (1 a 100
    cambios) y responde **200** con el plan completo.
  - Es **atómico**: si el mes no tiene plan (**404**), una partida no pertenece al plan (**404**, o **403** si es de
    otro usuario), un monto es inválido, hay un `partidaId` repetido o el lote está vacío, no se aplica **ninguno**.
  - Los montos son **absolutos**, no incrementos. Repetir el mismo lote no cambia `actualizadoEn` de las partidas
    cuyo monto no cambió.
  - Sobreasignar el ingreso es válido: solo se informa con `excedeIngreso:true`.
- Trazabilidad: UC25 → `PATCH /api/v1/planes/{mes}/montos` → T10c
- Acción en la app (Flutter): sliders con máximo sugerido `monto actual + máx(remanenteLibre, 0)`; guarda al soltar
  el slider o al confirmar el teclado, acumulando los cambios en un solo `PATCH`.
- Estado: **implementada**

### HU-08-05 — Ver la ejecución real por partida y categoría
**Como** usuario, **quiero** ver cuánto gasté de cada partida y de cada categoría, **para** corregir a tiempo.
- Criterios de aceptación:
  - Cada partida devuelve `real`, `diferencia = montoPlanificado − real`, `porcentajeUsado` truncado a 2 decimales
    (`ROUND_DOWN`) y `estado` calculado con comparación exacta por multiplicación cruzada (el 100 % exacto es
    `CERCA_DEL_PLAN`; 250.001 sobre 250.000 muestra 100,00 pero es `SOBRE_EL_PLAN`).
  - `porCategoria` incluye toda categoría con partidas **o** gasto real en el mes; su `real` suma todos los gastos
    de la categoría, tengan o no partida.
  - Solo se imputan `GASTO_GENERAL` y `PAGO_DEUDA`; `INGRESO_GENERAL`, `ABONO_META` y `RETIRO_META` nunca.
  - `resumen` trae `planificadoTotal`, `remanenteLibre`, `margenSeguridadPct`, `excedeIngreso`, `ingresoReal`,
    `gastoRealTotal`, `disponibleReal`, `ahorroRegistrado` y `sinPlanificar` con su desglose por motivo
    (`CATEGORIA_AMBIGUA` o `SIN_PARTIDA`).
  - Un movimiento va a **una sola** partida: no hay reparto. Si no tiene `partidaId` y su categoría tiene
    exactamente una partida, se imputa a ella; con dos o más queda ambiguo.
- Trazabilidad: UC25 → `GET /api/v1/planes/{mes}` → T10c
- Acción en la app (Flutter): barras por partida y por categoría con el semáforo de `estado`; sección "Sin
  planificar" con el motivo.
- Estado: **implementada**

### HU-08-06 — Copiar el plan de otro mes
**Como** usuario, **quiero** reutilizar el plan de un mes anterior, **para** no volver a crear todas las partidas.
- Criterios de aceptación:
  - `POST /api/v1/planes/{mes}/copiar-desde/{mesOrigen}` responde **201** con el plan de destino completo.
  - El origen debe existir y tener al menos una partida (**404** `NO_ENCONTRADO`); el destino no debe tener partidas
    (**409** `CONFLICTO_REGLA_NEGOCIO`); origen y destino deben ser distintos (**400** `PARAMETRO_INVALIDO`).
  - Copia nombre, categoría, monto y orden de cada partida. El ingreso de referencia solo se copia si el destino no
    tenía uno.
  - Los movimientos del mes de origen **no** se copian: la copia es solo del plan.
- Trazabilidad: UC25 → `POST /api/v1/planes/{mes}/copiar-desde/{mesOrigen}` → T10c
- Acción en la app (Flutter): botón "Copiar plan de…" con selector de mes; refresca el plan devuelto.
- Estado: **implementada**

### HU-08-07 — Imputar un movimiento a una partida
**Como** usuario, **quiero** asociar un gasto o pago de deuda a una partida, **para** que el plan refleje exactamente
dónde se fue el dinero.
- Criterios de aceptación:
  - `POST /api/v1/movimientos` acepta `partidaId` opcional en `GASTO_GENERAL` y `PAGO_DEUDA`; la partida debe ser del
    **mes actual** y su categoría deriva la del movimiento. `INGRESO_GENERAL` con `partidaId` → **400**
    `DATOS_INVALIDOS`.
  - En `PAGO_DEUDA` la partida debe ser de categoría `DEUDAS`. `POST /api/v1/deudas/{id}/pagar` sigue funcionando sin
    partida.
  - `PUT /api/v1/movimientos/{id}` acepta `partidaId` (partida del **mes de la fecha del movimiento**) o
    `desvincularPartida:true`; enviar ambos → **400**. Si el movimiento tiene partida, hay que cambiarla o
    desvincularla antes de cambiar la categoría.
  - `GET /api/v1/movimientos/paginados/me?partidaId=<id>` filtra el historial por partida.
  - `ABONO_META` y `RETIRO_META` no admiten partida. La imputación automática por categoría se calcula al leer el
    plan y no se persiste.
- Trazabilidad: UC4 (extendido) / UC25 → `POST /api/v1/movimientos`, `PUT /api/v1/movimientos/{id}`,
  `GET /api/v1/movimientos/paginados/me` → T10c
- Acción en la app (Flutter): selector opcional "Partida" en el formulario de gasto y en el de pago de deuda; el
  historial muestra y filtra por partida.
- Estado: **implementada**

### HU-08-08 — Rechazar partidas inválidas y borrar sin perder gastos
**Como** usuario, **quiero** que el plan me avise de datos inválidos y que borrar una partida no borre mis gastos,
**para** mantener el plan limpio sin perder información.
- Criterios de aceptación:
  - Nombre vacío, de más de 80 caracteres o con caracteres de control → **400** `VALIDACION_FALLIDA` con `errores[]`.
  - Nombre duplicado, plan con 100 partidas o cambio de categoría con movimientos → **409**
    `CONFLICTO_REGLA_NEGOCIO`. Un `partidaId` repetido en el lote → **400** `VALIDACION_FALLIDA`.
  - Categoría `SUELDO`/`AHORRO`, partida de otro mes o categoría que no coincide con la partida → **400**
    `DATOS_INVALIDOS`.
  - Partida inexistente → **404**; partida de otro usuario → **403** `PROHIBIDO` (anti-IDOR).
  - `DELETE /api/v1/planes/{mes}/partidas/{id}` responde **204** y deja los movimientos vinculados con
    `partidaId = null`; no borra ningún movimiento y, al releer, se reimputan por categoría.
- Trazabilidad: UC25 → `DELETE /api/v1/planes/{mes}/partidas/{id}` y validaciones de `/api/v1/planes` → T10c
- Acción en la app (Flutter): muestra `errores[]` junto al campo; confirma el borrado advirtiendo que los gastos se
  conservan sin partida.
- Estado: **implementada**

> El plan del mes es independiente del presupuesto 50/30/20 (HU-05): aquel reparte **por tipo** y este **por
> partida**. Ambos leen los mismos movimientos y comparten `EstadoEjecucion`.
