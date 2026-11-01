# T10 — Planificador de presupuesto: decisiones, capas y reglas comunes

Estado: v2. Reemplaza al borrador T10 v1. Este documento es el índice: las especificaciones T10a a T10d lo referencian y no repiten sus reglas.
Principio de trabajo: nada de lo que no esté escrito aquí o en las especificaciones se asume. Si el código real contradice un supuesto, el agente se detiene y reporta (Paso 0 de cada tarea).

## 1. Objetivo

Que el usuario planifique su mes con partidas (líneas con nombre, categoría y monto), vea cómo se reparte su ingreso de referencia, y compare el plan con lo que realmente gastó, sin que la app lo juzgue.

Los sliders del frontend previsualizan en el teléfono sin llamar al servidor; el guardado es un solo lote atómico.

## 2. Las tres capas

| Capa | Qué es | Estado |
|---|---|---|
| Guía | Regla de porcentajes (50/30/20 por defecto, personalizable) | Existe (`/presupuestos/me`) |
| Plan | Partidas del mes con monto planificado | Nueva (T10c) |
| Realidad | Movimientos reales | Existe |

Cadena de clasificación: Movimiento → Categoría → Tipo (NECESIDAD, DESEO, AHORRO, OTROS) → Guía. La partida se apoya en una categoría del catálogo y por eso hereda su tipo.

## 3. Decisiones cerradas

| ID | Decisión |
|---|---|
| DC1 | Las categorías son un catálogo predefinido. No hay categorías personalizadas. El catálogo final tiene 18 filas (T10a). |
| DC2 | Una partida tiene nombre libre, una categoría del catálogo (menú desplegable) y un monto planificado. |
| DC3 | Varias partidas pueden compartir categoría. |
| DC4 | Un movimiento puede llevar `partidaId`; en ese caso su categoría se deriva de la partida (no se clasifica dos veces). |
| DC5 | Las deudas se mantienen como NECESIDAD. No se agrega el tipo DEUDA. |
| DC6 | Estados neutros: BAJO_EL_PLAN (menos de 80 %), CERCA_DEL_PLAN (de 80 % a 100 % inclusive), SOBRE_EL_PLAN (más de 100 %). Son informativos: ninguno es un error. Sobreasignar el ingreso tampoco lo es. |
| DC7 | "Cuidado Personal y Vestuario" es DESEO por defecto. El usuario puede reclasificarlo (T10d). |
| DC8 | Toda categoría tiene un `codigo` estable; el código del backend busca por `codigo`, no por nombre. |
| DC9 | El sugeridor de categoría es determinista y explicable, sin IA externa. Nunca asigna en silencio: solo preselecciona. |
| DC10 | Slider: vista previa local en la app, guardado por lote atómico con montos absolutos, último en llegar gana. |
| DC11 | Los meses no se cierran: todos son editables. |
| DC12 | Copiar el mes anterior es una operación explícita (POST). |
| DC13 | Un gasto que no se puede imputar a una partida va a "Sin planificar", con su motivo. |
| DC14 | Las partidas de ahorro (categoría AHORRO) quedan fuera de T10c y T10d: el ahorro se muestra como informativo. Se decide después de auditar cómo se categorizan hoy los abonos a metas. |
| DC15 | Las partidas no usan la categoría SUELDO (es de ingresos). |
| DC16 | Un movimiento solo se imputa a una partida del mes de su fecha. La fecha de un movimiento la pone el servidor, así que desde la API solo se puede imputar al mes en curso (limitación conocida; ver BACKLOG: fecha opcional). |
| DC17 | El plan convive con `PresupuestoCategoria` (T5), que queda como legado y se documenta como tal. No se modifica salvo lo indicado en T10a y T10d. |

## 4. Nombres

| Concepto | Código y base de datos | API | Etiqueta sugerida en la app |
|---|---|---|---|
| Regla de porcentajes | `ConfiguracionPresupuesto` (existe) | `/presupuestos/me` | "Regla 50/30/20" |
| Categoría | `Categoria` (existe) | `/categorias` | "Categoría" |
| Plan del mes | `PlanMensual`, tabla `plan_mensual` | `/planes/{mes}` | "Plan del mes" |
| Partida | `PartidaPlan`, tabla `partida_plan` | `/planes/{mes}/partidas` | "Partida" |
| Grupo (T10d) | `GrupoPlan`, tabla `grupo_plan` | `/planes/grupos` | "Grupo" |
| Resto no asignado | `remanenteLibre` | en el resumen | "Libre" |
| Gasto fuera del plan | `sinPlanificar` | en el resumen | "Sin planificar" |

"Presupuesto" queda reservado para lo existente (regla y límites por categoría). Lo nuevo usa "plan" para evitar confundir a personas y agentes.

## 5. Entregas y orden

| Tarea | Contenido | Depende de |
|---|---|---|
| T10a | Catálogo ampliado, `codigo` estable, estados neutros | T5 |
| T10b | Sugeridor de categoría | T10a |
| T10c | Plan, partidas, `partidaId` en movimientos, cálculo con vectores, guardado por lote, copia de mes | T10a |
| T10d | Preferencia de tipo, reparto por tipo, sugerencia de regla, grupos, deudas vinculadas, referencias | T10c |

T10b y T10c se pueden hacer en paralelo. T10c ofrece al frontend el contrato y los vectores de prueba desde el principio, para que pueda empezar con datos de ejemplo.

## 6. Reglas comunes (G1 a G10)

- **G1 Fechas.** Zona del negocio America/Santiago, constante de `FechasMovimiento`. Instantes en UTC con Z; fechas de calendario `yyyy-MM-dd`; meses `yyyy-MM`. Ningún `ZoneId.systemDefault()`.
- **G2 Dinero.** `BigDecimal`, escala 2, HALF_UP. Entradas con `@Digits(integer=13, fraction=2)`. Nada de `double`.
- **G3 Errores.** Solo los códigos existentes de `CodigoError`; no se agregan nuevos. Mensajes de usuario con `DatosInvalidosException` (400) o `ErrorNegocio` (409), en español, sin clases, SQL ni rutas.
- **G4 Base de datos.** Siguiente migración disponible (se verifica en el Paso 0; no se edita ninguna existente). Constraints e índices en minúscula, sin comillas. RLS activo en toda tabla nueva. FK hacia `usuario` con `ON DELETE CASCADE` (patrón V10) y las tablas nuevas incluidas en `EliminacionDeCuentaService` (orden hijo antes que padre). Ningún DDL sobre `flyway_schema_history`.
- **G5 Contrato.** Rutas bajo `/api/v1`, con `@Tag`, `@Operation` y `operationId` únicos. Regenerar `docs/openapi.json` (`-Dopenapi.update=true`). Actualizar `docs/CLIENTE_FLUTTER.md`.
- **G6 Pruebas.** Suite con `-Dtest.timezone=UTC` y con `-Dtest.timezone=Asia/Tokyo`. Los casos de las tablas son requisitos: cada fila es un test. Los cálculos viven en clases puras con pruebas unitarias sin Spring. Las migraciones se prueban también contra PostgreSQL local (base nueva y base con datos).
- **G7 Propiedad.** El usuario sale siempre del token. Recurso ajeno: 403. Recurso inexistente: 404.
- **G8 Paso 0.** Cada tarea empieza con una auditoría de solo lectura y espera el "ok". Si algo contradice la especificación, se detiene.
- **G9 Seguridad.** Sin credenciales ni `.env`. No se registran en logs textos libres del usuario (nombres de partidas).
- **G10 Documentación.** Actualizar `docs/CONTEXT.md`, agregar la historia de usuario (formato de HU-05) y el diagrama de secuencia (formato del diagrama 13) cuando corresponda.

## 7. Preguntas abiertas (responder antes de implementar)

1. DC14: ¿las partidas de ahorro entran en una entrega posterior? Se necesita saber cómo se categorizan hoy los abonos a metas (lo revisa el Paso 0 de T10c).
2. Íconos y colores de las categorías nuevas (T10a): ¿se aceptan los propuestos?
3. T5 y la categoría OTROS: ¿cómo cuenta hoy en la ejecución general? T10d lo alinea con T5 (se verifica en su Paso 0).

## 8. Fuera de alcance de todo T10

Provisiones de gastos anuales (comparar contra lo acumulado en el año), ahorro por partida, fecha opcional en movimientos, bloqueo optimista, notificaciones del plan, monedas distintas de CLP, categorías personalizadas, IA externa.
