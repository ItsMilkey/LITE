# T9 — Sesión móvil y requisitos de las tiendas (BACKLOG: necesita decisiones, no implementar sin spec)

Cosas que una app móvil publicada necesita y que el backend aún no tiene:
- **[HECHO en T9.1]** Refresh tokens y expiración: access token de 15 min + refresh token rotativo de 90 días,
  revocable y con detección de reuso (`POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`; tabla `auth_refresh_token`,
  migración V9). Prestaciones pendientes de este punto: logout masivo expuesto en endpoint y el resto del desglose abajo.
- **[HECHO en T9.2]** Borrar cuenta desde la app (DELETE /api/v1/usuarios/me, con borrado en cascada de movimientos,
  deudas, metas y presupuestos; exige la contraseña actual, migración V10).
- **[HECHO en T9.3]** Recuperar contraseña y cambiar contraseña: `POST /auth/recuperar-contrasena` (200 genérico
  anti-enumeración, token de un solo uso por 1 h vía `token_verificacion`, migración V11; correo real con SMTP o
  log-only en dev/test) + `POST /auth/restablecer-contrasena` + `PUT /usuarios/me/contrasena`; ambos cambios
  revocan todas las sesiones activas. Pendiente: elegir proveedor SMTP (Brevo/Resend) y configurar la URL pública
  de restablecimiento (`app.mail.url-restablecer`).

  **Estado T9.3: implementado en código; PENDIENTE de activación (requiere proveedor externo).** Falta:
  - Proveedor de correo: **preferir una API HTTP** (Resend/Brevo/Amazon SES) antes que SMTP directo — Render
    gratuito puede bloquear el SMTP saliente.
  - Remitente y dominio verificado (SPF/DKIM) para que no caiga en spam.
  - Plantilla del correo (hoy es texto simple compuesto en `PasswordService`).
  - URL pública de la pantalla de restablecimiento (`app.mail.url-restablecer`; vacío = solo el token).
  - Activar con la variable `APP_MAIL_ENABLED=true` (por defecto el flujo no genera ni envía nada; solo log WARN).
    Si se niega a una API HTTP y se usa SMTP: `app.mail.log-only=false` + `spring.mail.*` con `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`.
- **[HECHO en T9.4]** Versión mínima de la app (forzar actualización): `GET /api/v1/app/version` público con
  `?plataforma=ANDROID|IOS&version=MAJOR.MINOR.PATCH`; umbrales min-*/latest-* y URLs de tienda en
  application.properties (SemVerUtils + VersionAppService). Al publicar cada release subir `latest-*`;
  subir `min-*` solo cuando algo obliga a salir de la app. Cuando la app se publique, actualizar las URLs
  `app.version.url-tienda-android/ios` con los enlaces reales. Nota post-review: la respuesta ya incluye
  `versionTerminos` y `versionPrivacidad` (`app.version.terminos/privacidad`) para alimentar la pantalla de
  legales del cliente; es la única raíz de T9.7 anticipada — T9.7 sigue aplazado y nada más de legales está
  implementado.
- **[HECHO en T9.5]** Protección de login contra fuerza bruta: `LoginRateLimiterService` (paquete `security`, caché
  Caffeine en memoria, ventana de 15 min re-expirada por cada fallo) cuenta intentos fallidos por la clave
  `email.toLowerCase() + ":" + IP` (IP resuelta desde la primera dirección de `X-Forwarded-For` o `getRemoteAddr()`).
  El 5º fallo bloquea el par: `POST /usuarios/login` responde **429 INTENTOS_LOGIN_EXCEDIDOS** antes de consultar la BD;
  cada 401 suma un intento (también con correo inexistente) y un login exitoso resetea el contador. Documentado en el
  contrato con la respuesta 429 (customizer de OpenApiConfig por `operationId`). Nota: el contador vive en memoria del
  proceso (suficiente para una sola instancia en Render); al escalar horizontalmente mover la caché a Redis.
- Recordatorios de vencimiento de cuotas con notificaciones push (FCM/APNs). Requiere guardar tokens de dispositivo y un proceso programado.
- Política de privacidad y términos (necesarios para publicar).
- **Estado (oct 2026): Fase A de T9 (T9.1–T9.5) y T9.6 concluidas.** T9.3 y T9.6 quedan marcadas como
  **PENDIENTES de activación** (implementadas en código, requieren proveedor externo): ver
  docs/DESPLIEGUE.md y los encabezados de cada tarea. Queda T9.7 (legal, aplazado, depende de la app
  pública y de las tiendas). T9.6 se implementó en [`T9.6-notificaciones-push.md`](T9.6-notificaciones-push.md):
  dispositivos FCM, job diario a las 09:00 de Santiago (T-3/T-0/T+1), deduplicación y trigger de cron externo.
  Nota: T9.6 **no** estaba desacoplado del backend — requirió tabla, endpoints y job; el doc anterior lo agrupaba
  por error con legales.
