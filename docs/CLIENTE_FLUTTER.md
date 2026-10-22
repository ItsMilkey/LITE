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

- `POST /usuarios/register` y `POST /usuarios/login` son públicos. El login devuelve `{ "token": "..." }`.
- Guarda el JWT con `flutter_secure_storage` (Keychain/Keystore), por ejemplo bajo la clave `saveup_jwt`; nunca en
  `SharedPreferences`, logs ni código fuente.
- En cada solicitud protegida envía `Authorization: Bearer <token>`.
- Si recibes 401 con `codigo = NO_AUTENTICADO` o `TOKEN_EXPIRADO`, borra el token y vuelve al login. No reintentes el login
  automáticamente con credenciales guardadas.

```dart
final storage = FlutterSecureStorage();
final dio = Dio(BaseOptions(baseUrl: 'https://lite-backend-1wn9.onrender.com/api/v1'));
dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) async {
  final token = await storage.read(key: 'saveup_jwt');
  if (token != null) options.headers['Authorization'] = 'Bearer $token';
  handler.next(options);
}));
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
| `TOKEN_EXPIRADO` | 401 | Ir al login y borrar el token. |
| `CREDENCIALES_INVALIDAS` | 401 | Mostrar “correo o contraseña incorrectos” (mensaje genérico). |
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
