# Despliegue — variables de entorno de producción

Guía para configurar el servicio (Render u otro PaaS) que corre el backend con el perfil `prod`.
Fuente de verdad: `application.properties`, `application-prod.properties`, los `@Value` de
`SecurityConfig`, `JwtService`, `CorreoService`, `PasswordService`, `NotificacionScheduler`,
`RecordatorioService` y `FcmPushSender`.

> Reglas: los valores de ejemplo son **ficticios**. Nunca pegues valores reales en este
> documento ni en el repo: las variables se definen solo en el panel del proveedor (Render →
> Environment) o en tu gestor de secretos. Los secretos van como variables de entorno, no en
> archivos ni en el código.

## Obligatorias

| Nombre | Para qué sirve | Obligatoria | Formato esperado | Ejemplo (ficticio) |
|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC de PostgreSQL (Supabase Session Pooler), con TLS o el arranque falla | Sí | JDBC de Postgres con `?sslmode=require` | `jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.example:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos (Supabase) | Sí | Texto | `postgres.pooler-pico` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la base de datos; **secreto** | Sí | Texto | `**********` (rota al darse de baja del repo) |
| `JWT_SECRET` | Clave HMAC-SHA256 que firma los access token; sin ella el contexto no arranca | Sí | Texto aleatorio; recomendado ≥ 32 bytes | `7Kf9#ezPq2Xw9vYcFgYvE8qTb8Rk9NcA2Jd5Hf7Xq3Lm9TzX4Rb8NvYcFsGk5Jd` (ficticio) |

## Según la feature que se active

| Nombre | Para qué sirve | Obligatoria si | Formato esperado | Ejemplo (ficticio) |
|---|---|---|---|---|
| `PUSH_CRON_SECRET` | Credencial del cabecera `X-Cron-Secret` del trigger diario `POST /api/v1/notificaciones/recordatorios/ejecutar`. Si **queda vacía el endpoint queda cerrado (403)** | Se quiere activar recordatorios push | Texto secreto (`application.properties#app.push.cron-secret`) | `c9bf1e42-2f4a-493e-a2ba-e3a0d5f3b1c9` (ficticio) |
| `FIREBASE_CREDENTIALS` | Credenciales del service account de FCM que habilita `FcmPushSender`; el `PushSender` activo es `LogPushSender` (solo loguea) mientras no exista | Se pasa `app.push.log-only=false` en `application-prod.properties` | JSON del service account **en base64** (también acepta el JSON crudo) | `ewogICJ0eXBlIjogInNlcnZp...` (ficticio) |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | SMTP real de recuperación de contraseña; el perfil `prod` compila con `app.mail.log-only=false`, y sin `spring.mail.host` configurada el envío falla con 500 | Se apoya el flujo "olvidé mi contraseña" en prod | Host de SMTP, puerto 587, credenciales del proveedor (Brevo/Resend u otro) | `smtp.mailprovider.example` / `587` / `backend-saveup@example.com` / `**********` (ficticios) |
| `SPRING_PROFILES_ACTIVE` | Sobreescribe el perfil activo (`application.properties` ya fija `prod`) | Rara vez: solo si el PaaS la define distinto | `prod` | `prod` |
| `SERVER_PORT` | Puerto donde escucha el servidor (por defecto `8080`; algunos PaaS lo fijan por `PORT`) | Si el PaaS lo exige | Entero | `8080` |

## Ajustes dentro de `application*.properties` (no son variables) antes de publicar

- `app.mail.log-only` — en `application-prod.properties` está pre-fijado a `false`: si aún no existe SMTP,
  súbela a `true` como variable de entorno (`APP_MAIL_LOGONLY=true`) para que el token vaya solo a logs
  (aceptable mientras la app no esté pública) o deja desactivado el flujo de recuperación. Con `true` el
  token sale en el log: solo dev/test.
- `app.push.log-only` — dejado en `true` mientras no exista Firebase; al publicar, `false` +
  `FIREBASE_CREDENTIALS` (`APP_PUSH_LOGONLY=false`).
- `app.version.min-android/ios`, `app.version.latest-android/ios` — activar bloqueo/sugerencia de
  actualización (T9.4).
- `app.version.url-tienda-android/ios` — URLs reales de tienda cuando existan reemplaza los
  placeholders `cl.saveup.lite`.
- `app.cors.allowed-origins` — restringir a los dominios de la app cuando existan (hoy `*`).

## Notas

- Swagger UI y `/v3/api-docs` (T8c) quedan **deshabilitados en prod**
  (`springdoc.api-docs.enabled=false`); en dev/test siguen activos y el contrato versionado vive en
  `docs/openapi.json`.
- Flyway corre las migraciones de `db/migration/postgresql` y Hibernate no toca el esquema
  (`ddl-auto=none`): no hay SQL manual en el despliegue, solo la conexión correcta.
- Las variables del panel corresponden a propiedades con binding relajado de Spring
  (`SPRING_DATASOURCE_URL` → `spring.datasource.url`, `APP_MAIL_LOGONLY` → `app.mail.log-only`).
- Al rotar secretos: cambiar `JWT_SECRET` no revoca los access ya emitidos (durán como máximo
  15 min) y entra en vigencia al reiniciar la instancia; cambiar `PUSH_CRON_SECRET` exige
  actualizar el cron externo en la misma ventana.
