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
  (token inválido, reusado o revocado, incluido un logout en otro dispositivo) o el refresh también falla, borra ambos
  tokens y vuelve al login. No reintentes el login automáticamente con credenciales guardadas.
- Para cerrar sesión: `POST /auth/logout` con `{ "refreshToken": "..." }`; luego borra ambos tokens del almacenamiento.
- **Política anti-fuerza bruta del login**: el backend cuenta los intentos fallidos por combinación de **correo + IP**
  (la IP viene de la primera dirección de `X-Forwarded-For` que agrega el proxy de Render). Tras **5 fallos** ese par
  queda bloqueado **15 minutos** y `POST /usuarios/login` responde **429 INTENTOS_LOGIN_EXCEDIDOS** — incluso con la
  contraseña correcta, y el bloqueo no dependerá de reintentos: no reenvíes el login automáticamente. Al lograr un
  login exitoso el contador se reinicia. En la UI: ante un 429 muestra "Demasiados intentos; espera unos minutos"
  con un botón de reintento manual, y deshabilita el reenvío automático de credenciales guardadas.
- **Cambiar la contraseña con sesión activa** (pantalla "Configuración → Seguridad"): `PUT /usuarios/me/contrasena` con
  `Authorization: Bearer <token>` y `{ "contrasenaActual": "...", "nuevaContrasena": "..." }` (8–20 caracteres, con letra
  y número). Devuelve 200 y **revoca todas las sesiones** (otros dispositivos quedan fuera); deja de usar el refresh
  token guardado y pide login con la nueva contraseña. `401 CREDENCIALES_INVALIDAS` si la actual no coincide y
  `400 VALIDACION_FALLIDA` (campo `nuevaContrasena`) si la nueva es débil.
- **Recuperar la contraseña sin sesión** (pantalla "Olvidé mi contraseña", endpoint público):
  1. `POST /auth/recuperar-contrasena` con `{ "email": "..." }` → **siempre 200 genérico** ("Si el correo está registrado…").
     No muestres nada que dependa de si el correo existe: la respuesta es idéntica para todos (anti-enumeración).
  2. El backend envía (o loguea, en dev) un token de un solo uso válido 1 hora. Cuando la app pública tenga pantalla de
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
