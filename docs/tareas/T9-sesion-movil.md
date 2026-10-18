# T9 — Sesión móvil y requisitos de las tiendas (BACKLOG: necesita decisiones, no implementar sin spec)

Cosas que una app móvil publicada necesita y que el backend aún no tiene:
- Refresh tokens y expiración: el JWT actual dura 24 h, así que sin refresh el usuario vuelve a iniciar sesión cada día. Decidir duración del access token, refresh token rotativo y revocación (logout).
- Borrar cuenta desde la app (DELETE /api/v1/usuarios/me, con borrado en cascada de movimientos, deudas, metas y presupuestos). Las tiendas exigen esta opción en las apps que permiten crear cuenta: verifica las políticas vigentes de Apple y Google antes de publicar.
- Recuperar contraseña (requiere envío de correo y un token de un solo uso) y cambiar contraseña.
- Versión mínima de la app (forzar actualización): endpoint o cabecera para informar versiones soportadas.
- Protección de login contra fuerza bruta (límite de intentos).
- Recordatorios de vencimiento de cuotas con notificaciones push (FCM/APNs). Requiere guardar tokens de dispositivo y un proceso programado.
- Política de privacidad y términos (necesarios para publicar).
