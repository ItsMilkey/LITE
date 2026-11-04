# T10d — Reglas flexibles: tipo preferido, reparto por tipo, sugerencia de regla, grupos y deudas vinculadas

Depende de: T10c. Reglas comunes: ver T10-decisiones.md (G1 a G10). Modelo sugerido: Opus o Gemini Pro para el plan; Sonnet 4.6 Thinking para implementar, en dos sesiones: (1) preferencias, reparto por tipo y sugerencia de regla; (2) grupos, deudas vinculadas y referencias. Empieza con el Paso 0 y espera el "ok".

## 1. Objetivo

Que la guía 50/30/20 sea una referencia flexible y no un dogma:
- el usuario puede reclasificar una categoría como Necesidad o Deseo;
- el plan se compara con la guía de forma informativa;
- la app puede sugerir una regla basada en el comportamiento real;
- las partidas se ordenan en grupos y pueden vincularse a una deuda;
- opcionalmente, la app muestra referencias de gasto histórico.

Principio de tono: nada de esto produce errores ni lenguaje de juicio. Son datos informativos; el color y el texto los decide la app.

## 2. Paso 0 — Auditoría (solo lectura)

1. Número de la siguiente migración y estado de T10c.
2. `PresupuestoService.calcularEjecucionGeneral`: **cómo trata hoy la categoría OTROS** y dónde lee `categoria.tipoPresupuesto`. Todo el tipo efectivo de este documento debe usar el mismo criterio que T5. Reportar el hallazgo; si T5 ignora OTROS, la sugerencia de regla también.
3. Todas las lecturas de `tipoPresupuesto` en `src/main` (ejecución, ejecución por categoría, reportes, plan).
4. `ConfiguracionPresupuesto` y su DTO (porcentajes y cómo saber si el usuario tiene configuración).
5. `DeudaService`: cómo obtener la cuota (con costo adicional) cuyo vencimiento cae en un mes, usando `CalendarioCuotas`, `fechaPrimeraCuota` y el cronograma; estados de deuda (PENDIENTE, PAGADA, CANCELADA).
6. `EliminacionDeCuentaService`: orden de borrado para las tablas nuevas.
7. `CalculadoraPlan` y `docs/plan-vectores.json` de T10c (hay que extenderlos).

## 3. Preferencia de tipo por usuario

Tabla `preferencia_categoria_usuario`: `id_preferencia` (PK `pk_preferencia`), `usuario_id` (FK `fk_pref_usuario` ON DELETE CASCADE), `categoria_id` (FK `fk_pref_categoria`), `tipo_presupuesto` VARCHAR(20) NOT NULL con `chk_pref_tipo` (solo NECESIDAD o DESEO), UNIQUE `uq_pref_usuario_categoria` (usuario_id, categoria_id). RLS activo.

Regla: **tipo efectivo** = preferencia del usuario si existe; si no, el tipo por defecto de la categoría. Solo se pueden reclasificar categorías cuyo tipo por defecto es NECESIDAD o DESEO (no SUELDO, AHORRO ni OTROS).

Endpoints (`/api/v1/categorias`):

| Método y ruta | Descripción | Éxito |
|---|---|---|
| `GET /preferencias` | Todas las categorías con `tipoPorDefecto`, `tipoPreferido` (o nulo) y `tipoEfectivo` | 200 |
| `PUT /{id}/tipo-preferido` | Cuerpo `{"tipo":"NECESIDAD"}` | 200 (la fila de la lista) |
| `DELETE /{id}/tipo-preferido` | Vuelve al tipo por defecto; es idempotente | 204 |

Errores: categoría inexistente → 404; categoría no reclasificable o tipo AHORRO u OTROS → 400 DATOS_INVALIDOS; valor que no es un tipo conocido → 400 SOLICITUD_INVALIDA.

El tipo efectivo reemplaza al tipo por defecto en: la ejecución general de T5 (cambio de comportamiento documentado), `porCategoria.tipo` del plan, el reparto por tipo y la sugerencia de regla. Se carga con una sola consulta de preferencias por solicitud y se resuelve en memoria.

## 4. Reparto por tipo (plan frente a guía)

El plan agrega `repartoPorTipo` y `guia` (solo informativos):

- Para cada tipo de NECESIDAD, DESEO y OTROS: `planificado` (suma de las partidas por tipo efectivo de su categoría), `porcentajeDelIngreso` (`planificado ÷ ingresoReferencia × 100`, HALF_UP; nulo sin ingreso), `real` (gasto real de ese tipo en el mes) y `porcentajeRealDelIngresoReal` (nulo si no hay ingreso real).
- `guia`: `{necesidades, deseos, ahorro}` tomados de la configuración del usuario, o `null` si no la tiene.
- `diferenciaPuntos` para necesidades y deseos: `porcentajeDelIngreso − guia` con 2 decimales; nulo si falta alguno de los dos datos.

Con los datos de referencia de T10c (ingreso 1.000.000; necesidades 730.000; deseos 16.000) y guía 50/30/20: necesidades 73,00 % (+23,00 puntos), deseos 1,60 % (−28,40 puntos).

## 5. Sugerencia de regla

`GET /api/v1/presupuestos/sugerencia-regla`. Solo lectura.

Algoritmo, con `BigDecimal` y 10 decimales internos:
1. Meses: hasta los 3 meses cerrados más recientes anteriores al mes en curso (zona America/Santiago) que tengan `ingresoReal > 0`.
2. Si hay menos de 2 meses válidos → 200 con `sugerencia: null` y `motivo: "DATOS_INSUFICIENTES"`.
3. Por mes: `necesidades% = gasto de tipo efectivo NECESIDAD ÷ ingresoReal × 100`; igual para deseos. El gasto es el de `GASTO_GENERAL` y `PAGO_DEUDA`, con el mismo criterio de T5 (Paso 0) para OTROS.
4. Mediana de cada porcentaje entre los meses (con cantidad par, promedio de los dos centrales).
5. Si `necesidades + deseos > 100`: `gastoSuperaIngreso = true`, se escalan ambos proporcionalmente para sumar 100 y `ahorro = 0`. Si no: `ahorro = 100 − necesidades − deseos` (lo que no se gastó).
6. Redondeo a múltiplos de 5 con restos mayores: base = piso a múltiplo de 5 de cada valor; los pasos de 5 que faltan hasta 100 se reparten a los mayores restos; empate: orden necesidades, deseos, ahorro.

Respuesta:

```json
{
  "mesesAnalizados": ["2026-08", "2026-09", "2026-10"],
  "actual": { "necesidades": 58.00, "deseos": 24.00, "ahorro": 18.00 },
  "sugerencia": { "necesidades": 60, "deseos": 25, "ahorro": 15 },
  "gastoSuperaIngreso": false,
  "igualALaGuia": false,
  "motivo": null
}
```

`actual` va con 2 decimales (HALF_UP). `igualALaGuia` compara la sugerencia con la configuración del usuario (falso si no la tiene). Para aceptar, la app usa el endpoint existente `POST /api/v1/presupuestos/me`.

## 6. Grupos

Tabla `grupo_plan`: `id_grupo_plan` (PK `pk_grupo_plan`), `usuario_id` (FK `fk_grupo_usuario` ON DELETE CASCADE), `nombre` VARCHAR(60) NOT NULL, `nombre_normalizado` VARCHAR(60) NOT NULL, `orden` INTEGER NOT NULL DEFAULT 0, UNIQUE `uq_grupo_usuario_nombre` (usuario_id, nombre_normalizado). RLS activo. `partida_plan.grupo_id` BIGINT NULL, FK `fk_partida_grupo` ON DELETE SET NULL, índice `idx_partida_grupo`.

Endpoints (`/api/v1/planes/grupos`): `GET` (200), `POST` `{nombre, orden?}` (201), `PUT /{id}` `{nombre?, orden?}` (200), `DELETE /{id}` (204; las partidas quedan sin grupo). Máximo 20 grupos por usuario (409).

Las partidas aceptan `grupoId` al crearse; el PUT de partida acepta `grupoId` y `quitarGrupo` (booleano), que no pueden ir juntos (400). Copiar un mes conserva el grupo.

El plan agrega `grupos`: por cada grupo del usuario, y "Sin grupo" (`id` nulo) solo si hay partidas sin grupo, al final: `{id, nombre, orden, planificado, porcentajeDelPlanificado, real, partidaIds}`. `porcentajeDelPlanificado = planificado ÷ planificadoTotal × 100` (HALF_UP; nulo si el total es 0). La suma de porcentajes puede no dar exactamente 100,00 por redondeo (no se fuerza). Ejemplo con los datos de T10c: grupo "Fijos" (Arriendo, Netflix, Spotify) 416.000, 55,76 %; grupo "Variable" (Supermercado, Transporte) 330.000, 44,24 %.

## 7. Deudas vinculadas

`partida_plan.deuda_id` BIGINT NULL, FK `fk_partida_deuda` → deuda ON DELETE SET NULL; índice único parcial `uq_partida_plan_deuda` (plan_id, deuda_id) donde `deuda_id` no es nulo.

Crear una partida con `deudaId`:
- La deuda debe existir (404), ser del usuario (403), no estar CANCELADA (409) y tener `fechaPrimeraCuota` (409: "Esta deuda no tiene fechas de cuotas.").
- Debe existir una cuota que venza dentro del mes (409 si no).
- La categoría es siempre DEUDAS: si se envía otra → 400; si se omite, se usa DEUDAS. No se envía `montoPlanificado` (si llega → 400).
- Una sola partida por deuda y mes (409 en duplicado).

Reglas:
- El monto planificado = cuota + costo adicional de la cuota del mes, calculado **en cada lectura**. Si ya no se puede calcular (por ejemplo, se editaron las condiciones de la deuda), se devuelve el último valor guardado y `vinculoDesactualizado: true`.
- `PATCH /montos` sobre una partida vinculada → 409 ("El monto de una partida de deuda lo define la cuota."). Su categoría y su deuda no se pueden cambiar (se borra y se vuelve a crear).
- Imputación: un `PAGO_DEUDA` cuya deuda tenga partida vinculada en el plan va a esa partida. Este paso va después de `partida_id` explícito y antes del cálculo por categoría (se actualiza la sección 5 de T10c en el mismo cambio).
- La partida expone `estadoCuota`: PENDIENTE (sin pagos en el mes), PARCIAL (pagado menos que el monto) o PAGADA (pagado igual o más). El campo `estado` neutro (BAJO, CERCA, SOBRE) se sigue calculando.
- Copiar un mes: las partidas vinculadas se copian solo si la deuda tiene cuota en el mes destino; las que no se copian aparecen en `avisos` del plan devuelto, con el motivo `SIN_CUOTA_EN_EL_MES`. El plan incorpora la lista `avisos` (vacía fuera de la copia).

## 8. Referencias históricas (opcional)

`GET /api/v1/planes/{mes}?incluirReferencias=true` agrega a cada elemento de `porCategoria` el campo `referencia`: `{mesAnterior, promedio3Meses}`. Es el gasto real de la categoría (suma de |monto| de `GASTO_GENERAL` y `PAGO_DEUDA`), tomando los hasta 3 meses cerrados previos al `mes`, a partir del mes del primer movimiento del usuario (así un usuario nuevo no ve promedios falsamente bajos). `promedio3Meses` con 2 decimales (HALF_UP) sobre esos meses; nulo si no hay ninguno. Una sola consulta agregada por categoría y mes. Sin el parámetro, no se incluye.

## 9. Soporte para sliders (extensión)

La app puede además recalcular localmente, al mover un slider: `planificado` y `porcentajeDelPlanificado` de cada grupo (de las partidas que cambian de grupo o de monto), y `repartoPorTipo.planificado` y `porcentajeDelIngreso`. Las fórmulas se agregan a `docs/PLAN_FORMULAS.md` y los casos a `docs/plan-vectores.json`. Las partidas vinculadas a deuda no tienen slider.

## 10. Diseño de código

- Ampliar `CalculadoraPlan` (puro) con grupos, reparto por tipo, imputación por deuda vinculada y estado de cuota.
- `service/plan/SugeridorRegla` (puro): toma la serie de meses ya agregada y devuelve la sugerencia; la carga de datos queda en el servicio.
- `service/categoria/PreferenciaTipoService` y su resolución de tipo efectivo (un único punto usado por T5, el plan y la sugerencia).
- Controladores delgados. `EliminacionDeCuentaService` incluye `preferencia_categoria_usuario`, `partida_plan` (ya) y `grupo_plan`.

## 11. Casos de prueba

Marcas: [U] puro, [I] integración, [P] PostgreSQL local.

| ID | Caso | Esperado | Tipo |
|---|---|---|---|
| D01 | `GET /preferencias` sin preferencias | Todas las categorías con tipo efectivo = por defecto | I |
| D02 | Preferir NECESIDAD para Cuidado Personal | `tipoEfectivo` NECESIDAD | I |
| D03 | Preferir tipo para SUELDO, AHORRO u OTROS | 400 DATOS_INVALIDOS | I |
| D04 | Tipo AHORRO u OTROS | 400 DATOS_INVALIDOS | I |
| D05 | Tipo "XYZ" | 400 SOLICITUD_INVALIDA | I |
| D06 | DELETE; DELETE sin preferencia | Vuelve al por defecto; 204 ambos | I |
| D07 | Preferencia de un usuario | Otro usuario no la ve ni le afecta | I |
| D08 | Gasto en la categoría preferida como NECESIDAD | La ejecución general lo cuenta en necesidades | I |
| D09 | Plan con preferencia | `porCategoria.tipo` refleja la preferencia | I |
| D10 | Categoría inexistente | 404 | I |
| D11 | Borrado de cuenta | Elimina las preferencias | I |
| D12 | Datos de referencia con guía 50/30/20 | Necesidades 73,00 % (+23,00 puntos); deseos 1,60 % (−28,40) | U |
| D13 | Sin ingreso de referencia; sin guía | Porcentajes nulos; `guia` y diferencias nulas | U |
| D14 | Real por tipo con preferencia | Suma por tipo efectivo | U |
| D15 | Partida de categoría OTROS | Aparece en el tipo OTROS | U |
| D16 | 3 meses (580/220, 600/250, 560/240 sobre 1.000) | actual 58,00 / 24,00 / 18,00; sugerencia 60 / 25 / 15 | U |
| D17 | Meses (700/500) y (720/480) sobre 1.000 | actual 59,17 / 40,83 / 0,00; sugerencia 60 / 40 / 0; `gastoSuperaIngreso` verdadero | U |
| D18 | Medianas exactas 50 / 30 / 20 con configuración igual | Sugerencia 50 / 30 / 20; `igualALaGuia` verdadero | U |
| D19 | Valores 52,5 / 27,5 / 20 | Sugerencia 55 / 25 / 20 (empate resuelto por orden) | U |
| D20 | 1 mes con ingreso; 0 meses | `sugerencia` nula, `DATOS_INSUFICIENTES` | I |
| D21 | Meses con ingreso 0; 4 meses cerrados | Se excluyen los de ingreso 0; se usan los 3 más recientes | I |
| D22 | 2 meses con necesidades 50 y 60 | Mediana 55 (promedio) | U |
| D23 | Mes en curso | No se incluye | I |
| D24 | Gasto del 31-oct 23:30 Santiago | Cuenta en octubre para la sugerencia | I |
| D25 | Sin configuración existente | `igualALaGuia` falso | I |
| D26 | Propiedad: 200 combinaciones aleatorias | La sugerencia suma 100 y son múltiplos de 5 | U |
| D27 | CRUD de grupos; nombre duplicado; más de 20; nombre de 61 caracteres | OK; 409; 409; 400 | I |
| D28 | Asignar `grupoId` ajeno; inexistente | 403; 404 | I |
| D29 | Borrar un grupo con partidas | Partidas quedan sin grupo | I |
| D30 | Datos de referencia con dos grupos | Fijos 416.000 (55,76 %); Variable 330.000 (44,24 %) | U |
| D31 | Plan con total 0 | `porcentajeDelPlanificado` nulo | U |
| D32 | "Sin grupo" | Aparece solo si hay partidas sin grupo y va al final | U |
| D33 | Copiar un mes con partidas agrupadas | Conserva `grupoId` | I |
| D34 | `quitarGrupo` junto con `grupoId` | 400 | I |
| D35 | Grupo sin partidas en el mes | Aparece con planificado 0 | U |
| D36 | Partida vinculada a una deuda con cuota 43.000 y costo adicional 500 | `montoPlanificado` 43.500; categoría DEUDAS | I |
| D37 | Deuda sin `fechaPrimeraCuota`; sin cuota en el mes; CANCELADA | 409 (los tres) | I |
| D38 | Deuda ajena; inexistente | 403; 404 | I |
| D39 | Enviar `montoPlanificado` o categoría distinta con `deudaId` | 400 | I |
| D40 | PATCH de monto sobre partida vinculada | 409 | I |
| D41 | Pago de 20.000; pago de 43.500; sin pago | PARCIAL (diferencia 23.500); PAGADA; PENDIENTE | U |
| D42 | Se editan las condiciones de la deuda y ya no hay cuota ese mes | Último valor guardado y `vinculoDesactualizado` verdadero | I |
| D43 | `PAGO_DEUDA` sin `partida_id` de una deuda con partida vinculada | Se imputa a esa partida (antes que la categoría) | U |
| D44 | Segunda partida para la misma deuda y mes | 409 | I |
| D45 | Copiar un mes con una partida vinculada y sin cuota en el destino | No se copia; aparece en `avisos` con `SIN_CUOTA_EN_EL_MES` | I |
| D46 | Partida vinculada | `estado` neutro se calcula además de `estadoCuota` | U |
| D47 | `incluirReferencias=true`; sin el parámetro | `referencia` presente; ausente | I |
| D48 | Usuario con su primer movimiento este mes | `referencia` nula | I |
| D49 | Categoría con 100.000, 120.000 y 80.000 en los 3 meses previos | `promedio3Meses` 100.000,00; `mesAnterior` 80.000,00 | U |
| D50 | Migración sobre copia de datos reales; constraints; RLS; índices | Sin errores; filas existentes intactas | P |
| D51 | `plan-vectores.json` con grupos y reparto; `openapi.json` | Regenerados y consistentes | U |

## 12. Criterios de aceptación

- [ ] Los 51 casos existen y pasan; los [U] sin Spring; los [P] contra PostgreSQL local.
- [ ] El tipo efectivo se resuelve en un solo lugar y T5, el plan y la sugerencia lo usan.
- [ ] El Paso 0 documentó cómo trata T5 la categoría OTROS y se aplicó el mismo criterio.
- [ ] Todo es informativo: ningún caso de sobreasignación, exceso o desvío produce un error HTTP.
- [ ] Documentos actualizados: `CONTEXT.md`, historias de usuario, diagrama, `PLAN_FORMULAS.md`, `plan-vectores.json`, `openapi.json`, `CLIENTE_FLUTTER.md`.
- [ ] Suite verde con UTC y Asia/Tokyo.

## 13. Fuera de alcance

Partidas de ahorro (DC14), preferencia de tipo por partida o por movimiento, provisiones anuales, notificaciones del plan, reordenar grupos en lote, imputar pagos de `/deudas/{id}/pagar` a una partida concreta.
