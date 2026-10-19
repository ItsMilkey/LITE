# Diagramas — SaveUp Lite (backend)

Esquemas y mapas de la arquitectura y el dominio del backend en formato [mermaid.js](https://mermaid.js.org/).
Un archivo por diagrama. Todos los bloques `mermaid` están verificados: renderizan sin error con `mermaid-cli`.

> **Commit de HEAD verificado:** `446a9f5` (`Pruebas locales`).
> El código manda sobre la documentación: cualquier desajuste se registra como **discrepancia**
> en la sección correspondiente.

## Índice

| # | Diagrama | Tipo mermaid | Qué pregunta responde | Fuentes usadas | Commit | Discrepancias |
|---|---|---|---|---|---|---|
| 01 | [Contexto](01-contexto.md) | `C4Context` | ¿Quién usa el sistema y con qué sistemas externos habla? | `.agents/rules/00-*`, `application-*.properties`, `keep_alive.yml`, `SecurityConfig` | `446a9f5` | D1 |
| 02 | [Contenedores](02-contenedores.md) | `C4Container` | ¿Qué unidades ejecutables hay y dónde corren? | `pom.xml`, `Dockerfile`, `application-*.properties`, `db/migration/postgresql/V1..V8` | `446a9f5` | D1 |
| 03 | [Componentes](03-componentes.md) | `C4Component` | ¿Qué componentes internos tiene la API y en qué capas? | `src/main/java/com/example/saveup/**` | `446a9f5` | D2 |
| 04 | [Paquetes](04-paquetes.md) | `classDiagram` | ¿Qué paquetes hay y cuál es la dirección de dependencia? | `src/main/java/com/example/saveup/**`, `.agents/rules/00-*` | `446a9f5` | — |
| 05 | [Modelo ER](05-modelo-er.md) | `erDiagram` (+ `classDiagram`) | ¿Qué tablas, FKs, cardinalidades y enums existen? | `V1..V8`, `model/**`, `model/enums/**` | `446a9f5` | D3 |
| 06 | [Despliegue](06-despliegue.md) | `C4Deployment` | ¿Cómo se construye, empaqueta y despliega? | `Dockerfile`, `application-*.properties`, `keep_alive.yml` | `446a9f5` | D1 |
| 07 | [Modelo conceptual](07-modelo-conceptual.md) | `mindmap` | ¿Cómo se relacionan los conceptos del dominio? | `docs/GLOSSARY.md`, `docs/CONTEXT.md`, `SignoMovimiento`, `service/finanzas/**` | `446a9f5` | D4 |
| 08 | [Estados](08-estados.md) | `stateDiagram-v2` | ¿Cómo evolucionan `EstadoDeuda` y `EstadoEjecucion`? | `enums/**`, `DeudaService`, `PresupuestoService` | `446a9f5` | — |
| 09 | [Flujo: auth](09-flujo-auth.md) | `sequenceDiagram` | ¿Cómo se registra, loguea y valida un JWT? | `UsuarioController`, `UsuarioService`, `security/**`, `SecurityConfig` | `446a9f5` | — |
| 10 | [Flujo: Smart-Split](10-flujo-movimiento-smart-split.md) | `sequenceDiagram` | ¿Cómo un ingreso reparte abonos a metas? | `MovimientoService`, `SmartSplitProcessor`, `SignoMovimiento` | `446a9f5` | — |
| 11 | [Flujo: pago de deuda](11-flujo-pago-deuda.md) | `sequenceDiagram` | ¿Cómo se paga una deuda y se imputan los pagos? | `DeudaController`, `DeudaService`, `ImputacionPagos`, `SignoMovimiento` | `446a9f5` | — |
| 12 | [Flujo: simulación](12-flujo-simulacion.md) | `sequenceDiagram` | ¿Cómo se simula un crédito sin persistir? | `SimulacionController`, `SimulacionService`, `service/finanzas/**`, `PorcentajeTasa` | `446a9f5` | — |
| 13 | [Flujo: presupuesto](13-flujo-presupuesto.md) | `sequenceDiagram` | ¿Cómo se calcula 50/30/20, la ejecución y el historial? | `ConfiguracionPresupuestoController`, `PresupuestoService`, `FechasMovimiento`, `EstadoEjecucion` | `446a9f5` | — |
| 14 | [Flujo: abono a meta](14-flujo-abono-meta.md) | `sequenceDiagram` | ¿Cómo se abona, retira y reintegra una meta? | `MetaAhorroController`, `MetaAhorroService`, `SignoMovimiento`, `MetaAhorroRepository` | `446a9f5` | — |
| 15 | [Casos de uso](15-casos-de-uso.md) | `flowchart` | ¿Qué casos de uso cubre cada módulo? | `docs/CONTEXT.md`, `controller/**`, `docs/tareas/T0..T5`, `T1d` | `446a9f5` | — |

## Validación

Cada bloque `mermaid` se extrajo a un `.mmd` temporal y se renderizó con:

```bash
npx -y @mermaid-js/mermaid-cli -i temp.mmd -o temp.svg
```

Salida esperada: `Generating single mermaid chart` y `exit code 0` por cada diagrama (sin mensajes de error del parser).

Plan B (si no hubiera red o Chromium): revisión manual de sintaxis contra la especificación de mermaid y
confirmación visual con el preview del navegador. No fue necesario: `mmdc` quedó disponible.

## Fuentes de verdad (orden de prioridad)

1. `src/main/java/com/example/saveup/**` (manda sobre todo lo demás).
2. `src/test/java/com/example/saveup/**` (comportamiento aceptado).
3. `.agents/rules/{00-contexto-proyecto,20-java-spring,30-flyway,40-calculo-financiero}.md`.
4. `docs/CONTEXT.md`, `docs/GLOSSARY.md`.
5. `docs/tareas/T0..T5`, `T1d`, `docs/tareas/BACKLOG.md`.
6. `C:\Users\hanom\.opencode\plan\saveup-lite-c4.md` (plan C4 previo, desfasado).

Si el código contradice a un doc, gana el código y se anota abajo.

## Discrepancias código ↔ doc

- **D1 — Motor de base de datos del plan C4 previo.** `C:\Users\hanom\.opencode\plan\saveup-lite-c4.md` describe
  Oracle 23c Autonomous Database en dev, `DataSourceConfig` con pool Oracle UCP y wallet TLS, y migraciones en
  `db/migration/oracle`. El código en HEAD **no** tiene nada de eso: `application-dev.properties` y
  `application-prod.properties` apuntan a **PostgreSQL/Supabase** (`PostgreSQLDialect`,
  `spring.flyway.locations=classpath:db/migration/postgresql`), no existe `DataSourceConfig.java` ni la carpeta
  `db/migration/oracle`. Confirmado además por `.agents/rules/00-contexto-proyecto.md`. **Gana el código.**
- **D2 — Componentes inexistentes en el plan previo.** El plan menciona `DeudaResponseMapper` dentro de
  `service.finanzas` y `DataSourceConfig` en `config`. Ninguno existe en HEAD: los campos derivados de
  `DeudaResponseDTO` se calculan en `DeudaService.enriquecer(...)`. **Gana el código.**
- **D3 — Recuento de tablas y enums.** El plan previo hablaba de 7 tablas y 5 enumerados y de un alcance T0–T3.
  HEAD tiene **8 tablas** (V6 añade `presupuesto_categoria`) y **7 enums** (suma `EstadoEjecucion` y `ModoPago`),
  con alcance T0–T5 (+ T1d). **Gana el código.**
- **D4 — "CAE" ambiguo.** `docs/GLOSSARY.md` advierte que "CAE" designa dos cosas (Carga Anual Equivalente y el
  Crédito con Aval del Estado). En el código el indicador financiero se llama siempre `cargaAnualEquivalente`;
  los diagramas respetan esa denominación.
- **D5 — Migraciones al día (V8).** Los diagramas se verificaron con `V1..V7`; el HEAD actual añade
  `V8__normalizar_constraints_indices_rls.sql`, que renombra los constraints a minúscula, valida
  `chk_movimiento_signo`, crea 6 índices de FK/filtro y activa RLS en las 8 tablas de dominio. No cambia
  el esquema lógico (tablas, FKs, enums), solo su coherencia y su seguridad; el rango de fuentes ya dice
  `V1..V8`. **Gana el código.**
- **D6 — Cliente del frontend.** Los diagramas describen un cliente "Ionic / Angular / Capacitor"; el
  frontend de producto se implementará en **Flutter**. Los orígenes siguen siendo los declarados en
  `app.cors.allowed-origins` (`localhost:8100`, `localhost:4200`, `capacitor://localhost`, `localhost`):
  todavía **no** hay un origen Flutter en la configuración.

## "Por confirmar"

- **Orígenes del cliente frontend:** no hay código de frontend en este repositorio. El cliente de producto
  será una app **Flutter** (ver D6); los orígenes representados provienen solo de `app.cors.allowed-origins`
  y no incluyen todavía un origen Flutter.
- **Comentario desactualizado en `application.properties`:** el texto dice "Activa el perfil dev", pero el valor
  real es `spring.profiles.active=prod` (dev y prod usan el mismo PostgreSQL, así que no cambia el modelo de datos).
- Los diagramas no representan un sistema externo de reporting ni colas/mensajería porque **no existen** en el código.

## Historias de usuario

- Catálogo en [`../historias-de-usuario/README.md`](../historias-de-usuario/README.md): **24 / 24** casos de uso
  cubiertos (UC1–UC24), con trazabilidad HU ↔ caso de uso ↔ endpoint ↔ tarea y marca de estado
  (**implementada / parcial / backlog**).
