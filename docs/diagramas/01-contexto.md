# 01 — Diagrama de contexto (C4 Nivel 1)

**Tipo mermaid:** `C4Context` · **Estado:** completo (verificado contra el código en HEAD `446a9f5`)

## Qué responde
Quién usa SaveUp Lite, qué es el sistema como caja negra y con qué sistemas externos reales se relaciona.

## Fuentes
- `.agents/rules/00-contexto-proyecto.md` (BD: Supabase PostgreSQL en dev y prod)
- `src/main/resources/application-dev.properties` y `application-prod.properties`
- `.github/workflows/keep_alive.yml`
- `src/main/java/com/example/saveup/config/SecurityConfig.java` (API stateless + JWT)
- `docs/CONTEXT.md`

## Diagrama

```mermaid
C4Context
    title SaveUp Lite - Nivel 1: Contexto

    Person(usuario, "Usuario", "Persona que gestiona sus finanzas personales: movimientos, metas de ahorro, deudas y presupuesto.")

    System(saveup, "SaveUp Lite", "Backend REST de finanzas personales. Spring Boot 3.3.0 / Java 21. API stateless con JWT. Expone /api/usuarios, /api/movimientos, /api/saldos, /api/metas, /api/deudas, /api/presupuestos, /api/reportes y /api/simulaciones.")

    System_Ext(cliente, "App movil / Frontend", "Cliente HTTP (app movil en Flutter). Origenes declarados hoy en CORS: localhost:8100, localhost:4200, capacitor://localhost y localhost (pendientes de actualizar para el cliente Flutter).")

    System_Ext(supabase, "PostgreSQL - Supabase", "Base de datos unica de dev y prod (Session Pooler, sslmode=require). Esquema de 8 tablas versionado por Flyway 10.")

    System_Ext(cron, "GitHub Actions - keep_alive", "Workflow programado que hace HEAD al servicio de Render cada 14 minutos para evitar el cold start.")

    Rel(usuario, cliente, "Usa")
    Rel(cliente, saveup, "Consume la API REST en JSON", "HTTPS + Bearer JWT")
    Rel(saveup, supabase, "Lee y escribe el dominio", "JDBC / SSL")
    Rel(cron, saveup, "Mantiene el servicio despierto", "HTTPS HEAD /")
```

## Notas y "por confirmar"
- La API es **stateless**: `SecurityConfig` fija `SessionCreationPolicy.STATELESS`, `csrf` deshabilitado y todo endpoint salvo `register`, `login`, `OPTIONS` y `GET/HEAD /` exige JWT válido.
- Los **reportes** (PDF/CSV) se generan dentro de la misma aplicación (`service.report`), sobre `byte[]` en memoria; no hay sistema externo de reporting.
- Las **simulaciones** (`POST /api/simulaciones/credito`) no tocan la base de datos: `SimulacionService` solo ejecuta el motor financiero y devuelve un DTO.
- El **cliente frontend no vive en este repositorio**; solo se conocen sus orígenes por `app.cors.allowed-origins`.
- Discrepancia documentada: `docs/diagramas/README.md` recoge que el plan C4 previo (`saveup-lite-c4.md`) describía **Oracle** como motor de desarrollo, lo que el código actual ya no refleja (dev y prod usan PostgreSQL/Supabase).
