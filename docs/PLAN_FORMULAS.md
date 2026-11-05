# Fórmulas del plan mensual (T10c, extendido en T10d)

Documento de referencia para el backend y el cliente Flutter. Todo cálculo de
dinero del plan vive en `service/plan/CalculadoraPlan` (clase pura, sin Spring
ni base de datos); este documento describe exactamente lo que hace.

Los casos de entrada/salida comprometidos están en
[`plan-vectores.json`](plan-vectores.json). Se regeneran con:

```powershell
.\mvnw.cmd test "-Dtest=PlanVectoresTest" "-Dplan.update=true"
```

Sin `-Dplan.update=true`, `PlanVectoresTest` falla si el archivo no coincide.

## 1. Tipos y redondeo

- Todos los montos son `BigDecimal` con escala 2 y `RoundingMode.HALF_UP`.
- `porcentajeUsado` es la única excepción: se **trunca** (`RoundingMode.DOWN`)
  a 2 decimales. Así el porcentaje mostrado nunca alcanza un umbral antes de
  que el estado lo haga.
- Los porcentajes informativos (margen de seguridad) se redondean `HALF_UP`.
- No se usa `double` en ninguna parte.

## 2. Entradas

- `ingresoReferencia`: monto opcional (nulo o ≥ 0).
- Partidas: `{id, nombre, categoría, montoPlanificado ≥ 0, orden, grupoId?, deudaId?}`.
- Movimientos del mes (ventana semiabierta
  `[inicioDeMes, inicioDeMesSiguiente)` en `America/Santiago`):
  `{monto con signo, tipo, partidaId?, deudaId?, categoría?}`.

Solo participan del plan `GASTO_GENERAL` y `PAGO_DEUDA`. `INGRESO_GENERAL`,
`ABONO_META` y `RETIRO_META` nunca se imputan a partidas.

## 3. Imputación del gasto real

Para cada movimiento imputable, en este orden:

1. Tiene `partidaId` → esa partida.
2. No lo tiene, es `PAGO_DEUDA` y su `deudaId` tiene una partida vinculada en
   el plan → esa partida (T10d, §12).
3. No lo tiene y su categoría coincide con **exactamente una** partida del
   plan → esa partida.
4. Su categoría coincide con **dos o más** partidas → sin planificar, motivo
   `CATEGORIA_AMBIGUA`.
5. Su categoría no tiene partidas, o el mes no tiene plan → sin planificar,
   motivo `SIN_PARTIDA`.

Un movimiento va a una sola partida: no hay reparto. La imputación se calcula
al leer y no se persiste.

## 4. Resumen del mes

| Campo | Fórmula | Nulo / borde |
|---|---|---|
| `planificadoTotal` | Σ `montoPlanificado` | 0 sin partidas |
| `remanenteLibre` | `ingresoReferencia − planificadoTotal` | nulo sin ingreso |
| `margenSeguridadPct` | `remanenteLibre ÷ ingresoReferencia × 100` | nulo sin ingreso o ingreso 0 |
| `excedeIngreso` | `planificadoTotal > ingresoReferencia` | falso sin ingreso; informativo |
| `ingresoReal` | Σ `INGRESO_GENERAL` | 0 si no hay |
| `gastoRealTotal` | Σ \|monto\| de `GASTO_GENERAL` y `PAGO_DEUDA` | 0 si no hay; incluye lo no imputado |
| `disponibleReal` | `ingresoReal − gastoRealTotal` | 0 si no hay movimientos |
| `ahorroRegistrado` | Σ \|monto\| de `ABONO_META` | informativo (DC14) |
| `sinPlanificar.monto` | Σ \|monto\| de los gastos no imputados | 0 si todos se imputaron |
| `sinPlanificar.movimientos` | cantidad de gastos no imputados | 0 si todos se imputaron |
| `sinPlanificar.porMotivo[]` | `{motivo, monto, movimientos}` | solo motivos con movimientos |

`margenSeguridadPct` se calcula sobre el ingreso de **referencia**, no sobre el
ingreso real: el plan es una decisión previa y el ingreso real solo alimenta
`ingresoReal` y `disponibleReal`.

## 5. Por partida

Para cada partida del plan, en el orden recibido:

- `real` = Σ \|monto\| de los movimientos imputados a ella.
- `diferencia` = `montoPlanificado − real` (negativa si se pasó).
- `porcentajeUsado` = `real × 100 ÷ montoPlanificado`, truncado a 2 decimales.
  Nulo si `montoPlanificado = 0`.
- `estado` = `EstadoEjecucion.desde(real, montoPlanificado)`:
  - `BAJO_EL_PLAN` si `real × 100 < 80 × planificado`.
  - `CERCA_DEL_PLAN` si `80 × planificado ≤ real × 100 ≤ 100 × planificado`.
  - `SOBRE_EL_PLAN` si `real × 100 > 100 × planificado`.
  - Si `montoPlanificado = 0`: `SOBRE_EL_PLAN` cuando `real > 0` y
    `BAJO_EL_PLAN` cuando `real = 0`.

La comparación del estado es exacta (multiplicación cruzada), no usa el
porcentaje truncado. Por eso un real de 250.001 sobre 250.000 muestra
`100,00` pero el estado es `SOBRE_EL_PLAN` (vector V10).

Una partida vinculada a una deuda (T10d, §12) conserva ese mismo `estado`
neutro y agrega `estadoCuota`, calculado en paralelo con el real imputado:

- `PENDIENTE` si no hay pagos en el mes (`real = 0`).
- `PARCIAL` si `0 < real < montoPlanificado`.
- `PAGADA` si `real ≥ montoPlanificado`.

El `montoPlanificado` de una partida vinculada no es el guardado: se recalcula
en cada lectura como cuota del mes + costo adicional. Si el vínculo ya no
calcula, se devuelve el último monto guardado y `vinculoDesactualizado:true`.

## 6. Por categoría (`porCategoria`)

Incluye toda categoría que tenga partidas **o** gasto real en el mes.

- `planificado` = Σ montos de las partidas de esa categoría.
- `real` = Σ \|monto\| de **todos** los gastos de la categoría, tengan o no
  partida (incluye los que quedaron sin planificar).
- `diferencia`, `porcentajeUsado` y `estado` con las mismas reglas que una
  partida.
- `tipo` = tipo efectivo de la categoría (T10d): la preferencia del usuario
  si definió una, o el tipo por defecto del catálogo si no.
- El orden es por `categoriaId` ascendente.

## 7. Reparto por tipo y guía (T10d, sesiones 1)

Junto al plan llegan `repartoPorTipo` y `guia`, ambos puramente informativos:
ninguna diferencia de puntos es un error ni bloquea nada.

- `guia` = `{necesidades, deseos, ahorro}` de la configuración de presupuesto
  del usuario, o `null` si no la tiene.
- `repartoPorTipo` tiene exactamente tres filas, en el orden NECESIDAD, DESEO,
  OTROS. Para cada tipo:
  - `planificado` = Σ montos de las partidas cuyo tipo efectivo es ese tipo
    (con escala 2; 0,00 si no hay).
  - `porcentajeDelIngreso` = `planificado ÷ ingresoReferencia × 100`
    (HALF_UP, 2 decimales); nulo sin ingreso de referencia o si es 0.
  - `real` = Σ |monto| del gasto del mes por tipo efectivo (independiente de
    la imputación por partida; incluye lo sin planificar y lo sin partida).
  - `porcentajeRealDelIngresoReal` = `real ÷ ingresoReal × 100` (HALF_UP);
    nulo sin ingreso real o si es 0.
  - `diferenciaPuntos` = `porcentajeDelIngreso − porcentaje de la guía`
    (2 decimales), solo para NECESIDAD y DESEO. Para OTROS, sin ingreso o sin
    guía es nulo: OTROS no tiene referencia.
- Las partidas de categorías AHORRO no aparecen en ninguna fila.

Con los vectores de referencia de T10c (ingreso 1.000.000; necesidades
730.000; deseos 16.000) y guía 50/30/20: necesidades 73,00 % (+23,00 puntos)
y deseos 1,60 % (−28,40 puntos). Lo cubren los casos V19–V25 de
`plan-vectores.json`.

## 8. Reglas de las partidas

- Nombre: 1 a 80 caracteres después de `trim`, sin caracteres de control.
- Único dentro del plan comparando `nombre_normalizado` (minúsculas, sin
  tildes, espacios colapsados; conserva palabras vacías y puntuación).
- Monto ≥ 0, hasta 2 decimales y 13 enteros.
- Categoría del catálogo distinta de `SUELDO` y `AHORRO`.
- Máximo 100 partidas por plan.
- Cambiar la categoría con movimientos vinculados → 409.
- Borrar una partida deja sus movimientos con `partida_id = NULL`; vuelven a
  imputarse por categoría al leer el plan.
- `orden` de una partida nueva = máximo actual + 1 (empieza en 1).

## 9. Guardado por lote y copia

- `PATCH /api/v1/planes/{mes}/montos` recibe `{cambios:[{partidaId, monto}]}`
  y es atómico: si una partida no existe, un monto es inválido, hay un
  `partidaId` repetido, el lote está vacío o supera 100 cambios, no se aplica
  ninguno.
- Repetir el mismo lote no cambia `actualizadoEn` de las partidas cuyos montos
  no cambiaron.
- Copiar un mes exige: origen con al menos una partida (si no, 404), destino
  sin partidas (si no, 409) y meses distintos (si no, 400). Copia nombre,
  categoría, monto, orden y grupo; el ingreso de referencia solo si el destino
  no tenía uno.
- Las partidas vinculadas a una deuda se copian solo si la deuda tiene cuota
  en el mes destino; las omitidas se informan en `avisos` con el motivo
  `SIN_COUOTA_EN_EL_MES` (§12). Fuera de una copia, `avisos` siempre es `[]`.

## 10. Uso sugerido en el cliente (sliders)

- El máximo sugerido de un slider es `monto actual + máx(remanenteLibre, 0)`.
  Se calcula localmente y cambia a medida que se mueven otras partidas.
- Si el usuario teclea un monto mayor, se acepta con un aviso: sobreasignar es
  válido y `excedeIngreso` solo lo informa.
- Guardar al soltar el slider, al confirmar el teclado numérico, o acumulando
  cambios y enviándolos juntos. El mismo endpoint sirve para todo.
- Tras cada respuesta, la app reemplaza su estado local por el plan devuelto.

## 11. Grupos (T10d, sesión 2)

Un grupo solo ordena y resume: no cambia ningún cálculo de dinero. `grupo_plan`
es del usuario (máximo 20, nombre 1–60, único por `nombre_normalizado`).
Borrar un grupo deja sus partidas sin grupo (`ON DELETE SET NULL`).

El plan devuelve `grupos`:

- Un elemento por cada grupo del usuario, en orden `orden` y luego `id`.
- Al final, `"Sin grupo"` solo si hay partidas con `grupoId` nulo o con un
  grupo que ya no existe; su `id` y `orden` son nulos.
- Para cada grupo: `planificado` = Σ montos de sus partidas; `real` = Σ real de
  sus partidas; `partidaIds` en orden de presentación.
- `porcentajeDelPlanificado = planificado ÷ planificadoTotal × 100` (HALF_UP,
  2 decimales); nulo si `planificadoTotal = 0`. La suma de porcentajes puede no
  dar exactamente 100,00 por redondeo y no se fuerza.
- Un grupo sin partidas en el mes aparece con `planificado = 0,00` y
  `partidaIds = []`.

Con los datos de referencia de §7: Fijos (Arriendo, Netflix, Spotify) 416.000
(55,76 %) y Variable (Supermercado, Transporte) 330.000 (44,24 %). Casos
V26–V29 de `plan-vectores.json`.

En el cliente, al mover un slider solo hay que recalcular localmente, para los
grupos afectados, `planificado` y `porcentajeDelPlanificado` con las fórmulas
anteriores (y `repartoPorTipo.planificado` / `porcentajeDelIngreso` por tipo).

## 12. Deudas vinculadas (T10d, sesión 2)

Una partida con `deudaId` representa la cuota de esa deuda en el mes:

- La deuda debe existir (404), ser del usuario (403), no estar `CANCELADA`
  (409), tener `fechaPrimeraCuota` (409) y una cuota que venza en el mes (409).
- La categoría es siempre `DEUDAS`: si llega otra → 400; si se omite, se usa
  `DEUDAS`. Enviar `montoPlanificado` junto con `deudaId` → 400.
- `montoPlanificado = cuota del mes + costo adicional de esa cuota`, calculado
  con el mismo motor que `DeudaService` **en cada lectura**. Ejemplo: cuota
  43.000 y costo adicional 500 → 43.500.
- Solo puede existir una partida por deuda y plan (409).
- Un `PATCH /montos` sobre una partida vinculada → 409: el monto lo define la
  cuota. Su categoría y su deuda no se cambian (se borra y se recrea).
- Si el vínculo ya no puede calcularse (por ejemplo, se editaron las
  condiciones de la deuda y ya no hay cuota ese mes), se devuelve el último
  monto guardado y `vinculoDesactualizado: true`. El plan no falla.
- `estadoCuota` se calcula en paralelo al `estado` neutro (§5):
  `PENDIENTE` sin pagos, `PARCIAL` con `0 < real < monto`, `PAGADA` con
  `real ≥ monto`. Ejemplo: pago de 20.000 sobre 43.500 → `PARCIAL`
  (diferencia 23.500); pago de 43.500 → `PAGADA`; sin pago → `PENDIENTE`.
  Casos V30–V33 de `plan-vectores.json`.
- Imputación: un `PAGO_DEUDA` sin `partidaId` cuya deuda tenga partida
  vinculada en el plan va a esa partida (§3, paso 2). El `partidaId` explícito
  siempre manda.
- Copiar un mes: la partida vinculada solo se copia si la deuda tiene cuota en
  el mes destino. Si no, no se copia y se agrega un aviso
  `{motivo:"SIN_COUOTA_EN_EL_MES", partidaId, nombre, deudaId, mensaje}`;
  `avisos` es `[]` fuera de una copia.

## 13. Referencias históricas (T10d, sesión 2)

`GET /api/v1/planes/{mes}?incluirReferencias=true` agrega a cada elemento de
`porCategoria` un objeto `referencia {mesAnterior, promedio3Meses}`. Sin el
parámetro, el campo no se incluye.

- La ventana son los **hasta 3 meses cerrados** inmediatamente anteriores al
  mes del plan, sin retroceder antes del mes del primer movimiento del usuario.
  Si el primer movimiento es del mismo mes (o no hay movimientos), la
  referencia es nula.
- El gasto de cada mes es la suma de \|monto\| de `GASTO_GENERAL` y
  `PAGO_DEUDA` de la categoría. Un mes de la ventana sin gasto cuenta como 0.
- `mesAnterior` = gasto del mes inmediatamente anterior al plan (0,00 si no
  hubo gasto dentro de la ventana).
- `promedio3Meses = Σ gasto de la ventana ÷ cantidad de meses de la ventana`,
  HALF_UP a 2 decimales.
- Ejemplo: 100.000 en agosto, 120.000 en septiembre y 80.000 en octubre, con
  plan en noviembre → `promedio3Meses = 100.000,00` y
  `mesAnterior = 80.000,00`.
- Una consulta de rango (más `primerMovimiento` para fijar el inicio) y agrupación por mes en memoria con
  `FechasMovimiento` (America/Santiago); el cálculo en sí vive en
  `ReferenciasHistoricas` (puro). No se usa `EXTRACT(MONTH ...)` en SQL: la
  columna `fecha` está en UTC y movería los gastos de fin de mes.
