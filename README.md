# SaveUp Lite — backend

API REST de finanzas personales (presupuesto 50/30/20, movimientos, metas, deudas, plan mensual y reportes) construida con Spring Boot. El cliente Flutter consume el contrato versionado de `docs/openapi.json`.

## Stack

- Java 21 · Spring Boot 3.3 · Maven
- PostgreSQL (Supabase) + Flyway (`V1`–`V17`)
- JWT stateless + refresh rotativo, BCrypt, Problem Details (RFC 7807)
- OpenAPI 3 (springdoc; deshabilitado en producción, contrato versionado en el repo)
- Tests JUnit 5, H2 para integración y PostgreSQL 16 (Testcontainers) para migraciones

## Arranque local

```powershell
# Variables mínimas (ver docs/DESPLIEGUE.md para el detalle y producción)
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/saveup?sslmode=disable"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
$env:JWT_SECRET="<base64 de 48 bytes aleatorios>"

.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080/api/v1`; el contrato se publica en `/v3/api-docs` (solo dev).

## Tests

```powershell
.\mvnw.cmd test "-Dtest.timezone=UTC"   # suite H2 (UTC)
.\mvnw.cmd test -Ppostgres "-Dtest=*PostgresTest"   # Flyway y constraints en PostgreSQL 16 (Docker)
```

La suite H2 excluye los tests etiquetados `postgres`; la corrida con `-Ppostgres` requiere Docker.

Prueba de humo end-to-end (requiere la API arriba y la BD migrada por Flyway, con las 18 categorías):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

Cubre registro/login, saldo, movimientos, deudas, simulación, filtros, presupuesto por categoría, plan del mes, grupos, preferencias y sugerencias (T5/T10).

## Documentación

| Documento | Para qué |
|---|---|
| [docs/CONTEXT.md](docs/CONTEXT.md) | Mapa del dominio: entidades, endpoints, reglas vigentes y errores |
| [docs/CLIENTE_FLUTTER.md](docs/CLIENTE_FLUTTER.md) | Guía de consumo para la app Flutter (auth, dinero, plan, errores, push) |
| [docs/openapi.json](docs/openapi.json) | Contrato OpenAPI versionado (se regenera con `-Dopenapi.update=true`) |
| [docs/PLAN_FORMULAS.md](docs/PLAN_FORMULAS.md) | Fórmulas del plan mensual y contrato de cálculo local |
| [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md) | Variables de entorno, despliegue, CI y activación de correo/push |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Glosario financiero y de planificación |
| [docs/diagramas/](docs/diagramas/README.md) | Arquitectura C4, modelo ER, estados y flujos |
| [docs/historias-de-usuario/](docs/historias-de-usuario/README.md) | Historias de usuario y matriz de trazabilidad |
| [docs/tareas/](docs/tareas/) | Especificaciones por tarea, decisiones de T10 y backlog |

`docs/DOCUMENTACION_COMPLETA_SAVEUP_LITE.txt` es un documento histórico (T0–T5) y no refleja el estado actual.

## Estructura

```
src/main/java/com/example/saveup/
  controller/  service/  repository/  model/  dto/  error/  security/  config/
src/main/resources/
  db/migration/postgresql/   application*.properties   categorias/
src/test/java/com/example/saveup/
  controller/  service/  migration/  ...
docs/   # contrato, guías, diagramas, historias, tareas
```

## Estado

T0–T10 completas (motor financiero, deudas, presupuesto, movimientos, OpenAPI, sesión móvil, notificaciones y plan mensual). Las funciones de correo y push están implementadas pero desactivadas por defecto; ver [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md) y [docs/tareas/BACKLOG.md](docs/tareas/BACKLOG.md).
