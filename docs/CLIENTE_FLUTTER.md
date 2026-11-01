# Cliente Flutter — consumo de la API v1

Contrato: [`docs/openapi.json`](openapi.json). Errores: [`docs/CONTEXT.md`](CONTEXT.md#errores-uniformes-t8b).

## 1. URL base por entorno

| Entorno | URL base |
|---|---|
| Backend local (web, macOS/Windows/iOS simulator) | `http://localhost:8080/api/v1` |
| Emulador Android | `http://10.0.2.2:8080/api/v1` |
| Producción | `https://lite-backend-1wn9.onrender.com/api/v1` |

El HTTP sin cifrar suele estar bloqueado en dispositivos reales (Android cleartext e iOS ATS). Úsalo solo en el emulador/simulador
con el backend local; producción siempre HTTPS. No incluyas `/v3/api-docs` ni Swagger UI en la app: solo existen en dev.

## 2. Token y autenticación

- `POST /usuarios/register`, `POST /usuarios/login`, `POST /auth/refresh`, `POST /auth/logout`,
  `POST /auth/recuperar-contrasena`, `POST /auth/restablecer-contrasena` y `GET /app/version` son públicos. Login y refresh
  devuelven `{ "token": "...", "refreshToken": "...", "expiraEn": "2026-10-22T12:15:00.000Z" }`.
- Guarda ambos con `flutter_secure_storage` (Keychain/Keystore): `saveup_jwt` para el access token y `saveup_refresh` para
  el refresh token; nunca en `SharedPreferences`, logs ni código fuente.
- El access token dura **15 minutos** (`expiraEn` te dice cuándo) y el refresh token **90 días**. Rotativo: cada refresh
  entrega un refresh token nuevo y revoca el anterior; el anterior deja de servir en cuanto se entregó el nuevo.
- En cada solicitud protegida envía `Authorization: Bearer <token>`.
- Manejo del 401 en el interceptor: si `codigo = TOKEN_EXPIRADO`, llama **una sola vez** `POST /auth/refresh` con
  `{ "refreshToken": "..." }` y reintenta la solicitud original; guarda el par nuevo. Si `codigo = REFRESH_INVALIDO`
  (token inválido, reusado, revocado por cambio o restablecimiento de contraseña, detección de reuso o cuenta eliminada)
  o el refresh también falla, borra ambos tokens y vuelve al login. No reintentes el login automáticamente con
  credenciales guardadas.
- Para cerrar sesión: `POST /auth/logout` con `{ "refreshToken": "..." }`; luego borra ambos tokens del almacenamiento.
- **Política anti-fuerza bruta del login**: el backend cuenta los intentos fallidos por combinación de **correo + IP**
  (la IP sale de la primera dirección de `X-Forwarded-For` cuando el backend corre detrás de un proxy de confianza,
  `APP_BEHIND_PROXY=true`; si no, usa la IP de la conexión). Tras **5 fallos** ese par
  queda bloqueado **15 minutos** y `POST /usuarios/login` responde **429 INTENTOS_LOGIN_EXCEDIDOS** — incluso con la
  contraseña correcta, y el bloqueo no dependerá de reintentos: no reenvíes el login automáticamente. Al lograr un
  login exitoso el contador se reinicia. En la UI: ante un 429 muestra "Demasiados intentos; espera unos minutos"
  con un botón de reintento manual, y deshabilita el reenvío automático de credenciales guardadas.
- **Cambiar la contraseña con sesión activa** (pantalla "Configuración → Seguridad"): `PUT /usuarios/me/contrasena` con
  `Authorization: Bearer <token>` y `{ "contrasenaActual": "...", "nuevaContrasena": "..." }`. Devuelve 200 y **revoca todas las sesiones** (otros dispositivos quedan fuera); deja de usar el refresh
  token guardado y pide login con la nueva contraseña. `401 CREDENCIALES_INVALIDAS` si la actual no coincide y
  `400 VALIDACION_FALLIDA` (campo `nuevaContrasena`) si la nueva es débil.
- **Política de contraseñas** (registro, cambio y restablecimiento; el **login NO la valida**: compara contra el hash tal
  cual): **8 a 64 caracteres**, al menos **una letra y un número**, y máximo **72 bytes en UTF-8** (el límite que BCrypt
  cifra sin truncar; los acentos y la 'ñ' pesan 2 bytes por carácter). Endpoints: `POST /usuarios/register`,
  `PUT /usuarios/me/contrasena` y `POST /auth/restablecer-contrasena`.
- **Recuperar la contraseña sin sesión** (pantalla "Olvidé mi contraseña", endpoint público):
  1. `POST /auth/recuperar-contrasena` con `{ "email": "..." }` → **siempre 200 genérico** ("Si el correo está registrado…").
     No muestres nada que dependa de si el correo existe: la respuesta es idéntica para todos (anti-enumeración).
  2. Con el envío activo, el backend envía (o loguea, en dev) un token de un solo uso válido 1 hora. **En producción
     el correo está PENDIENTE de activación** (`app.mail.enabled=false` por defecto): la solicitud responde igual, pero
     no genera ni envía token; la UI debe indicar que el envío aún no está disponible. Cuando exista pantalla pública de
     restablecimiento, configúrala en `app.mail.url-restablecer`; mientras tanto el correo muestra el token en texto.
     Pide al usuario pegar el token recibido y la contraseña nueva en el formulario.
  3. `POST /auth/restablecer-contrasena` con `{ "token": "...", "nuevaContrasena": "..." }` → 200; el token queda
     consumido (no se puede reusar) y **se revocan todas las sesiones** del usuario. `400 TOKEN_VERIFICACION_INVALIDO`
     si el token es inválido, ya se usó o expiró: muestra "solicita uno nuevo" y deja reintentar desde el paso 1.
- **Forzar actualización / versión mínima** (T9.4, endpoint público): al arrancar, antes de cualquier login, consulta
  `GET /app/version?plataforma=ANDROID|IOS&version=MAJOR.MINOR.PATCH` con la versión de la app compilada
  (`plataforma`/`version` son obligatorios). Responde
  `{ actualizacionObligatoria, actualizacionSugerida, mensaje, urlTienda, versionTerminos, versionPrivacidad }`:
  - `actualizacionObligatoria=true` (versión < mínimo soportado): bloquea el uso, muestra `mensaje` y abre `urlTienda`
    con el botón "Actualizar". No envíes requests de caso de uso con una versión bloqueada.
  - `actualizacionSugerida=true` (entre mínimo y la más reciente): muestra `mensaje` como aviso no intrusivo
    (banner o diálogo descartable) y sigue operando.
  - Ambos en `false` (al día o más nueva): no muestres nada.
  - Muestra `versionTerminos`/`versionPrivacidad` en la pantalla de legales; si cambian respecto a lo guardado,
    pide re-aceptación.
  - Errores: parámetro ausente o plataforma inválida → `400 PARAMETRO_INVALIDO`; versión mal formada →
    `400 DATOS_INVALIDOS`. Si la llamada falla por red, aplica política por defecto: degradar de forma segura
    (continuar con aviso de estado desconocido), no bloquear el uso por no poder consultar.
- **Eliminar la cuenta** (requisito de Apple y Google si la app permite crear cuentas): `DELETE /usuarios/me` con
  `Authorization: Bearer <token>` y cuerpo `{ "contrasena": "..." }`. Borra en cascada todo el dominio del usuario
  (movimientos, deudas, metas, presupuestos y sesiones) y devuelve **204**; es irreversible. `401 CREDENCIALES_INVALIDAS`
  si la contraseña no coincide y `400 VALIDACION_FALLIDA` si falta. Confirma con el usuario (delante, no en un menú
  escondido) antes de llamarla.

```dart
final storage = FlutterSecureStorage();
final dio = Dio(BaseOptions(baseUrl: 'https://lite-backend-1wn9.onrender.com/api/v1'));
// onRequest: adjunta Authorization: Bearer <token> (saveup_jwt).
// onError: si status == 401 && codigo == TOKEN_EXPIRADO y no venimos de un refresh,
// llama a /auth/refresh con saveup_refresh, persiste el par nuevo y reintenta;
// con REFRESH_INVALIDO limpia ambas claves y ve al login.
```

## 3. Dinero, porcentajes y fechas (T8a)

- **Montos**: `BigDecimal` serializado como número JSON con escala 2 (`12500.00`). No hagas aritmética financiera con `double`.
  Guarda céntimos como `int` o usa un paquete decimal; el backend es la fuente de verdad de saldos, intereses y totales.
- **Porcentajes**: las tasas y porcentajes de la API vienen en porcentaje, no en fracción (`tasaMensual: 1.5` significa 1,5 %;
  `porcentajeAhorro: 20.00` significa 20 %). No dividas por 100 al enviar ni al mostrar.
- **Fechas de calendario** (`LocalDate`): `yyyy-MM-dd` (`fechaLimite`, `fechaPrimeraCuota`, filtros `desde`/`hasta`). No las
  interpretes como UTC ni les apliques zona horaria.
- **Instantes** (`Date`): ISO-8601 UTC terminado en `Z` (`fecha`, `fechaCreacion`). Convierte a hora local solo para mostrar;
  la zona del negocio es `America/Santiago`.
- **Paginación**: `page` empieza en 0 y `size` debe estar entre 1 y 100 (por defecto 50 en movimientos y 20 en pagos).
- **Reportes**: `GET /reportes/movimientos/exportar` devuelve bytes y `Content-Disposition`; guárdalos como archivo, no como JSON.

### Catálogo de categorías y estados (T10a)

- `GET /categorias` devuelve el catálogo completo (18 categorías) con `id`, `codigo`, `nombre`, `iconId`, `colorHex` y
  `tipoPresupuesto`. El orden es estable: `NECESIDAD` → `DESEO` → `AHORRO` → `OTROS` y luego por nombre.
- **Usa `codigo` como clave estable** (por ejemplo `ALIMENTACION_BASE`, `DEUDAS`, `AHORRO`). No uses `nombre` ni `id`
  como clave de lógica: los nombres visibles pueden cambiar y el id depende del entorno.
- Los estados de ejecución son **informativos**, nunca errores: `BAJO_EL_PLAN` (< 80 %), `CERCA_DEL_PLAN`
  (80 % a 100 % inclusive) y `SOBRE_EL_PLAN` (> 100 %). El 100 % exacto queda en `CERCA_DEL_PLAN`. Sobreasignar
  el ingreso tampoco es un error.
- El **color y el tono los decide la app**; la API solo entrega `colorHex` e `iconId`. Un estado neutro no debe
  mostrarse como alarma.

### Sugerencia de categoría por texto (T10b)

`GET /categorias/sugerencia?texto=<texto>&contexto=GASTO|INGRESO` (protegido con `Authorization: Bearer <token>`).
`contexto` es opcional y por defecto `GASTO`.

- `texto` es obligatorio y se mide **después de recortar espacios**: máximo **80 caracteres**. Más de 80 o ausente →
  `400 PARAMETRO_INVALIDO`. Menos de 3 caracteres normalizados → **200** con `sugerencias: []` (tolera que el usuario
  escriba rápido).
- `contexto=GASTO` excluye `SUELDO` y `AHORRO`; `contexto=INGRESO` solo devuelve `SUELDO` y `OTROS`. Un valor
  desconocido → `400 PARAMETRO_INVALIDO`.
- Respuesta 200:

```json
{
  "texto": "cena aniversario",
  "sugerencias": [
    { "categoriaId": 17, "codigo": "REGALOS_CELEBRACIONES", "nombre": "Regalos y Celebraciones", "confianza": 0.90, "motivo": "PALABRA_CLAVE" },
    { "categoriaId": 16, "codigo": "RESTAURANTES_SALIDAS", "nombre": "Restaurantes y Salidas", "confianza": 0.90, "motivo": "PALABRA_CLAVE" }
  ],
  "ambigua": true
}
```

- `sugerencias` trae de **0 a 3** elementos ordenados por `confianza` descendente y, en empate, por `codigo`
  alfabético. `motivo` es `PALABRA_CLAVE`, `PREFIJO`, `APROXIMADA` o `HISTORIAL` (el historial real llega en T10c).
- **Debounce:** espera unos **400 ms** sin teclear antes de llamar. **Descarta respuestas atrasadas** comparando el
  campo `texto` de la respuesta con el valor actual del formulario; el backend devuelve el texto original exacto
  (incluidos espacios y mayúsculas) para que puedas hacer esa comparación.
- **Preselección sin asignación silenciosa:** si hay sugerencias, preselecciona la primera; muestra las demás como
  opciones rápidas. Si `ambigua=true`, pide confirmación visible antes de guardar. Si la lista viene vacía, no
  preselecciones nada: el backend nunca devuelve `OTROS` por su cuenta.
- El endpoint no persiste nada ni registra el texto (G9). Errores: `400 PARAMETRO_INVALIDO` por texto ausente o
  demasiado largo o contexto desconocido; `401 NO_AUTENTICADO` si falta el token.

## 4. Errores: mapear por `codigo`

Todo error usa `Content-Type: application/problem+json`:

```json
{ "type": "about:blank", "title": "No encontrado", "status": 404,
  "detail": "No se encontró el recurso solicitado.", "instance": "/api/v1/deudas/77",
  "codigo": "NO_ENCONTRADO" }
```

Usa `codigo` para decidir el mensaje/localización; `detail` es solo un respaldo en español. Si `errores[]` viene presente,
muestra cada `{campo, mensaje}` junto al campo del formulario. En 500 muestra el `idError` al usuario para soporte.

| `codigo` | HTTP | Acción sugerida en la app |
|---|---|---|
| `VALIDACION_FALLIDA` | 400 | Mostrar `errores[]` por campo. |
| `SOLICITUD_INVALIDA` | 400/405/415 | Revisar el cuerpo/Content-Type; error de integración. |
| `PARAMETRO_INVALIDO` | 400 | Corregir el parámetro o filtro enviado. |
| `DATOS_INVALIDOS` | 400 | Mostrar `detail` (regla de negocio o monto inválido). |
| `NO_AUTENTICADO` | 401 | Ir al login y borrar el token. |
| `TOKEN_EXPIRADO` | 401 | Llamar `POST /auth/refresh` una vez y reintentar; si persiste, ir al login. |
| `REFRESH_INVALIDO` | 401 | La sesión ya no existe: borrar ambos tokens y volver al login. |
| `TOKEN_VERIFICACION_INVALIDO` | 400 | El token de restablecimiento no sirve (inexistente, reusado o expirado): pedir uno nuevo. |
| `CREDENCIALES_INVALIDAS` | 401 | Mostrar “correo o contraseña incorrectos” (mensaje genérico). |
| `INTENTOS_LOGIN_EXCEDIDOS` | 429 | Login bloqueado 15 min por fuerza bruta: mensaje "espera unos minutos", reintento manual (sin línea a 0). |
| `PROHIBIDO` | 403 | El recurso es de otro usuario; volver atrás. |
| `NO_ENCONTRADO` | 404 | Mostrar estado vacío o refrescar la lista. |
| `CONFLICTO_REGLA_NEGOCIO` | 409 | Mostrar `detail`; no reintentar automáticamente. |
| `USUARIO_YA_REGISTRADO` | 409 | Sugerir iniciar sesión o recuperar la cuenta. |
| `DEUDA_SOBREPAGO` | 409 | Ajustar el monto del pago. |
| `ERROR_INTERNO` | 500 | Mostrar mensaje genérico e `idError` para soporte. |

## 5. Generar el cliente Dart

Desde la raíz del repositorio, con [openapi-generator](https://openapi-generator.tech/) instalado:

```bash
openapi-generator-cli generate \
  -i docs/openapi.json \
  -g dart-dio \
  -o lib/api \
  --additional-properties=pubName=saveup_api,pubVersion=1.0.0,serializationLibrary=json_serializable,nullableFields=true \
  --global-property apiTests=false,modelTests=false
```

- El generador usa los `operationId` como nombres de métodos; no los cambies sin versionar la API.
- No edites `lib/api` a mano: si cambia el contrato, regenera y compila.
- Si el contrato cambia, actualiza `docs/openapi.json` con
  `.\mvnw.cmd "-Dopenapi.update=true" test -Dtest=OpenApiContractTest` y vuelve a generar el cliente.
- En Flutter ejecuta `dart run build_runner build --delete-conflicting-outputs` si el generador produce modelos `json_serializable`.

## 6. Notificaciones push (T9.6)

La app recibe recordatorios de cuota a las **09:00 de America/Santiago** en tres momentos: **3 días antes**
(`PROXIMA_VENCER`), **el día del vencimiento** (`VENCE_HOY`) y **un día después** (`VENCIDA`). Se envía **una sola push
por usuario y por corrida**, aunque tenga varias cuotas; el detalle viaja en el payload.

1. Tras el login (o al arrancar con sesión guardada), pide el permiso del sistema operativo y obtén el token de FCM.
   No lo pidas antes de que el usuario entienda para qué sirve.
2. Regístralo: `POST /api/v1/notificaciones/dispositivos` con `Authorization: Bearer <token>` y
   `{ "token": "<fcm-token>", "plataforma": "ANDROID" | "IOS" }`. Responde `201` la primera vez y `200` si el
   dispositivo ya existía (es idempotente; sirve también para refrescar `fechaUltimoUso`).
3. Si FCM rota el token (`onTokenRefresh`), repite el `POST` con el nuevo. El anterior se desactiva solo cuando FCM lo
   rechaza en un envío.
4. En el **logout**, antes de borrar el JWT, llama `DELETE /api/v1/notificaciones/dispositivos` con
   `{ "token": "<fcm-token>" }` (`204`, idempotente). Si no lo haces, el dispositivo seguirá recibiendo avisos hasta
   que FCM rechace el token.
5. `GET /api/v1/notificaciones/dispositivos` lista los activos; el token solo se devuelve enmascarado
   (`tokenEnmascarado`). No lo uses para reenviar: el valor completo vive únicamente en el teléfono.

Payload de datos de la notificación:

```json
{ "tipo": "RECORDATORIO_CUOTA", "cantidad": "1", "deudaId": "77", "cuota": "3", "deepLink": "saveup://deudas/77" }
```

Con `cantidad > 1` no viene `deudaId` y el `deepLink` es `saveup://deudas` (lista). Configura el manejo del *tap* para
navegar con `deepLink`; no interpretes el título, que puede cambiar.

En `dev` el push está activo por defecto (`app.push.enabled=true`) con `app.push.log-only=true`: el job "envía" y
loguea, sin Firebase. En `prod` está **PENDIENTE de activación** (`app.push.enabled=false` por defecto): la corrida es
no-op y no persiste bitácora; se activa con `APP_PUSH_ENABLED=true` cuando existan el proyecto Firebase, las
credenciales y el cron. El registro y la baja de tokens de dispositivo funcionan aunque la bandera esté apagada.
El **cron externo** que dispara el job llama `POST /api/v1/notificaciones/recordatorios/ejecutar` con la
cabecera `X-Cron-Secret` (no usa JWT) y recibe `202 {evaluados, enviados, omitidos}`.

