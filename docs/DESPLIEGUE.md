# Despliegue — variables de entorno de producción

Guía para configurar el servicio (Render u otro PaaS) que corre el backend con el perfil `prod`.
Fuente de verdad: `application.properties`, `application-prod.properties`, los `@Value` de
`SecurityConfig`, `JwtService`, `CorreoService`, `PasswordService`, `NotificacionScheduler`,
`RecordatorioService` y `FcmPushSender`.

> Reglas: los valores de ejemplo son **ficticios**. Nunca pegues valores reales en este
> documento ni en el repo: las variables se definen solo en el panel del proveedor (Render →
> Environment) o en tu gestor de secretos. Los secretos van como variables de entorno, no en
> archivos ni en el código.

## Obligatorias para arrancar

Sin estas la aplicación no arranca. El correo y el push **no** están aquí: con
las banderas maestras desactivadas (por defecto) la app arranca sin ninguna credencial de
proveedor externo.

| Nombre | Para qué sirve | Obligatoria | Formato esperado | Ejemplo (ficticio) |
|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC de PostgreSQL (Supabase Session Pooler), con TLS o el arranque falla | Sí | JDBC de Postgres con `?sslmode=require` | `jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.example:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos (Supabase) | Sí | Texto | `postgres.pooler-pico` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la base de datos; **secreto** | Sí | Texto | `**********` (rota al darse de baja del repo) |
| `JWT_SECRET` | Clave HMAC-SHA256 que firma los access token; sin ella el contexto no arranca | Sí | **Base64** de 48 bytes aleatorios (`$b = New-Object byte[] 48; ([Security.Cryptography.RandomNumberGenerator]::Create()).GetBytes($b); [Convert]::ToBase64String($b)`); al menos 32 bytes decodificados | `3q2+7w...` (ficticio) |

## Opcionales (funciones desactivadas hasta configurarlas)

El correo de recuperación (T9.3) y los recordatorios push (T9.6) están **implementados en código y
PENDIENTES de activación**: con `APP_MAIL_ENABLED=false` y `APP_PUSH_ENABLED=false` (valores por
defecto) la recuperación *no genera ni envía nada* (log WARN) y los recordatorios son no-op total
(no persisten `RECORDATORIO_ENVIADO`, para no perder avisos). Activa cada función solo cuando su
proveedor y sus requisitos listos abajo existan; con la bandera activa y credenciales faltantes la
app arranca igual, pero cada envío falla con un error claro en el log.

| Nombre | Para qué sirve | Obligatoria si | Formato esperado | Ejemplo (ficticio) |
|---|---|---|---|---|
| `APP_MAIL_ENABLED` | Bandera maestra del correo de recuperación (`app.mail.enabled`). Por defecto `false`: nada se genera ni envía (log WARN) | Se quiere activar "olvidé mi contraseña" en prod | Boolean | `true` |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | SMTP real cuando `app.mail.enabled=true` y `app.mail.log-only=false`; sin host, el envío falla con 500 (la app arranca igual). Si el proveedor ofrece API HTTP (p. ej. Resend/Brevo), preferirla: Render free puede bloquear el SMTP saliente — en ese caso integrar la API antes de añadir SMTP | Flujo de recuperación por SMTP | Host de SMTP, puerto 587, credenciales del proveedor | `smtp.mailprovider.example` / `587` / `backend-saveup@example.com` / `**********` (ficticios) |
| `APP_PUSH_ENABLED` | Bandera maestra de los recordatorios push (`app.push.enabled`). Por defecto `false`: los recordatorios son no-op total (no envían ni persisten bitácora) | Se quiere activar los recordatorios de cuota | Boolean | `true` |
| `PUSH_CRON_SECRET` | Credencial del cabecera `X-Cron-Secret` del disparador externo `POST /api/v1/notificaciones/recordatorios/ejecutar`. Si **queda vacía el endpoint queda cerrado (403)** | Recordatorios push activados (sin él no hay disparo) | Texto secreto (`application.properties#app.push.cron-secret`) | `c9bf1e42-2f4a-493e-a2ba-e3a0d5f3b1c9` (ficticio) |
| `FIREBASE_CREDENTIALS` | Credenciales del service account de FCM que habilita `FcmPushSender`. Es obligatorio **proyecto Firebase** y la app móvil registrando su token en `POST /notificaciones/dispositivos` (hoy ausente) | `APP_PUSH_ENABLED=true` y `app.push.log-only=false` | JSON del service account **en base64** (también acepta el JSON crudo) | `ewogICJ0eXBlIjogInNlcnZp...` (ficticio) |
| `SPRING_PROFILES_ACTIVE` | `application.properties` fija `dev`; en el PaaS **hay que definirla como `prod`** para cargar `application-prod.properties` | Sí (despliegue) | `prod` | `prod` |
| `SERVER_PORT` | Puerto donde escucha el servidor (por defecto `8080`; algunos PaaS lo fijan por `PORT`) | Si el PaaS lo exige | Entero | `8080` |
| `APP_BEHIND_PROXY` | `true` solo si la app está detrás de un reverse proxy de confianza (Render). Habilita confiar en `X-Forwarded-For` para el límite de intentos de login; con `false` la cabecera se ignora y se usa la IP de la conexión | Despliegue detrás de proxy | Boolean | `true` |

## Ajustes dentro de `application*.properties` (no son variables) antes de publicar

- `app.mail.enabled` — bandera maestra (T9.3), `false` por defecto; se activa con `APP_MAIL_ENABLED=true`.
  Con `false` el flujo de recuperación no genera ni envía nada (log WARN); con `true` y sin SMTP configurado,
  cada envío falla de forma ruidosa (ERROR_INTERNO) y la app arranca igual.
- `app.mail.log-only` — en `application-prod.properties` está pre-fijado a `false` (la app pública no debe
  dejar tokens en logs). Con `APP_MAIL_ENABLED=true` pero SMTP aún ausente, súbela a `true` como variable de
  entorno (`APP_MAIL_LOGONLY=true`) para que el token vaya solo a logs (solo dev/test: exponer el token en
  logs de prod no es aceptable más allá de un despliegue privado).
- `app.push.enabled` — bandera maestra (T9.6), `false` por defecto; se activa con `APP_PUSH_ENABLED=true`.
  Con `false` los recordatorios son no-op total y no consumen credenciales de FCM.
- `app.push.log-only` — dejado en `true` mientras no exista Firebase; al activar push, `false` +
  `FIREBASE_CREDENTIALS` (`APP_PUSH_LOGONLY=false`).
- `app.version.min-android/ios`, `app.version.latest-android/ios` — activar bloqueo/sugerencia de
  actualización (T9.4).
- `app.version.url-tienda-android/ios` — URLs reales de tienda cuando existan reemplaza los
  placeholders `cl.saveup.lite`.
- `app.cors.allowed-origins` — restringir a los dominios de la app cuando existan (hoy `*`).

## Claves de `application*.properties` (referencia completa)

Estas claves ya están fijadas en los archivos; se listan para que el despliegue no dependa de
suposiciones. Solo se cambian editando el archivo o con una variable de entorno equivalente.

| Clave | dev / prod | Nota |
|---|---|---|
| `spring.flyway.locations` | `classpath:db/migration/postgresql` en ambos | carpeta de migraciones |
| `spring.flyway.baseline-on-migrate` | `true` en ambos | marca en V0 una BD preexistente |
| `spring.flyway.baseline-version` | `0` en ambos | versión base del baseline |
| `spring.flyway.validate-on-migrate` | `true` en ambos | checksum de migraciones aplicadas |
| `spring.jpa.hibernate.ddl-auto` | `validate` / `none` | dev valida el esquema; prod no lo toca |
| `jwt.expirationInMs` | `900000` | 15 minutos de vida del access token |
| `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` | activos / `false` | el contrato de prod vive en `docs/openapi.json` |
| `app.cors.allowed-methods` / `allowed-headers` / `max-age` | iguales en ambos | `allow-credentials` es `true` en dev y `false` en prod |
| `app.mail.url-restablecer` | vacío | URL pública de la pantalla de restablecimiento |
| `app.push.dias-antes` | `3` | antelación del aviso PROXIMA_VENCER |
| `app.version.min-ios` / `latest-ios` | `1.0.0` | mismos umbrales que Android |
| `app.version.terminos` / `privacidad` | `1` | versión de los documentos legales |
| `spring.jackson.time-zone` / `hibernate.jdbc.time_zone` | `UTC` | contrato de fechas e instantes en UTC |
| `spring.jackson.date-format` / `write-dates-as-timestamps` | ISO-8601 / `false` | fechas de calendario y dinero |
| `server.port` / `spring.application.name` | `8080` / `SaveUp` | — |
| `logging.level.*` | solo dev | trazas SQL, Hikari y JDBC para depurar |

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
