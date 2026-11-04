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
  alfabético. `motivo` es `PALABRA_CLAVE`, `PREFIJO`, `APROXIMADA` o `HISTORIAL`. Desde T10c, `HISTORIAL` (confianza
  0,95) usa las partidas reales del usuario: si ya usó esa descripción en una partida, esa categoría sube al primer
  lugar.
- **Debounce:** espera unos **400 ms** sin teclear antes de llamar. **Descarta respuestas atrasadas** comparando el
  campo `texto` de la respuesta con el valor actual del formulario; el backend devuelve el texto original exacto
  (incluidos espacios y mayúsculas) para que puedas hacer esa comparación.
- **Preselección sin asignación silenciosa:** si hay sugerencias, preselecciona la primera; muestra las demás como
  opciones rápidas. Si `ambigua=true`, pide confirmación visible antes de guardar. Si la lista viene vacía, no
  preselecciones nada: el backend nunca devuelve `OTROS` por su cuenta.
- El endpoint no persiste nada ni registra el texto (G9). Errores: `400 PARAMETRO_INVALIDO` por texto ausente o
  demasiado largo o contexto desconocido; `401 NO_AUTENTICADO` si falta el token.

### Plan del mes y partidas (T10c)

El plan es **por mes de calendario**: `mes` se escribe `yyyy-MM` (por ejemplo `2026-11`) y debe caer entre
`2000-01` y `2100-12`. Cualquier otro formato o fuera de rango → `400 PARAMETRO_INVALIDO`. Todos los meses son
editables y no hay cierre de mes.

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/planes/{mes}` | — (`?incluirReferencias=true` opcional) | `200` plan completo (`existe:false` si no hay plan) |
| `PUT` | `/planes/{mes}/ingreso-referencia` | `{ "monto": 1000000.00 }` o `{ "monto": null }` | `200` plan completo |
| `POST` | `/planes/{mes}/partidas` | `{ "nombre": "Arriendo", "categoriaId": 4, "montoPlanificado": 400000.00, "grupoId": 1 }` | `201` plan completo |
| `PUT` | `/planes/{mes}/partidas/{id}` | `{ "nombre": "...", "categoriaId": 4, "grupoId": 1 }` o `{ "quitarGrupo": true }` (al menos uno) | `200` plan completo |
| `DELETE` | `/planes/{mes}/partidas/{id}` | — | `204` |
| `PATCH` | `/planes/{mes}/montos` | `{ "cambios": [ { "partidaId": 1, "monto": 420000.00 } ] }` | `200` plan completo |
| `POST` | `/planes/{mes}/copiar-desde/{mesOrigen}` | — | `201` plan completo |
| `GET` | `/planes/grupos` | — | `200` lista de grupos |
| `POST` | `/planes/grupos` | `{ "nombre": "Fijos", "orden": 1 }` | `201` grupo |
| `PUT` | `/planes/grupos/{id}` | `{ "nombre": "...", "orden": 2 }` (al menos uno) | `200` grupo |
| `DELETE` | `/planes/grupos/{id}` | — | `204` (las partidas quedan sin grupo) |

Para vincular una deuda, `POST /planes/{mes}/partidas` acepta `{ "nombre": "Cuota crédito", "deudaId": 7 }` **sin**
`categoriaId` ni `montoPlanificado`: la categoría es siempre `DEUDAS` y el monto sale de la cuota.

Todas las rutas llevan `Authorization: Bearer <token>`. Toda modificación devuelve el **plan completo recalculado**:
reemplaza tu estado local con esa respuesta en lugar de parchear el objeto a mano.

- **`GET /planes/{mes}` de un mes sin plan** responde `200` con `existe:false`, `ingresoReferencia:null`,
  `partidas:[]`, `planificadoTotal:0` y el gasto real del mes entero en `sinPlanificar` (motivo `SIN_PARTIDA`).
  Nunca es un 404: la pantalla se muestra vacía y lista para empezar.
- **Forma de la respuesta** (siempre: `mes`, `existe`, `ingresoReferencia`, `resumen`, `partidas`, `porCategoria`,
  `repartoPorTipo`, `guia`, `grupos`, `avisos`; `referencia` solo con `incluirReferencias=true`):

```json
{
  "mes": "2026-11",
  "existe": true,
  "ingresoReferencia": 1000000.00,
  "resumen": {
    "planificadoTotal": 746000.00,
    "remanenteLibre": 254000.00,
    "margenSeguridadPct": 25.40,
    "excedeIngreso": false,
    "ingresoReal": 900000.00,
    "gastoRealTotal": 210000.00,
    "disponibleReal": 690000.00,
    "ahorroRegistrado": 80000.00,
    "sinPlanificar": {
      "monto": 12000.00,
      "movimientos": 1,
      "porMotivo": [ { "motivo": "SIN_PARTIDA", "monto": 12000.00, "movimientos": 1 } ]
    }
  },
  "partidas": [
    { "id": 1, "nombre": "Arriendo", "categoriaId": 4, "categoriaCodigo": "VIVIENDA_SUMINISTROS",
      "montoPlanificado": 400000.00, "real": 400000.00, "diferencia": 0.00,
      "porcentajeUsado": 100.00, "estado": "CERCA_DEL_PLAN", "grupoId": 1,
      "deudaId": null, "estadoCuota": null, "vinculoDesactualizado": false, "orden": 1,
      "actualizadoEn": "2026-11-02T03:00:00.000Z" }
  ],
  "porCategoria": [
    { "categoriaId": 4, "codigo": "VIVIENDA_SUMINISTROS", "tipo": "NECESIDAD",
      "planificado": 400000.00, "real": 400000.00, "diferencia": 0.00,
      "porcentajeUsado": 100.00, "estado": "CERCA_DEL_PLAN",
      "referencia": { "mesAnterior": 80000.00, "promedio3Meses": 100000.00 } }
  ],
  "repartoPorTipo": [
    { "tipo": "NECESIDAD", "planificado": 400000.00, "porcentajeDelIngreso": 40.00,
      "real": 400000.00, "porcentajeRealDelIngresoReal": 44.44, "diferenciaPuntos": -10.00 },
    { "tipo": "DESEO", "planificado": 0.00, "porcentajeDelIngreso": 0.00,
      "real": 0.00, "porcentajeRealDelIngresoReal": 0.00, "diferenciaPuntos": -30.00 },
    { "tipo": "OTROS", "planificado": 0.00, "porcentajeDelIngreso": 0.00,
      "real": 0.00, "porcentajeRealDelIngresoReal": 0.00, "diferenciaPuntos": null }
  ],
  "guia": { "necesidades": 50.00, "deseos": 30.00, "ahorro": 20.00 },
  "grupos": [
    { "id": 1, "nombre": "Fijos", "orden": 1, "planificado": 400000.00,
      "porcentajeDelPlanificado": 100.00, "real": 400000.00, "partidaIds": [1] }
  ],
  "avisos": []
}
```

- `resumen.remanenteLibre` y `margenSeguridadPct` pueden ser `null` (sin ingreso de referencia; el margen también
  si el ingreso es 0). `excedeIngreso` es **informativo**: planificar más que el ingreso se acepta, solo avisa.
  La sección de fórmulas y vectores de prueba está en [`PLAN_FORMULAS.md`](PLAN_FORMULAS.md).
- **Reglas de validación de una partida:** nombre de 1 a 80 caracteres tras `trim`, sin caracteres de control ni
  saltos de línea y único dentro del plan (comparando sin tildes ni mayúsculas: "Súper" y "super" chocan); monto
  ≥ 0 con 2 decimales y hasta 13 enteros; categoría del catálogo distinta de `SUELDO` y `AHORRO`; máximo 100
  partidas por plan. Nombre vacío o demasiado largo → `400 VALIDACION_FALLIDA` con `errores[]`; nombre duplicado o
  plan lleno → `409 CONFLICTO_REGLA_NEGOCIO`; categoría `SUELDO`/`AHORRO` → `400 DATOS_INVALIDOS`.
- **`PUT` de una partida vacío** (`{}`) → `400 VALIDACION_FALLIDA`. Cambiar la **categoría** de una partida que ya
  tiene movimientos vinculados → `409 CONFLICTO_REGLA_NEGOCIO`. Cambiar solo el nombre siempre se permite.
- **`DELETE` de una partida** deja sus movimientos con `partidaId = null`; al volver a leer el plan se reimputan por
  categoría. No borra ningún movimiento.
- **`PATCH /montos` es atómico**: los montos son absolutos (no incrementos). Si el mes no tiene plan (`404`), una
  partida no pertenece al plan (`404`/`403` si es de otro usuario), un monto es inválido, hay un `partidaId` repetido,
  la lista está vacía o supera 100 cambios, no se aplica **ninguno**. Reenviar el mismo lote no cambia
  `actualizadoEn` de las partidas cuyo monto no cambió. En una **partida vinculada a una deuda** el monto lo define
  la cuota: el `PATCH` sobre ella → `409 CONFLICTO_REGLA_NEGOCIO` y no se aplica nada.
- **Copiar un mes:** el origen debe tener al menos una partida (`404 NO_ENCONTRADO`), el destino no debe tener
  partidas (`409 CONFLICTO_REGLA_NEGOCIO`) y los meses deben ser distintos (`400 PARAMETRO_INVALIDO`). Copia nombre,
  categoría, monto, orden y grupo; el ingreso de referencia solo si el destino no tenía uno. Las partidas vinculadas
  se copian solo si la deuda tiene cuota en el destino; las omitidas llegan en `avisos` (ver más abajo).

### Preferencia de tipo y reparto por tipo (T10d, sesión 1)

El usuario puede reclasificar categorías del catálogo como NECESIDAD o DESEO. **Tipo efectivo** = preferencia si
existe; si no, el tipo por defecto del catálogo. El tipo efectivo reemplaza al por defecto en toda la app:
ejecución 50/30/20 (T5), `porCategoria.tipo`, `repartoPorTipo` y sugerencia de regla.

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/categorias/preferencias` | — | `200` catálogo con `tipoPorDefecto`, `tipoPreferido` (o `null`) y `tipoEfectivo` |
| `PUT` | `/categorias/{id}/tipo-preferido` | `{ "tipo": "NECESIDAD" }` | `200` la fila del catálogo |
| `DELETE` | `/categorias/{id}/tipo-preferido` | — | `204` (idempotente; vuelve al por defecto) |

- El listado ordena igual que el catálogo: NECESIDAD → DESEO → AHORRO → OTROS y luego por nombre.
- Solo pueden reclasificarse categorías cuyo tipo por defecto es NECESIDAD o DESEO; el tipo pedido tampoco puede
  ser AHORRO ni OTROS: todos esos casos → `400 DATOS_INVALIDOS`. Un valor no conocido (`"XYZ"`) → `400
  SOLICITUD_INVALIDA`. Categoría inexistente → `404 NO_ENCONTRADO` (en `PUT` y en `DELETE`).
- `repartoPorTipo` (siempre tres filas: NECESIDAD, DESEO, OTROS) y `guia` del plan (**informativos**:
  ninguna diferencia es un error) se documentan en [`PLAN_FORMULAS.md`](PLAN_FORMULAS.md), sección 7. Cómo
  repartir los colores lo decide la app, no la API.

### Grupos, deudas vinculadas y referencias (T10d, sesión 2)

**Grupos.** `GET/POST /planes/grupos` y `PUT/DELETE /planes/grupos/{id}` (en la tabla de arriba). Reglas:

- Nombre de 1 a 60 caracteres; único por usuario comparando sin tildes ni mayúsculas (`"Fijos"` y `" fijos "`
  chocan) → `409 CONFLICTO_REGLA_NEGOCIO`. Máximo **20** grupos por usuario → `409`. Nombre vacío, de más de 60 o
  con caracteres de control → `400 VALIDACION_FALLIDA`.
- `DELETE` responde `204` y **no borra partidas**: las deja con `grupoId = null`.
- El plan trae `grupos`: un elemento por grupo del usuario en orden `orden, id` y, al final, `"Sin grupo"` (con
  `id` y `orden` nulos) solo si hay partidas sin grupo. Cada uno trae `planificado`, `real`, `partidaIds` y
  `porcentajeDelPlanificado` (nulo si el planificado total es 0; la suma puede no dar exactamente 100,00 y la app no
  debe forzarla). Un grupo sin partidas en el mes aparece con `planificado: 0.00`.
- `POST` de partida acepta `grupoId`; `PUT` acepta `grupoId` o `quitarGrupo:true`, nunca ambos (`400
  DATOS_INVALIDOS`). Copiar un mes conserva el grupo.

**Deudas vinculadas.** `POST /planes/{mes}/partidas` con `deudaId` (sin `montoPlanificado` ni otra categoría):

- La deuda debe existir (`404`), ser del usuario (`403`), no estar `CANCELADA` y tener `fechaPrimeraCuota` (`409`),
  y tener una cuota que venza en el mes (`409`). Enviar `montoPlanificado` o una categoría distinta de `DEUDAS` →
  `400 DATOS_INVALIDOS`. Una sola partida por deuda y mes → `409`.
- `montoPlanificado` = cuota del mes + costo adicional, recalculado en cada lectura; la app **no** lo envía. Si el
  vínculo ya no calcula (por ejemplo, editaron la deuda), llega el último monto guardado con
  `vinculoDesactualizado: true`.
- `estadoCuota`: `PENDIENTE` (sin pagos), `PARCIAL` (pagado < monto) o `PAGADA` (pagado ≥ monto). Es adicional al
  `estado` neutro, que se sigue calculando.
- `PATCH /montos` sobre una partida vinculada → `409`. Tampoco se puede cambiar su `deudaId` ni su `categoriaId`:
  bórrala y vuelve a crearla. Si la deuda se borra, la partida queda normal (`deudaId: null`).
- Un `PAGO_DEUDA` sin `partidaId` cuya deuda tenga partida vinculada en el plan se imputa a esa partida
  automáticamente; el `partidaId` explícito manda.

**Avisos.** `avisos` solo se llena al copiar un mes. Cada elemento tiene
`{motivo, partidaId, nombre, deudaId, mensaje}`; hoy el único motivo es `SIN_COUOTA_EN_EL_MES` (la partida
vinculada no se copió porque la deuda no tiene cuota en el destino). Fuera de una copia siempre es `[]`. Un aviso
nunca es un error HTTP.

**Referencias históricas.** Con `GET /planes/{mes}?incluirReferencias=true`, cada `porCategoria` agrega
`referencia: {mesAnterior, promedio3Meses}`. Sin el parámetro el campo **no aparece**. La ventana son los hasta 3
meses cerrados previos al mes, desde el mes del primer movimiento del usuario; los meses sin gasto cuentan 0 y
`promedio3Meses` redondea HALF_UP a 2 decimales. Si el primer movimiento es del mismo mes (o no hay historial), la
referencia es nula. Es solo información: la app decide cómo mostrarla.

### Sugerencia de regla (T10d, sesión 1)

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/presupuestos/sugerencia-regla` | `200` siempre; con menos de 2 meses devuelve `sugerencia:null` y `motivo` |

Analiza hasta los **3 meses cerrados más recientes** (anterior al mes en curso, zona `America/Santiago`) que
tengan `ingresoReal > 0`; `PAGO_DEUDA` cuenta y la categoría OTROS se ignora. Devuelve:

```json
{ "mesesAnalizados": ["2026-09", "2026-10"],
  "actual": { "necesidades": 58.00, "deseos": 24.00, "ahorro": 18.00 },
  "sugerencia": { "necesidades": 60, "deseos": 25, "ahorro": 15 },
  "gastoSuperaIngreso": false, "igualALaGuia": false, "motivo": null }
```

- `actual` = mediana de los porcentajes reales, 2 decimales. `sugerencia` = múltiplos de 5 que suman 100;
  si el gasto supera el ingreso, se escala (`gastoSuperaIngreso:true`) y `ahorro` queda en 0.
- Con menos de 2 meses válidos → `sugerencia:null` y `motivo:"DATOS_INSUFICIENTES"`; nunca 404.
- `igualALaGuia` compara con la configuración del usuario (`false` si no la tiene). Es **solo lectura**: para
  aceptar la sugerencia, la app llama al `POST /presupuestos/me` existente.
- **Sliders:** el máximo sugerido de cada slider es `monto actual + máx(remanenteLibre, 0)` y se recalcula localmente
  al mover otras partidas. Si el usuario teclea más, acéptalo con un aviso: sobreasignar es válido. Guarda al soltar
  el slider o al confirmar el teclado, acumulando los cambios en un solo `PATCH`; tras la respuesta, reemplaza el
  estado local por el plan devuelto.

**Partida en los movimientos (T10c).** `MovimientoRegistroDTO`, `MovimientoEdicionDTO`, `MovimientoResponseDTO` y el
filtro añaden `partidaId`:

- En `POST /movimientos`, `partidaId` es opcional para `GASTO_GENERAL` y `PAGO_DEUDA`. La partida debe ser del
  **mes actual** y, si viene, su categoría deriva la del movimiento (si envías también `categoriaId`, debe
  coincidir). `INGRESO_GENERAL` con `partidaId` → `400 DATOS_INVALIDOS`. En `PAGO_DEUDA` la partida debe ser de
  categoría `DEUDAS`.
- En `PUT /movimientos/{id}`: envía `partidaId` para asignar/cambiar (la partida debe ser del **mes de la fecha del
  movimiento**), `desvincularPartida:true` para quitarla. Enviar ambos → `400`. Ninguno de los dos conserva la
  partida actual. Si el movimiento tiene partida, cambia la partida o desvincúlala antes de cambiar la categoría.
- `GET /movimientos/paginados/me?partidaId=<id>` filtra por partida.
- `ABONO_META` y `RETIRO_META` no admiten partida. La imputación por categoría de los movimientos sin `partidaId`
  se calcula al leer el plan y **no** se guarda en el movimiento.

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

