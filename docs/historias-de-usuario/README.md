# Historias de usuario — SaveUp Lite

Catálogo de historias de usuario (HU) del **producto** SaveUp Lite: una app móvil en **Flutter**
que consume la API REST de este repositorio.

Cada historia traza a un **caso de uso** de [`../diagramas/15-casos-de-uso.md`](../diagramas/15-casos-de-uso.md),
a su **endpoint** en [`../CONTEXT.md`](../CONTEXT.md) y a su **tarea** en [`../tareas/`](../tareas/).

> **El código manda.** Fuentes de verdad en orden de prioridad: `src/main` > `src/test` > `.agents/rules/*` >
> `docs/CONTEXT.md` / `docs/GLOSSARY.md` > `docs/tareas/*`. Una historia que contradiga el código está mal
> y se corrige (se anota como discrepancia en [`../diagramas/README.md`](../diagramas/README.md)).

## Actores

| Actor | Descripción |
|---|---|
| **Visitante** | Persona sin sesión. Solo puede registrarse e iniciar sesión. |
| **Usuario** | Persona autenticada con JWT. Gestiona sus movimientos, metas de ahorro, deudas y presupuesto. Todo recurso se accede por su RUT del token (anti-IDOR). |

## Formato de una historia

```
### HU-<módulo>-<n> — <Título>
**Como** <actor>, **quiero** <capacidad>, **para** <beneficio>.
- Criterios de aceptación (bullets, derivados de CONTEXT.md §Reglas vigentes)
- Trazabilidad: <UC> · <endpoint(s)> · <tarea>
- Acción en la app (Flutter): <pantalla o gesto>
- Estado: implementada | parcial | backlog
```

## Leyenda de estado

| Estado | Significado |
|---|---|
| **implementada** | El endpoint existe en la API (HEAD `446a9f5`) y tiene criterios verificables con tests. |
| **parcial** | Parte del comportamiento existe; el resto depende del cliente o de otra pieza. |
| **backlog** | No implementada; registrada en [`../tareas/BACKLOG.md`](../tareas/BACKLOG.md). |

## Matriz de trazabilidad

| HU | Caso de uso | Endpoint(s) | Tarea | Estado |
|---|---|---|---|---|
| HU-01-01 | UC1 Registrarse | `POST /api/v1/usuarios/register` | base | implementada |
| HU-01-02 | UC2 Iniciar sesión | `POST /api/v1/usuarios/login` | base | implementada |
| HU-01-03 | UC3 Ver perfil | `GET /api/v1/usuarios/me` | base | implementada |
| HU-01-04 | — | (sin endpoint; stateless) | base | parcial |
| HU-01-05 | UC1 Registrarse | `POST /api/v1/usuarios/register` | base | implementada |
| HU-01-06 | UC2 Iniciar sesión | `POST /api/v1/usuarios/login` | base | implementada |
| HU-01-07 | (todas las rutas) | `SecurityConfig` | base | implementada |
| HU-02-01 | UC4 Registrar movimiento | `POST /api/v1/movimientos` | base, T1d | implementada |
| HU-02-02 | UC5 Ver y filtrar historial | `GET /api/v1/movimientos/me`, `GET /api/v1/movimientos/paginados/me` | base, T4 | implementada |
| HU-02-03 | UC6 Editar o eliminar movimiento | `PUT/DELETE /api/v1/movimientos/{id}` | T4 | implementada |
| HU-02-04 | UC7 Consultar saldo | `GET /api/v1/saldos/me` | base | implementada |
| HU-02-05 | UC8 Ver categorías | `GET /api/v1/categorias` | base | implementada |
| HU-02-06 | UC4 Registrar movimiento | `POST /api/v1/movimientos` | T1d | implementada |
| HU-02-07 | UC6 Editar o eliminar movimiento | `PUT/DELETE /api/v1/movimientos/{id}` | T4 | implementada |
| HU-02-08 | UC5 Ver y filtrar historial | `GET /api/v1/movimientos/paginados/me` | T4 | implementada |
| HU-03-01 | UC9 Crear meta | `POST /api/v1/metas` | base | implementada |
| HU-03-02 | UC10 Abonar a meta | `POST /api/v1/metas/{id}/abonar` | T1d | implementada |
| HU-03-03 | UC11 Retirar de meta | `POST /api/v1/metas/{id}/retirar` | T1d | implementada |
| HU-03-04 | UC12 Editar meta | `PUT /api/v1/metas/{id}` | base | implementada |
| HU-03-05 | UC13 Eliminar meta con reintegro | `DELETE /api/v1/metas/{id}` | T1d | implementada |
| HU-03-06 | UC10, UC11 Abonar/retirar | `/api/v1/metas/{id}/abonar`, `/retirar` | T1d | implementada |
| HU-04-01 | UC14 Simular crédito | `POST /api/v1/simulaciones/credito` | T1a, T1b | implementada |
| HU-04-02 | UC15 Registrar deuda | `POST /api/v1/deudas` | T1c | implementada |
| HU-04-03 | UC16 Pagar deuda | `POST /api/v1/deudas/{id}/pagar` | T2 | implementada |
| HU-04-04 | UC17 Editar o cancelar deuda | `PUT /api/v1/deudas/{id}`, `PATCH /api/v1/deudas/{id}/cancelar` | T1c, T2 | implementada |
| HU-04-05 | UC18 Ver detalle, amortización e historial | `GET /api/v1/deudas/{id}`, `.../amortizacion`, `.../pagos` | T3 | implementada |
| HU-04-06 | — (backlog) | (sin endpoint) | T6 | backlog |
| HU-04-07 | UC16 Pagar deuda | `POST /api/v1/deudas/{id}/pagar` | T2 | implementada |
| HU-04-08 | UC17 Editar o cancelar deuda | `PATCH /api/v1/deudas/{id}/cancelar` | T2 | implementada |
| HU-04-09 | UC17 Editar o cancelar deuda | `PUT /api/v1/deudas/{id}` | T1c, T2 | implementada |
| HU-05-01 | UC19 Configurar 50/30/20 | `GET/POST /api/v1/presupuestos/me` | base | implementada |
| HU-05-02 | UC20 Ver ejecución por tipo | `GET /api/v1/presupuestos/ejecucion/me` | base | implementada |
| HU-05-03 | UC21 Definir límites por categoría | `GET/PUT /api/v1/presupuestos/categorias/me` | T5 | implementada |
| HU-05-04 | UC22 Ver ejecución por categoría | `GET /api/v1/presupuestos/categorias/ejecucion/me` | T5 | implementada |
| HU-05-05 | UC23 Ver historial mensual | `GET /api/v1/presupuestos/historial/me` | T5 | implementada |
| HU-05-06 | (extiende UC4/UC19) Smart-Split | (interno de `POST /api/v1/movimientos`) | base | implementada |
| HU-05-07 | UC19 Configurar 50/30/20 | `POST /api/v1/presupuestos/me` | base | implementada |
| HU-05-08 | UC21 Definir límites por categoría | `PUT /api/v1/presupuestos/categorias/me` | T5 | implementada |
| HU-05-09 | UC23 Ver historial mensual | `GET /api/v1/presupuestos/historial/me` | T5 | implementada |
| HU-06-01 | UC24 Exportar movimientos | `GET /api/v1/reportes/movimientos/exportar` | base | implementada |
| HU-06-02 | — (backlog) | (sin endpoint) | T8 | backlog |
| HU-06-03 | UC24 Exportar movimientos | `GET /api/v1/reportes/movimientos/exportar` | base | implementada |
| HU-07-01 | (todas las rutas) | `security/**` | base | implementada |
| HU-07-02 | (todos los módulos) | `security/**` | base | implementada |
| HU-07-03 | (todos los módulos) | `security/**` | base | implementada |
| HU-07-04 | (todos los módulos) | `.agents/rules/00-*` | base | implementada |
| HU-07-05 | — | `keep_alive.yml` | base | implementada |

## Cobertura

- **Casos de uso cubiertos: 24 / 24.** Cada UC1–UC24 tiene al menos una HU.
- HU adicionales sin caso de uso propio: HU-01-04 (cerrar sesión), HU-05-06 (Smart-Split) y toda la épica **HU-07** (transversal).
- **Total: 47 historias de usuario.**

## Épicas

| Archivo | Módulo | Casos de uso |
|---|---|---|
| [HU-01-cuenta.md](HU-01-cuenta.md) | Cuenta y seguridad | UC1–UC3 |
| [HU-02-movimientos.md](HU-02-movimientos.md) | Movimientos y saldo | UC4–UC8 |
| [HU-03-metas.md](HU-03-metas.md) | Metas de ahorro | UC9–UC13 |
| [HU-04-deudas.md](HU-04-deudas.md) | Deudas | UC14–UC18 |
| [HU-05-presupuesto.md](HU-05-presupuesto.md) | Presupuesto | UC19–UC23 |
| [HU-06-reportes.md](HU-06-reportes.md) | Reportes | UC24 |
| [HU-07-transversal.md](HU-07-transversal.md) | Transversal (seguridad y calidad) | — |

## Reglas transversales (aplican a toda HU)

- Identidad **solo** desde el JWT (`SecurityUtils.getAuthenticatedRut()`); nunca un `usuarioRut` en el body.
- Todo recurso accedido por ID valida propiedad: **ajeno → 403**.
- Dinero en `BigDecimal`, escala 2, `RoundingMode.HALF_UP`. Prohibido `double`/`Double`.
- Regla de negocio violada → **409**; validación → **400**; sin token → **401**.
- Cada HU debe poder verificarse con un test JUnit 5 que no dependa de la base de datos.
