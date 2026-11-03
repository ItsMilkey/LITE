# T10c — Plan del mes y partidas

Depende de: T10a (categorías con `codigo` y estados neutros). Se puede hacer en paralelo con T10b. Reglas comunes: ver T10-decisiones.md (G1 a G10). Modelo sugerido: Opus o Gemini Pro para el plan; Sonnet 4.6 Thinking para implementar, en dos sesiones: (1) migración, calculadora pura y vectores; (2) servicio, endpoints y cambios en movimientos. Empieza con el Paso 0 y espera el "ok".

## 1. Objetivo

El usuario arma un plan por mes con partidas (nombre libre, categoría del catálogo, monto planificado) y un ingreso de referencia opcional. El servidor calcula el resumen y lo compara con el gasto real. El frontend previsualiza los sliders localmente y guarda en un solo lote.

## 2. Paso 0 — Auditoría (solo lectura)

1. Número de la siguiente migración y estado de T10a.
2. `PresupuestoService`, `FechasMovimiento` (ventana mensual semiabierta) y cómo cargan los movimientos del mes (para reutilizar la consulta y no multiplicarla).
3. `Movimiento`, `MovimientoRegistroDTO`, `MovimientoEdicionDTO`, `MovimientoResponseDTO`, `MovimientoSpecifications` y los filtros de T4: dónde entra `partidaId`. Importante: el PUT de T4 es parcial y trata los nulos como "no enviado"; por eso aquí se usa `desvincularPartida` (sección 8).
4. Cómo se categorizan hoy los abonos y retiros de metas (para la pregunta abierta 1 de T10-decisiones.md; solo se reporta).
5. `EliminacionDeCuentaService`: orden de borrado y dónde incluir las tablas nuevas.
6. `EstadoEjecucion.desde(...)` de T10a y su firma.
7. `DeudaService.registrarPago` y cómo `POST /movimientos` delega en él (para la regla de `PAGO_DEUDA` con `partidaId`).
8. `CodigoError` y los manejadores de errores.

## 3. Modelo de datos

Siguiente migración. Constraints e índices en minúscula. RLS en las dos tablas. FKs hacia `usuario` con `ON DELETE CASCADE` (patrón V10); la FK entre hijos queda sin cascada y el borrado de cuenta elimina hijo antes que padre (patrón del proyecto).

**plan_mensual**
- `id_plan_mensual` BIGINT identity, `pk_plan_mensual`
- `usuario_id` VARCHAR NOT NULL, FK `fk_plan_usuario` → usuario ON DELETE CASCADE
- `mes` DATE NOT NULL (siempre día 1), `chk_plan_mes_dia1`
- `ingreso_referencia` NUMERIC(19,2) NULL, `chk_plan_ingreso` (nulo o ≥ 0)
- `creado_en`, `actualizado_en` TIMESTAMP NOT NULL
- `uq_plan_usuario_mes` UNIQUE (usuario_id, mes)

**partida_plan**
- `id_partida_plan` BIGINT identity, `pk_partida_plan`
- `plan_id` BIGINT NOT NULL, FK `fk_partida_plan` → plan_mensual (sin cascada)
- `categoria_id` BIGINT NOT NULL, FK `fk_partida_categoria` → categoria
- `nombre` VARCHAR(80) NOT NULL; `nombre_normalizado` VARCHAR(80) NOT NULL
- `monto_planificado` NUMERIC(19,2) NOT NULL, `chk_partida_monto` (≥ 0)
- `orden` INTEGER NOT NULL DEFAULT 0
- `actualizado_en` TIMESTAMP NOT NULL
- `uq_partida_plan_nombre` UNIQUE (plan_id, nombre_normalizado)
- Índices `idx_partida_plan` (plan_id), `idx_partida_categoria` (categoria_id), `idx_partida_nombre` (nombre_normalizado)

**movimiento** (ALTER)
- `partida_id` BIGINT NULL, FK `fk_mov_partida` → partida_plan ON DELETE SET NULL; índice `idx_movimiento_partida`.

`nombre_normalizado` = minúsculas, sin tildes, espacios colapsados y recortados (sin quitar palabras vacías ni puntuación). Dos nombres que coinciden así son duplicados.

La migración no modifica filas existentes: las tablas nuevas empiezan vacías y `partida_id` queda nulo.

## 4. Reglas de negocio

- **Mes:** `yyyy-MM`, entre 2000-01 y 2100-12. Todos los meses son editables (DC11).
- **Plan:** se crea al primer cambio (ingreso de referencia, partida o copia). `GET` de un mes sin plan devuelve 200 con `existe: false` (sección 7), no 404.
- **Partida:** nombre de 1 a 80 caracteres tras recortar, sin caracteres de control; monto ≥ 0 con hasta 2 decimales y 13 enteros; categoría del catálogo distinta de SUELDO y AHORRO (DC14 y DC15). Máximo 100 partidas por plan. Nombre único dentro del plan según `nombre_normalizado`.
- **Cambio de categoría de una partida:** se rechaza (409) si tiene movimientos vinculados; el nombre sí se puede cambiar siempre.
- **Borrar una partida:** sus movimientos quedan sin partida (`SET NULL`) y vuelven a imputarse por categoría (sección 5).
- **Ingreso de referencia:** nulo, o ≥ 0.
- **Último en llegar gana.** No hay bloqueo optimista. Cada partida expone `actualizadoEn`.

## 5. Imputación del gasto real

Aplica a movimientos `GASTO_GENERAL` y `PAGO_DEUDA` con fecha dentro del mes (ventana semiabierta de `FechasMovimiento`). `INGRESO_GENERAL`, `ABONO_META` y `RETIRO_META` nunca se imputan a partidas.

Para cada movimiento imputable, en este orden:
1. Tiene `partida_id` → esa partida.
2. No lo tiene y su categoría coincide con **exactamente una** partida del plan → esa partida.
3. Su categoría coincide con **dos o más** partidas → "Sin planificar", motivo `CATEGORIA_AMBIGUA`.
4. Su categoría no tiene partidas (o el mes no tiene plan) → "Sin planificar", motivo `SIN_PARTIDA`.

Un movimiento va a una sola partida; no hay reparto. La imputación se calcula al leer y no se guarda.

## 6. Cálculos

Todo con `BigDecimal`, escala 2, HALF_UP, salvo `porcentajeUsado` (HALF_UP no; ver abajo).

Resumen del mes:
- `planificadoTotal` = suma de `montoPlanificado`.
- `remanenteLibre` = `ingresoReferencia − planificadoTotal`; nulo sin ingreso.
- `margenSeguridadPct` = `remanenteLibre ÷ ingresoReferencia × 100`; nulo si no hay ingreso o es 0.
- `excedeIngreso` = `planificadoTotal > ingresoReferencia` (falso sin ingreso). Es informativo; nunca produce error.
- `ingresoReal` = suma de `INGRESO_GENERAL` del mes.
- `gastoRealTotal` = suma de |monto| de `GASTO_GENERAL` y `PAGO_DEUDA` del mes (todos, con o sin partida).
- `disponibleReal` = `ingresoReal − gastoRealTotal`.
- `ahorroRegistrado` = suma de |monto| de `ABONO_META` del mes (informativo; DC14).
- `sinPlanificar` = `{monto, movimientos, porMotivo[]}` según la sección 5.

Por partida:
- `real` = suma de |monto| de los movimientos imputados a ella.
- `diferencia` = `montoPlanificado − real` (negativa si se pasó).
- `porcentajeUsado` = `real × 100 ÷ montoPlanificado`, **truncado** a 2 decimales (ROUND_DOWN) para que el valor mostrado nunca alcance un umbral antes de que el estado lo haga. Nulo si `montoPlanificado` es 0.
- `estado` = `EstadoEjecucion.desde(real, montoPlanificado)` (T10a, comparación exacta por multiplicación cruzada). Si `montoPlanificado` es 0: SOBRE_EL_PLAN si `real > 0`, y BAJO_EL_PLAN si `real = 0`.

Por categoría (`porCategoria`): todas las categorías que tengan partidas o gasto real en el mes.
- `planificado` = suma de las partidas de la categoría; `real` = suma de **todos** los gastos de la categoría en el mes (con o sin partida); mismo cálculo de `diferencia`, `porcentajeUsado` y `estado`; `tipo` = tipo por defecto de la categoría (T10d lo reemplaza por la preferencia del usuario).

Porcentajes de otras métricas (margen, etc.): HALF_UP a 2 decimales.

## 7. Contrato de API

Base `/api/v1/planes`. JWT obligatorio; el usuario sale del token.

| Método y ruta | Cuerpo | Éxito |
|---|---|---|
| `GET /{mes}` | — | 200 plan completo |
| `PUT /{mes}/ingreso-referencia` | `{"monto": 1000000.00}` o `{"monto": null}` | 200 plan |
| `POST /{mes}/partidas` | `{"nombre","categoriaId","montoPlanificado"}` | 201 plan |
| `PUT /{mes}/partidas/{id}` | `{"nombre"?, "categoriaId"?}` (al menos uno) | 200 plan |
| `PATCH /{mes}/montos` | `{"cambios":[{"partidaId":1,"monto":300000.00}]}` (de 1 a 100) | 200 plan |
| `DELETE /{mes}/partidas/{id}` | — | 204 |
| `POST /{mes}/copiar-desde/{mesOrigen}` | — | 201 plan |

Todas las respuestas con plan devuelven el plan completo, para que el cliente se reconcilie. Ejemplo de `GET /2026-11` (cifras ilustrativas):

```json
{
  "mes": "2026-11",
  "existe": true,
  "ingresoReferencia": 1000000.00,
  "resumen": {
    "planificadoTotal": 746000.00,
    "remanenteLibre": 254000.00,
    "margenSeguridadPct": 25.40,
    "excedeIngreso": false,
    "ingresoReal": 1000000.00,
    "gastoRealTotal": 714500.00,
    "disponibleReal": 285500.00,
    "ahorroRegistrado": 0.00,
    "sinPlanificar": {
      "monto": 12000.00, "movimientos": 1,
      "porMotivo": [ { "motivo": "SIN_PARTIDA", "monto": 12000.00, "movimientos": 1 } ]
    }
  },
  "partidas": [
    { "id": 21, "nombre": "Supermercado", "categoriaId": 2, "categoriaCodigo": "ALIMENTACION_BASE",
      "montoPlanificado": 250000.00, "real": 262500.00, "diferencia": -12500.00,
      "porcentajeUsado": 105.00, "estado": "SOBRE_EL_PLAN", "orden": 1,
      "actualizadoEn": "2026-11-01T14:20:00.000Z" }
  ],
  "porCategoria": [
    { "categoriaId": 2, "codigo": "ALIMENTACION_BASE", "tipo": "NECESIDAD",
      "planificado": 250000.00, "real": 262500.00, "diferencia": -12500.00,
      "porcentajeUsado": 105.00, "estado": "SOBRE_EL_PLAN" }
  ]
}
```

Mes sin plan: `existe: false`, `ingresoReferencia: null`, `partidas: []`, `planificadoTotal: 0`, `remanenteLibre: null`, y el gasto real del mes completo en `sinPlanificar` con motivo `SIN_PARTIDA`.

**Copiar:** `mesOrigen` debe tener al menos una partida (si no, 404); `mes` no debe tener partidas (si no, 409); `mes` y `mesOrigen` deben ser distintos (400). Copia nombre, categoría, monto y orden; copia el ingreso de referencia solo si el destino no tiene uno. No copia movimientos ni valores reales.

**PATCH montos:** montos absolutos (no incrementos), atómico (todo o nada), sin `partidaId` repetido. Si el monto es igual al actual, no cambia nada y `actualizadoEn` se conserva.

**Errores** (códigos existentes):

| Caso | Código | HTTP |
|---|---|---|
| Nombre vacío o de más de 80 caracteres, monto negativo, más de 2 decimales o más de 13 enteros, lote vacío o de más de 100, `partidaId` repetido, PUT sin campos, ingreso negativo | VALIDACION_FALLIDA (con `errores[]` y el campo JSON, por ejemplo `cambios[0].monto`) | 400 |
| Mes con formato inválido o fuera de rango; mes igual a mesOrigen | PARAMETRO_INVALIDO | 400 |
| Categoría SUELDO o AHORRO | DATOS_INVALIDOS | 400 |
| Categoría, partida o mes de origen inexistentes; partida que existe pero no pertenece al plan de ese mes | NO_ENCONTRADO | 404 |
| Nombre duplicado en el plan; más de 100 partidas; cambio de categoría con movimientos vinculados; copiar a un mes con partidas | CONFLICTO_REGLA_NEGOCIO | 409 |
| Partida de otro usuario | PROHIBIDO | 403 |

Mensajes en español y sin detalles técnicos. Ejemplo: "Ya tienes una partida con ese nombre en este mes."

## 8. Cambios en movimientos

- `MovimientoRegistroDTO` y `MovimientoEdicionDTO` aceptan `partidaId` opcional. `MovimientoEdicionDTO` agrega `desvincularPartida` (booleano) porque el PUT parcial ignora los nulos.
- `MovimientoResponseDTO` devuelve `partidaId`.
- `GET /api/v1/movimientos/paginados/me` acepta el filtro `partidaId`.
- Reglas al crear o editar un gasto con partida:
  - Solo `GASTO_GENERAL` y `PAGO_DEUDA` admiten `partidaId`; otro tipo con `partidaId` → 400.
  - La partida debe existir (404), ser del usuario (403) y pertenecer al plan del **mes de la fecha del movimiento** (DC16); si no → 400. Al crear, la fecha es la del servidor, es decir, el mes actual en America/Santiago.
  - Si se envía `categoriaId` y no coincide con la de la partida → 400. Si no se envía, se usa la de la partida (DC4).
  - `PAGO_DEUDA` con `partidaId`: la categoría de la partida debe ser la de `codigo` DEUDAS; si no → 400.
  - Al editar un movimiento que ya tiene partida y se envía una `categoriaId` distinta sin `desvincularPartida: true` → 400 con el mensaje "Cambia la partida o desvincula el gasto antes de cambiar la categoría."
  - `desvincularPartida: true` junto con `partidaId` → 400.
- `POST /api/v1/deudas/{id}/pagar` no recibe `partidaId` (fuera de alcance): esos pagos se imputan por categoría (sección 5).

## 9. Soporte para los sliders (contrato de cálculo local)

Durante el arrastre, la app no llama al servidor. Solo cambian los montos planificados; los valores reales son los del último `GET`. Por eso la app puede recalcular sola:

- Resumen: `planificadoTotal`, `remanenteLibre`, `margenSeguridadPct`, `excedeIngreso`.
- Por partida: `diferencia`, `porcentajeUsado`, `estado`.
- Por categoría: `planificado`, `diferencia`, `porcentajeUsado`, `estado`.

No recalcula: `ingresoReal`, `gastoRealTotal`, `disponibleReal`, `ahorroRegistrado`, `sinPlanificar` ni la imputación; esos valores solo cambian al leer del servidor.

Las fórmulas, el truncado de `porcentajeUsado`, el redondeo y la comparación exacta de estados están en `docs/PLAN_FORMULAS.md` (se crea en esta tarea). Para que el servidor y la app no se desvíen:
- `docs/plan-vectores.json` contiene casos (entradas y resultados esperados) generados por un test desde la calculadora pura y comparados con el archivo comprometido, igual que `openapi.json` (`-Dplan.update=true` lo regenera).
- La app usa esos vectores en sus propias pruebas.

Guía de uso (se documenta en `CLIENTE_FLUTTER.md`): máximo sugerido de un slider = monto actual + máx(`remanenteLibre`, 0) (calculado localmente, cambia a medida que se mueven otras partidas); si el usuario teclea un monto mayor, se acepta con un aviso (sobreasignar es válido). Guardar al soltar el slider, al confirmar el teclado numérico, o acumulando cambios y enviándolos juntos; el mismo endpoint sirve para todo. Tras cada respuesta, la app reemplaza su estado local por el plan devuelto.

## 10. Historial para el sugeridor (T10b)

Implementar `FuenteHistorialCategoria`: dado el usuario y un nombre normalizado, devuelve el `codigo` de la categoría de la partida **más reciente del mismo usuario** con ese `nombre_normalizado` (empate: la de mayor `actualizadoEn`). Solo datos del propio usuario. Una consulta indexada por `idx_partida_nombre`.

## 11. Diseño de código

- `service/plan/CalculadoraPlan` (puro, sin Spring ni base de datos): recibe ingreso de referencia, partidas, movimientos imputables y devuelve el resumen, las partidas calculadas, `porCategoria` y `sinPlanificar`. Contiene toda la lógica de las secciones 5 y 6.
- `service/plan/PlanService`: valida propiedad y reglas, carga datos con un número fijo de consultas, llama a la calculadora y arma los DTO. Sin cálculos.
- `service/plan/PlanVectores` (solo en tests): genera `docs/plan-vectores.json`.
- Controlador delgado `PlanController`. Sin `try/catch` ni `ResponseEntity<?>`. `PresupuestoService` no se toca.
- Actualizar `EliminacionDeCuentaService` (movimientos → partidas → planes).

## 12. Casos de prueba

Marcas: [U] calculadora pura, [I] integración, [P] contra PostgreSQL local. Datos de referencia: ingreso 1.000.000; partidas Arriendo (VIVIENDA_SUMINISTROS) 400.000, Supermercado (ALIMENTACION_BASE) 250.000, Transporte (TRANSPORTE_MOVILIDAD) 80.000, Netflix (SUSCRIPCIONES_DIGITAL) 10.000, Spotify (SUSCRIPCIONES_DIGITAL) 6.000.

| ID | Caso | Esperado | Tipo |
|---|---|---|---|
| C01 | Sin plan, sin ingreso y sin movimientos | Todo en cero; remanente y margen nulos | U |
| C02 | Ingreso 1.000.000 sin partidas | planificado 0; remanente 1.000.000; margen 100,00 | U |
| C03 | Datos de referencia | planificado 746.000; remanente 254.000; margen 25,40; excede falso | U |
| C04 | Supermercado 250.000 → 300.000 | planificado 796.000; remanente 204.000; margen 20,40 | U |
| C05 | Arriendo → 700.000 | planificado 1.046.000; remanente −46.000; margen −4,60; excede verdadero | U |
| C06 | Ingreso 0 con partidas | margen nulo; remanente = −planificado | U |
| C07 | Sin ingreso, con partidas | remanente, margen nulos; excede falso | U |
| C08 | Real 199.999 de 250.000 | porcentaje 79,99; BAJO_EL_PLAN | U |
| C09 | Real 200.000 de 250.000 | 80,00; CERCA_DEL_PLAN | U |
| C10 | Real 250.000 de 250.000 | 100,00; CERCA_DEL_PLAN | U |
| C11 | Real 250.001 de 250.000 | 100,00 (truncado); SOBRE_EL_PLAN | U |
| C12 | Real 262.500 de 250.000 | 105,00; SOBRE_EL_PLAN; diferencia −12.500 | U |
| C13 | Monto 0 con real 0; monto 0 con real 1.000 | Porcentaje nulo; BAJO; y SOBRE | U |
| C14 | Gasto con `partida_id` de Netflix | Se imputa a Netflix aunque comparta categoría con Spotify | U |
| C15 | Gasto de Transporte sin `partida_id` (una sola partida) | Se imputa a Transporte | U |
| C16 | Gasto de Suscripciones sin `partida_id` (dos partidas) | Sin planificar, motivo CATEGORIA_AMBIGUA | U |
| C17 | Gasto de Salud sin partidas | Sin planificar, motivo SIN_PARTIDA; aparece en `porCategoria` con planificado 0 | U |
| C18 | `PAGO_DEUDA` con una partida de categoría DEUDAS | Se imputa a ella | U |
| C19 | `INGRESO_GENERAL`, `ABONO_META`, `RETIRO_META` | No se imputan; los abonos suman a `ahorroRegistrado` | U |
| C20 | Suscripciones: partidas 10.000 y 6.000, gasto real 10.000 | `porCategoria`: planificado 16.000; porcentaje 62,50; BAJO | U |
| C21 | Mes sin plan con gastos | `existe` falso; todo el gasto en `sinPlanificar` | I |
| C22 | Ingreso real 900.000 y de referencia 1.000.000 | El plan no cambia; `disponibleReal` usa el real | U |
| C23 | POST partida válida en un mes sin plan | 201; el plan queda creado | I |
| C24 | Nombre vacío o solo espacios | 400 VALIDACION_FALLIDA | I |
| C25 | Nombre de 81 caracteres | 400 | I |
| C26 | "Super" y luego " súper " en el mismo mes | 409 CONFLICTO_REGLA_NEGOCIO | I |
| C27 | Mismo nombre en otro mes | 201 | I |
| C28 | Categoría SUELDO; categoría AHORRO | 400 DATOS_INVALIDOS (ambas) | I |
| C29 | `categoriaId` inexistente | 404 NO_ENCONTRADO | I |
| C30 | Monto negativo; con 3 decimales; con 14 enteros | 400 (las tres) | I |
| C31 | Partida número 101 | 409 | I |
| C32 | Mes `2026-1`, `2026-13`, `abc`, `1999-12`, `2101-01` | 400 PARAMETRO_INVALIDO | I |
| C33 | Ingreso de referencia: −1, 0, nulo, 1.000.000 | 400; 200; 200 (limpia); 200 | I |
| C34 | PUT partida sin campos | 400 | I |
| C35 | Cambiar categoría con movimientos vinculados; sin ellos | 409; 200 | I |
| C36 | Borrar partida con movimientos | 204; movimientos sin partida; se reimputan por categoría | I |
| C37 | Partida de otro usuario (PUT, PATCH, DELETE) | 403 | I |
| C38 | Partida de otro mes del mismo usuario en la ruta de este mes | 404 | I |
| C39 | PATCH montos válido | 200; plan completo recalculado | I |
| C40 | PATCH con un monto inválido entre varios | 400 y ningún monto cambia | I |
| C41 | PATCH con `partidaId` repetido; con 0 cambios; con 101 | 400 (las tres) | I |
| C42 | PATCH repetido con los mismos montos | Mismo resultado; `actualizadoEn` no cambia la segunda vez | I |
| C43 | PATCH con partida inexistente entre varias | 404 y nada aplicado | I |
| C44 | Copiar un mes con 5 partidas a un mes vacío | 201; 5 partidas; ingreso copiado si el destino no tenía | I |
| C45 | Copiar a un mes que ya tiene partidas | 409 | I |
| C46 | Copiar desde un mes sin partidas; desde sí mismo | 404; 400 | I |
| C47 | POST gasto con `partidaId` del mes actual | Categoría derivada; el real de la partida sube | I |
| C48 | POST gasto con `partidaId` y `categoriaId` distinta | 400 | I |
| C49 | POST gasto con `partidaId` de otro mes | 400 | I |
| C50 | POST ingreso con `partidaId` | 400 | I |
| C51 | PUT: asignar `partidaId`; `desvincularPartida: true`; ambos juntos | 200; 200; 400 | I |
| C52 | PUT con `categoriaId` distinta en un gasto con partida, sin desvincular | 400 con el mensaje de la sección 8 | I |
| C53 | `GET paginados` con `partidaId` | Solo movimientos de esa partida | I |
| C54 | `PAGO_DEUDA` por `/movimientos` con `partidaId` de categoría DEUDAS; de otra | 200; 400 | I |
| C55 | Gasto del 31-oct 23:30 hora de Santiago (02:30 UTC del 1-nov) | Cuenta en octubre, no en noviembre | I |
| C56 | Plan con 100 partidas y 500 movimientos | Número de consultas fijo (máximo 5) | I |
| C57 | Sin token; token de otro usuario | 401; el plan del otro usuario no aparece | I |
| C58 | Borrado de cuenta | Se eliminan planes y partidas sin errores de FK | I |
| C59 | Constraints: nombre duplicado, monto negativo, día de `mes` distinto de 1, FK | Fallan en PostgreSQL | P |
| C60 | RLS activo y sin políticas; índices; migración sobre copia de datos reales | Sin errores; filas existentes intactas | P |
| C61 | Sugeridor con historial: el usuario tuvo una partida "Super" en ALIMENTACION_BASE | "super" devuelve esa categoría primero con 0,95 y motivo HISTORIAL; otro usuario no la ve | I |
| C62 | `docs/plan-vectores.json` regenerado | Coincide con el comprometido | U |
| C63 | `openapi.json` | Incluye todos los endpoints con sus errores | I |

## 13. Criterios de aceptación

- [ ] Los 63 casos existen y pasan; los [U] sin Spring; los [P] contra PostgreSQL local.
- [ ] Todos los cálculos viven en `CalculadoraPlan`; ningún controlador ni DTO calcula.
- [ ] Ningún `double`, ningún `ZoneId.systemDefault()`.
- [ ] `docs/PLAN_FORMULAS.md` y `docs/plan-vectores.json` creados y consistentes.
- [ ] `openapi.json`, `CLIENTE_FLUTTER.md`, `CONTEXT.md`, historia de usuario del plan y diagrama de secuencia actualizados.
- [ ] Suite verde con UTC y Asia/Tokyo.

## 14. Fuera de alcance

Grupos, deudas vinculadas, preferencia de tipo, reparto por tipo, sugerencia de regla y referencias de promedio (T10d); partidas de ahorro (DC14); imputar pagos hechos por `/deudas/{id}/pagar` a una partida concreta; fecha opcional en movimientos; bloqueo optimista; provisiones anuales.
