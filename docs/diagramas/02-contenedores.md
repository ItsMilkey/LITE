# 02 — Diagrama de contenedores (C4 Nivel 2)

**Tipo mermaid:** `C4Container` · **Estado:** completo (verificado contra el código en HEAD `446a9f5`)

## Qué responde
Dentro de la caja negra del sistema: qué unidades ejecutables/desplegables existen y con qué hablan.

## Fuentes
- `pom.xml` (`artifactId=saveup`, `version=0.0.1-SNAPSHOT`, Spring Boot 3.3.0, Flyway 10)
- `Dockerfile` (multi-etapa: `maven:3.9.6-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`)
- `src/main/resources/application-*.properties`
- `src/main/resources/db/migration/postgresql/V1..V8`
- `.github/workflows/keep_alive.yml`

## Diagrama

```mermaid
C4Container
    title SaveUp Lite - Nivel 2: Contenedores

    Person(usuario, "Usuario", "Persona que gestiona sus finanzas")

    System_Boundary(plataforma, "Plataforma SaveUp Lite") {
        Container(api, "Aplicacion Spring Boot", "Spring Boot 3.3.0 / Java 21 / Flyway 10", "API REST de finanzas personales. Artefacto unico: saveup-0.0.1-SNAPSHOT.jar. Incluye Spring MVC, Spring Data JPA, Spring Security y OpenPDF. Puerto 8080.")
        ContainerDb(db, "Base de datos PostgreSQL", "PostgreSQL - Supabase", "Esquema de 8 tablas creado y versionado por Flyway (V1..V8 en db/migration/postgresql). Solo la aplicacion accede a ella.")
    }

    System_Ext(cliente, "App movil / Frontend", "App movil en Flutter que consume la API REST (los origenes CORS actuales son de un cliente Ionic/Angular legacy)")
    System_Ext(cron, "GitHub Actions - keep_alive", "Cron cada 14 minutos que hace HEAD al servicio de Render")

    Rel(usuario, cliente, "Usa")
    Rel(cliente, api, "Llama la API REST", "HTTPS + JSON + JWT")
    Rel(api, db, "Lee y escribe entidades JPA", "JDBC / SSL")
    Rel(cron, api, "HEAD / para evitar el cold start", "HTTPS")
```

## Notas y "por confirmar"
- El contenedor de base de datos es **único** (mismo Supabase PostgreSQL en dev y prod): la configuración de ambos perfiles apunta a `classpath:db/migration/postgresql`.
- Los **tests** usan H2 en memoria con `MODE=Oracle` y `flyway.enabled=false` (`src/test/resources`), por lo que no dependen de la base real; no se representan como contenedor de producción.
- La aplicación se empaqueta como **un solo jar ejecutable**; no hay contenedores separados por módulo funcional.
