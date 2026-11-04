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
  - La respuesta incluye `resumen`, `partidas`, `porCategoria`, `repartoPorTipo`, `grupos` y `avisos`; los importes
    son `BigDecimal` escala 2 y los estados (`BAJO_EL_PLAN`, `CERCA_DEL_PLAN`, `SOBRE_EL_PLAN`) son informativos,
    nunca errores.
  - Con `?incluirReferencias=true`, cada elemento de `porCategoria` agrega `referencia` (HU-08-15); sin el parámetro
    el campo no aparece.
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
  - `PUT /api/v1/planes/{mes}/partidas/{id}` cambia `nombre`, `categoriaId`, `grupoId` o `quitarGrupo` (al menos uno;
    `{}` → **400** `VALIDACION_FALLIDA`) y responde **200** con el plan completo. `grupoId` y `quitarGrupo:true`
    juntos → **400** `DATOS_INVALIDOS`.
  - `POST` acepta `grupoId` opcional (HU-08-12) y `deudaId` (HU-08-13), que deriva monto y categoría.
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
  - Sobreasignar el ingreso es válido: solo se informa con `excedeIngreso:true`. El monto de una partida vinculada a
    una deuda lo define la cuota: un `PATCH` sobre ella → **409** `CONFLICTO_REGLA_NEGOCIO` y no se aplica nada.
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
  - Copia nombre, categoría, monto, orden y grupo de cada partida. El ingreso de referencia solo se copia si el
    destino no tenía uno.
  - Las partidas vinculadas a una deuda se copian solo si la deuda tiene una cuota que vence en el mes destino; las
    omitidas aparecen en `avisos` con el motivo `SIN_COUOTA_EN_EL_MES` (HU-08-14).
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
    plan y no se persiste. Un `PAGO_DEUDA` sin `partidaId` cuya deuda tenga una partida vinculada en el plan se
    imputa a esa partida (después del `partidaId` explícito y antes del cálculo por categoría).
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

### HU-08-09 — Reclasificar el tipo de una categoría
**Como** usuario, **quiero** decidir que una categoría (por ejemplo, Suscripciones) cuenta para mí como necesidad y
no como deseo, **para** que toda la app lo entienda igual.
- Criterios de aceptación:
  - `GET /api/v1/categorias/preferencias` devuelve el catálogo con `tipoPorDefecto`, `tipoPreferido` (`null` si no
    definió) y `tipoEfectivo` = preferencia si existe, si no el por defecto.
  - `PUT /api/v1/categorias/{id}/tipo-preferido` con `{ "tipo": "NECESIDAD" | "DESEO" }` guarda la preferencia y
    responde `200` con la fila del catálogo. `DELETE …/tipo-preferido` vuelve al por defecto (idempotente, `204`).
  - Solo NECESIDAD y DESEO son válidos, y solo para categorías cuyo tipo por defecto ya es NECESIDAD o DESEO.
    AHORRO/OTROS en el cuerpo o sobre SUELDO/AHORRO/OTROS → **400** `DATOS_INVALIDOS`; tipo desconocido → **400**
    `SOLICITUD_INVALIDA`; categoría inexistente → **404** en PUT y en DELETE.
  - El tipo efectivo reemplaza al por defecto en la ejecución 50/30/20, en `porCategoria.tipo`, en el reparto por
    tipo y en la sugerencia de regla. No afecta al catálogo base ni al resto de usuarios.
- Trazabilidad: UC25 (ext) → `/api/v1/categorias/preferencias`, `/categorias/{id}/tipo-preferido` → T10d (sesión 1)
- Acción en la app (Flutter): selector "¿Es necesidad o deseo?" por categoría, con opción "tipo por defecto".
- Estado: **implementada**

### HU-08-10 — Comparar el plan con la guía 50/30/20 (reparto por tipo)
**Como** usuario, **quiero** ver cuánto de mi plan y de mi gasto cae en necesidades, deseos y otros, frente a la
guía que configuré, **para** decidir por dónde ajustar sin que nadie me juzgue.
- Criterios de aceptación:
  - El plan de cualquier mes incluye `repartoPorTipo` con las tres filas fijas NECESIDAD, DESEO y OTROS:
    `planificado`, `porcentajeDelIngreso`, `real`, `porcentajeRealDelIngresoReal` y `diferenciaPuntos`.
  - `diferenciaPuntos` (= porcentaje del plan − porcentaje de la guía) solo para NECESIDAD y DESEO; `null` para
    OTROS, sin ingreso de referencia o sin guía. `guia` es `null` si el usuario no tiene configuración (HU-05).
  - Las partidas del tipo AHORRO no aparecen en ninguna fila. Todo es **informativo**: ninguna diferencia es un
    error HTTP y ninguna corrige el plan.
  - Los cálculos exactos están en [`PLAN_FORMULAS.md`](../PLAN_FORMULAS.md) §7 y los vectores V19–V25 en
    [`plan-vectores.json`](../plan-vectores.json).
- Trazabilidad: UC25 → `GET /api/v1/planes/{mes}` → T10d (sesión 1)
- Acción en la app (Flutter): sección "Tu mes frente a la regla"; decide el color con `diferenciaPuntos`.
- Estado: **implementada**

### HU-08-11 — Sugerir una regla desde mi comportamiento real
**Como** usuario, **quiero** que la app me sugiera una regla 50/30/20 basada en cómo gasté de verdad, **para**
ajustar mi guía sin adivinar.
- Criterios de aceptación:
  - `GET /api/v1/presupuestos/sugerencia-regla` analiza hasta los **3 meses cerrados más recientes** (anteriores al
    mes en curso, zona `America/Santiago`) con `ingresoReal > 0`; los meses sin ingresos se saltan.
  - `actual` es la mediana de `necesidades%` y `deseos%` reales (gasto de `GASTO_GENERAL`/`PAGO_DEUDA` del tipo
    efectivo; la categoría OTROS se ignora), con 2 decimales, más `ahorro = 100 − n − d`.
  - Si el gasto supera el ingreso (`gastoSuperaIngreso:true`), se escala proporcional para sumar 100 y `ahorro`
    queda en 0. La `sugerencia` son múltiplos de 5 que suman 100 (empates por orden necesidades, deseos, ahorro).
  - Con menos de 2 meses válidos → `200` con `sugerencia:null` y `motivo:"DATOS_INSUFICIENTES"`; nunca es error.
  - `igualALaGuia` dice si la sugerencia coincide con la configuración vigente (`false` si no la tiene). Solo
    lectura: para aceptarla, la app llama al `POST /presupuestos/me` existente.
- Trazabilidad: UC25 (ext) → `GET /api/v1/presupuestos/sugerencia-regla` → T10d (sesión 1)
- Acción en la app (Flutter): tarjeta "Sugerencia de regla" con botón "Usar esta regla" que prefill el formulario
  de HU-05; muestra `actual` y la sugerencia lado a lado.
- Estado: **implementada**

### HU-08-12 — Agrupar las partidas del plan
**Como** usuario, **quiero** ordenar mis partidas en grupos (por ejemplo "Fijos" y "Variable"), **para** entender el
plan de un vistazo sin que cambie ningún monto.
- Criterios de aceptación:
  - `GET /api/v1/planes/grupos` responde `200` con los grupos del usuario en orden `orden, id`; `POST` con
    `{nombre, orden?}` responde `201`; `PUT /{id}` con `{nombre?, orden?}` responde `200`; `DELETE /{id}` responde
    `204` y deja sus partidas sin grupo, sin borrarlas.
  - Nombre de 1 a 60 caracteres, sin caracteres de control y único por usuario comparando `nombre_normalizado`
    (minúsculas, sin tildes): duplicado → **409** `CONFLICTO_REGLA_NEGOCIO`; más de **20** grupos → **409**;
    nombre inválido → **400** `VALIDACION_FALLIDA`; grupo inexistente → **404**; grupo ajeno → **403**.
  - `POST` de partida acepta `grupoId`; `PUT` acepta `grupoId` o `quitarGrupo:true` (ambos juntos → **400**
    `DATOS_INVALIDOS`). Copiar un mes conserva el grupo de cada partida.
  - El plan devuelve `grupos` con `{id, nombre, orden, planificado, porcentajeDelPlanificado, real, partidaIds}`.
    "Sin grupo" (id nulo) aparece al final solo si hay partidas sin grupo. Un grupo sin partidas aparece con
    `planificado: 0.00`; con planificado total 0, `porcentajeDelPlanificado` es nulo. La suma de porcentajes puede
    no dar 100,00 y no se fuerza.
  - Todo es informativo: un grupo nunca produce un error de dinero.
- Trazabilidad: UC25 (ext) → `/api/v1/planes/grupos` → T10d (sesión 2)
- Acción en la app (Flutter): secciones colapsables por grupo, con su total y porcentaje; el grupo "Sin grupo" se
  muestra al final; permite renombrar, reordenar con `orden` y borrar (advirtiendo que las partidas se conservan).
- Estado: **implementada**

### HU-08-13 — Vincular una partida a una deuda
**Como** usuario, **quiero** que la cuota de una deuda sea una partida del plan, **para** no escribir el monto a mano
y saber si ya la pagué.
- Criterios de aceptación:
  - `POST /api/v1/planes/{mes}/partidas` con `{nombre, deudaId}` crea la partida con categoría `DEUDAS` y
    `montoPlanificado = cuota del mes + costo adicional`, calculado en cada lectura (43.000 + 500 → 43.500).
  - Deuda inexistente → **404**; ajena → **403**; `CANCELADA`, sin `fechaPrimeraCuota` o sin cuota que venza en el
    mes → **409** `CONFLICTO_REGLA_NEGOCIO`; enviar `montoPlanificado` o una categoría distinta de `DEUDAS` →
    **400** `DATOS_INVALIDOS`.
  - Una sola partida por deuda y mes → **409**. `PATCH /montos` sobre una partida vinculada → **409**; su deuda y
    su categoría no se pueden cambiar (se borra y se recrea).
  - Si el vínculo ya no calcula, la partida conserva el último monto guardado y `vinculoDesactualizado: true`; el
    plan no falla.
  - La partida expone `estadoCuota` (`PENDIENTE`/`PARCIAL`/`PAGADA`) además del `estado` neutro. Un `PAGO_DEUDA` sin
    `partidaId` se imputa a la partida vinculada de esa deuda.
  - Si la deuda se borra, la partida queda normal (`deudaId: null`) y conserva el monto guardado.
- Trazabilidad: UC25 (ext) / UC4 → `POST /api/v1/planes/{mes}/partidas` → T10d (sesión 2)
- Acción en la app (Flutter): al crear la partida, opción "Vincular a una deuda" con selector; el monto se muestra
  de solo lectura y el estado de la cuota con su semáforo propio. Se documenta en
  [`PLAN_FORMULAS.md`](../PLAN_FORMULAS.md) §12.
- Estado: **implementada**

### HU-08-14 — Avisar al copiar un mes qué partidas no se copiaron
**Como** usuario, **quiero** saber qué partidas de deuda no pasaron al mes nuevo, **para** no creer que el plan está
completo cuando falta una cuota.
- Criterios de aceptación:
  - Al copiar un mes, las partidas vinculadas se copian solo si la deuda tiene una cuota que vence en el mes destino.
  - Las omitidas **no** se copian y aparecen en `avisos` del plan devuelto con
    `{motivo:"SIN_COUOTA_EN_EL_MES", partidaId, nombre, deudaId, mensaje}`.
  - La copia sigue siendo **201** aunque haya avisos: un aviso nunca es un error HTTP. Fuera de una copia, `avisos`
    es `[]`.
- Trazabilidad: UC25 (ext) → `POST /api/v1/planes/{mes}/copiar-desde/{mesOrigen}` → T10d (sesión 2)
- Acción en la app (Flutter): tras copiar, muestra un aviso informativo con las partidas omitidas y su deuda.
- Estado: **implementada**

### HU-08-15 — Ver referencias históricas de gasto por categoría
**Como** usuario, **quiero** comparar lo que gasté en una categoría con los meses anteriores, **para** decidir mi
plan con contexto.
- Criterios de aceptación:
  - `GET /api/v1/planes/{mes}?incluirReferencias=true` agrega `referencia: {mesAnterior, promedio3Meses}` a cada
    elemento de `porCategoria`; sin el parámetro el campo no aparece.
  - La ventana son los hasta **3 meses cerrados** previos al mes, desde el mes del primer movimiento del usuario;
    los meses sin gasto cuentan 0 y el promedio se redondea HALF_UP a 2 decimales.
  - Si el primer movimiento es del mismo mes (o no hay historial) la referencia es nula; nunca produce un error.
  - Solo se consideran `GASTO_GENERAL` y `PAGO_DEUDA` de la categoría; una sola consulta agregada por categoría y
    mes.
- Trazabilidad: UC25 (ext) → `GET /api/v1/planes/{mes}?incluirReferencias=true` → T10d (sesión 2)
- Acción en la app (Flutter): línea o etiqueta "mes anterior" y "promedio 3 meses" junto a la categoría; el color lo
  decide la app, la API no juzga.
- Estado: **implementada**
