# 06 — Diagrama de despliegue

**Tipo mermaid:** `C4Deployment` · **Estado:** completo (verificado contra el código en HEAD `9e9385e`)

## Qué responde
Dónde corre la aplicación: construcción con Maven, imagen Docker, servicio en Render y base de datos Supabase, sin exponer valores de credenciales.

## Fuentes
- `Dockerfile` (multi-etapa `maven:3.9.6-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`)
- `src/main/resources/application.properties`, `application-dev.properties`, `application-prod.properties`
- `.github/workflows/keep_alive.yml`
- `.agents/rules/00-contexto-proyecto.md`

## Diagrama

```mermaid
C4Deployment
    title SaveUp Lite - Despliegue

    Deployment_Node(dev, "Entorno de desarrollo", "Windows / Linux / macOS con Java 21 y Maven Wrapper") {
        Container(build, "Build Maven", "mvn package -DskipTests", "Compila y empaqueta saveup-0.0.1-SNAPSHOT.jar")
    }

    Deployment_Node(docker, "Imagen Docker", "eclipse-temurin 21-jre-alpine") {
        Container(apiProd, "Aplicacion Spring Boot", "Perfil prod, puerto 8080", "Ejecuta java -jar app.jar y expone la API REST")
    }

    Deployment_Node(render, "Render - Web Service", "PaaS que hospeda la imagen") {
        Container(apiRun, "Contenedor en ejecucion", "Spring Boot 3.3.0 / Java 21", "Lee las variables de entorno y aplica las migraciones Flyway al arrancar")
    }

    Deployment_Node(supabase, "Supabase - PostgreSQL gestionado", "Session Pooler con SSL obligatorio") {
        ContainerDb(pg, "Base de datos SaveUp", "PostgreSQL", "Esquema de 8 tablas versionado por Flyway (V1..V7)")
    }

    Deployment_Node(gh, "GitHub Actions", "Runner ubuntu-latest") {
        Container(cron, "keep_alive", "cron */14 * * * *", "HEAD al servicio de Render para evitar el cold start")
    }

    Rel(build, apiProd, "Produce el jar que empaqueta la imagen", "Docker multi-etapa")
    Rel(apiProd, apiRun, "Se despliega como imagen", "Docker")
    Rel(apiRun, pg, "Lee y escribe el dominio", "JDBC / SSL")
    Rel(cron, apiRun, "Ping HEAD /", "HTTPS")
```

## Variables de entorno (solo nombres, sin valores)
La aplicación lee sus secretos del entorno; **no** se escriben valores en el repositorio ni en este documento.

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `SPRING_PROFILES_ACTIVE` (opcional; `application.properties` fija `prod` por defecto)

## Notas y "por confirmar"
- **Un solo entorno de base de datos**: dev y prod usan PostgreSQL/Supabase (`spring.flyway.locations=classpath:db/migration/postgresql`) y en ambos `ddl-auto` es `validate` (dev) o `none` (prod): Hibernate no crea el esquema, lo hace Flyway.
- El `Dockerfile` **no** define variables de entorno ni secretos: se inyectan en Render.
- El JWT expira a las 24 h por defecto (`jwt.expirationInMs=86400000`).
- El keep-alive existe porque Render suspende el servicio tras 15 min; el workflow pingeaa cada 14 min.
