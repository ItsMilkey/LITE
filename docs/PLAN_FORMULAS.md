# Fórmulas del plan mensual (T10c)

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
- Partidas: `{id, nombre, categoría, montoPlanificado ≥ 0, orden}`.
- Movimientos del mes (ventana semiabierta
  `[inicioDeMes, inicioDeMesSiguiente)` en `America/Santiago`):
  `{monto con signo, tipo, partidaId?, categoría?}`.

Solo participan del plan `GASTO_GENERAL` y `PAGO_DEUDA`. `INGRESO_GENERAL`,
`ABONO_META` y `RETIRO_META` nunca se imputan a partidas.

## 3. Imputación del gasto real

Para cada movimiento imputable, en este orden:

1. Tiene `partidaId` → esa partida.
2. No lo tiene y su categoría coincide con **exactamente una** partida del
   plan → esa partida.
3. Su categoría coincide con **dos o más** partidas → sin planificar, motivo
   `CATEGORIA_AMBIGUA`.
4. Su categoría no tiene partidas, o el mes no tiene plan → sin planificar,
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

## 6. Por categoría (`porCategoria`)

Incluye toda categoría que tenga partidas **o** gasto real en el mes.

- `planificado` = Σ montos de las partidas de esa categoría.
- `real` = Σ \|monto\| de **todos** los gastos de la categoría, tengan o no
  partida (incluye los que quedaron sin planificar).
- `diferencia`, `porcentajeUsado` y `estado` con las mismas reglas que una
  partida.
- `tipo` = tipo por defecto de la categoría (T10d podrá reemplazarlo por la
  preferencia del usuario).
- El orden es por `categoriaId` ascendente.

## 7. Reglas de las partidas

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

## 8. Guardado por lote y copia

- `PATCH /api/v1/planes/{mes}/montos` recibe `{cambios:[{partidaId, monto}]}`
  y es atómico: si una partida no existe, un monto es inválido, hay un
  `partidaId` repetido, el lote está vacío o supera 100 cambios, no se aplica
  ninguno.
- Repetir el mismo lote no cambia `actualizadoEn` de las partidas cuyos montos
  no cambiaron.
- Copiar un mes exige: origen con al menos una partida (si no, 404), destino
  sin partidas (si no, 409) y meses distintos (si no, 400). Copia nombre,
  categoría, monto y orden; el ingreso de referencia solo si el destino no
  tenía uno.

## 9. Uso sugerido en el cliente (sliders)

- El máximo sugerido de un slider es `monto actual + máx(remanenteLibre, 0)`.
  Se calcula localmente y cambia a medida que se mueven otras partidas.
- Si el usuario teclea un monto mayor, se acepta con un aviso: sobreasignar es
  válido y `excedeIngreso` solo lo informa.
- Guardar al soltar el slider, al confirmar el teclado numérico, o acumulando
  cambios y enviándolos juntos. El mismo endpoint sirve para todo.
- Tras cada respuesta, la app reemplaza su estado local por el plan devuelto.
