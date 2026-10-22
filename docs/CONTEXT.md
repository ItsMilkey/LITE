# SaveUp Lite — mapa del dominio
Resumen de docs/DOCUMENTACION_COMPLETA_SAVEUP_LITE.txt. Mantenlo actualizado al terminar cada tarea.

## Entidades
- USUARIO(rut PK, nombre, apellido, email, contrasena, fechaRegistro)
- AUTH_REFRESH_TOKEN(id, usuario, tokenHash SHA-256, fechaExpiracion, fechaRevocacion?, fechaUso?) — sesión móvil (T9.1):
  refresh rotativo de 90 días; el secreto nunca se persiste. FKs hacia USUARIO con ON DELETE CASCADE (V10).
- TOKEN_VERIFICACION(id, usuario, tipo RESET_PASSWORD, tokenHash SHA-256, fechaExpiracion, usado, fechaCreacion) —
  token de un solo uso, vale 1 hora (T9.3): pedir uno nuevo invalida los anteriores; solo se persiste el hash.
  FK a USUARIO con ON DELETE CASCADE (V11).
- No hay tabla para T9.4: la política de versiones (min/latest por plataforma, URL de tienda, versiones legales)
  vive en application.properties y se resuelve en memoria (SemVerUtils + VersionAppService).
- No hay tabla para T9.5: el contador de fuerza bruta vive en una caché Caffeine en memoria
  (LoginRateLimiterService, clave email.toLowerCase()+":"+IP); se pierde al reiniciar sin riesgo de seguridad.
- CATEGORIA(id, nombre, iconId, colorHex, tipoPresupuesto: NECESIDAD|DESEO|AHORRO|OTROS)
- MOVIMIENTO(id, monto, descripcion, fecha [updatable=false], tipoMovimiento, usuario, categoria?, deuda?, metaAhorro?, movimientoOrigen?)
  Signo: + INGRESO_GENERAL y RETIRO_META; − GASTO_GENERAL, PAGO_DEUDA y ABONO_META. Saldo = SUM(monto).
  El signo NO lo decide el cliente: lo impone el tipo vía SignoMovimiento (ver reglas vigentes).
- META_AHORRO(id, nombre, montoObjetivo?, montoActual, fechaLimite?, usuario). Meta 'Ahorros' por defecto, protegida.
- DEUDA(id, nombre, descripcion?, montoTotal, cantidadCuotas, estado PENDIENTE|PAGADA|CANCELADA, fechaCreacion, usuario,
  tipoDeuda PERSONAL|TIENDA|CREDITO_CONSUMO|EDUCACION|OTRO, modalidadCalculo SIN_INTERES|TASA_CONOCIDA|CUOTA_CONOCIDA,
  montoCapital, gastosIniciales, costoAdicionalPorCuota, tasaMensual, valorCuota, fechaPrimeraCuota?,
  cuotasPagadasPrevias, montoPagadoPrevio, cargaAnualEquivalente, costoTotalCredito)
- CONFIGURACION_PRESUPUESTO(id, usuario 1:1, %necesidades, %deseos, %ahorro, activo, automatizarAhorroEnMetas)
- ASIGNACION_META_PRESUPUESTO(id, configuracion, meta, porcentajeAsignacion)
- PRESUPUESTO_CATEGORIA(id, usuario, categoria, limiteMensual > 0; UNIQUE(usuario, categoria)) — límite mensual recurrente por categoría

## Endpoints (protegidos salvo register/login, auth/refresh, auth/logout y los de recuperación de contraseña)
- /api/v1/usuarios: POST register · POST login (LoginResponseDTO {token, refreshToken, expiraEn}; T9.5: verificación de
  bloqueo email+IP ANTES de la BD, cada 401 suma un intento (también correo inexistente) y un éxito resetea; 5 fallos
  → 429 INTENTOS_LOGIN_EXCEDIDOS por 15 min) · GET me (UsuarioResponseDTO, sin entidad ni hash)
  · PUT me/contrasena ({contrasenaActual, nuevaContrasena}) → 200; exige la actual (401 si no) y revoca TODAS las sesiones
  · DELETE me ({contrasena}) → 204 irreversible: borra todo el dominio del usuario y sus sesiones (T9.2; 401 si la contraseña no coincide)
- /api/v1/auth (T9.1): POST refresh (rota el par {token, refreshToken, expiraEn}; token usado/revocado → 401 REFRESH_INVALIDO
  y revoca todas las sesiones del usuario) · POST logout (revoca el refresh; idempotente)
- /api/v1/auth (T9.3, públicos): POST recuperar-contrasena ({email}) → 200 SIEMPRE con mensaje genérico (anti-enumeración);
  el token de un solo uso sale por SMTP (log en consola con app.mail.log-only=true) · POST restablecer-contrasena
  ({token, nuevaContrasena}) → 200; token inválido/reusado/expirado → 400 TOKEN_VERIFICACION_INVALIDO; revoca todas las sesiones
- /api/v1/app/version (T9.4, público): GET ?plataforma=ANDROID|IOS&version=MAJOR.MINOR.PATCH → AppVersionResponseDTO
  {actualizacionObligatoria (version < min-*, app bloqueada), actualizacionSugerida (entre min-* y latest-*), mensaje,
  urlTienda, versionTerminos, versionPrivacidad}; plataforma/parámetro faltante → 400 PARAMETRO_INVALIDO,
  SemVer mal formado → 400 DATOS_INVALIDOS
- /api/v1/saldos/me
- /api/v1/movimientos: POST · GET me (?limit) · GET paginados/me (?page&size&desde&hasta&tipoMovimiento&categoriaId&montoMin&montoMax&q) · PUT {id} · DELETE {id}
  POST solo acepta INGRESO_GENERAL, GASTO_GENERAL y PAGO_DEUDA. ABONO_META y RETIRO_META se rechazan con 400:
  se registran únicamente desde la sección de metas, donde existe la meta que los respalda.
- /api/v1/categorias: GET
- /api/v1/metas: POST · GET me · POST {id}/abonar · POST {id}/retirar · PUT {id} · DELETE {id}
- /api/v1/deudas: POST · GET me · GET {id} · POST {id}/pagar · PUT {id} · PATCH {id}/cancelar · GET {id}/amortizacion · GET {id}/pagos (?page&size, fecha desc)
- /api/v1/presupuestos: GET me (DTO; sin configuración → 404 NO_ENCONTRADO) · POST me (DTO; porcentajes deben sumar 100.00 y ninguno ser negativo) · GET ejecucion/me (?month&year) · GET categorias/me · PUT categorias/me · GET categorias/ejecucion/me (?month&year) · GET historial/me (?desde&hasta=YYYY-MM)
- /api/v1/reportes/movimientos/exportar (?alcance&formato&mes&anio)
- /api/v1/simulaciones: POST credito (simulación sin persistencia, motor financiero + resumen educativo)

## Errores uniformes (T8b)
Todos los errores usan `application/problem+json`:
`{type:"about:blank", title, status, detail, instance, codigo, errores?, idError?}`.
`codigo` es estable para el cliente; `detail` va en español y no expone clases, SQL, rutas internas ni trazas.
`errores[]` solo en validación y `campo` usa el nombre JSON (p. ej. `condiciones.montoCapital`). `idError` solo en 500;
el mismo UUID queda en el log con la traza. Los 4xx se registran a INFO con status, `codigo`, método y path (sin query);
`DataIntegrityViolationException` mantiene el WARN. Los 500 se registran a ERROR con traza e `idError`.
- Paginación centralizada en `Paginacion.crear`: `page >= 0` y `1 <= size <= 100`; fuera de rango → 400 DATOS_INVALIDOS.
  Aplica a movimientos paginados, pagos de deuda y al `limit` de `/api/v1/movimientos/me`.

| Código | HTTP | Cuándo |
|---|---|---|
| VALIDACION_FALLIDA | 400 | Bean Validation de DTO o parámetros; incluye `errores[]` |
| SOLICITUD_INVALIDA | 400/405/415 | JSON malformado, enum desconocido, método o Content-Type no soportado |
| PARAMETRO_INVALIDO | 400 | Parámetro faltante o con tipo incorrecto |
| DATOS_INVALIDOS | 400 | IllegalArgumentException o CalculoFinancieroException (entrada inválida) |
| NO_AUTENTICADO | 401 | Token ausente o inválido |
| TOKEN_EXPIRADO | 401 | Token válido pero expirado; el cliente refresca con /auth/refresh (T9.1) |
| REFRESH_INVALIDO | 401 | Refresh token inválido/reusado/revocado o usuario eliminado; volver al login |
| TOKEN_VERIFICACION_INVALIDO | 400 | Token de restablecimiento inexistente, ya usado o expirado (no se distingue el motivo) |
| CREDENCIALES_INVALIDAS | 401 | Email inexistente o contraseña incorrecta (también al eliminar cuenta), con el mismo mensaje |
| INTENTOS_LOGIN_EXCEDIDOS | 429 | 5 fallos de login para el mismo email.toLowerCase()+IP (T9.5); el par queda bloqueado 15 min |
| PROHIBIDO | 403 | Recurso de otro usuario; el detail es fijo |
| NO_ENCONTRADO | 404 | EntityNotFoundException o ruta inexistente |
| CONFLICTO_REGLA_NEGOCIO | 409 | Regla de negocio (ErrorNegocio) o DataIntegrityViolationException |
| USUARIO_YA_REGISTRADO | 409 | RUT o email duplicado, con mensaje genérico |
| DEUDA_SOBREPAGO | 409 | El pago supera el saldo por pagar |
| ERROR_INTERNO | 500 | Cualquier otra excepción o invariante interna (incluye IllegalStateException) |

## Contrato OpenAPI y versionado (T8c)
- Todas las rutas públicas viven bajo `/api/v1` (por ejemplo `/api/v1/usuarios/login` y `/api/v1/deudas/{id}`).
  `POST /api/v1/usuarios/register` y `POST /api/v1/usuarios/login` siguen siendo públicos; el resto exige JWT.
- El contrato versionado está en `docs/openapi.json` y se genera desde `GET /v3/api-docs` con `OpenApiContractTest`.
  Para regenerarlo: `.\mvnw.cmd "-Dopenapi.update=true" test -Dtest=OpenApiContractTest`. Sin esa propiedad, el test falla si el archivo no coincide.
- springdoc-openapi está fijado en 2.6.0 (compatible con Spring Boot 3.3). Swagger UI y `/v3/api-docs` están disponibles solo en dev;
  `application-prod.properties` los deshabilita. El cliente Flutter consume `docs/openapi.json` o el endpoint en dev.
- Cada endpoint tiene `@Tag`, un `summary` en español y un `operationId` único y estable; los DTO documentan tasas en porcentaje,
  montos con escala 2, fechas y campos obligatorios con `requiredMode = REQUIRED`.
- Todos los endpoints documentan 400/401/403/404/409/500 con el esquema `ProblemaHttp` (`application/problem+json`).
  La guía de consumo para Flutter está en `docs/CLIENTE_FLUTTER.md`.

## Reglas vigentes
- Signo del movimiento (T1d): el signo lo impone el tipo, no el cliente. `SignoMovimiento.aplicar(tipo, monto)` devuelve el monto en valor absoluto con escala 2 (HALF_UP) y lo niega si el tipo es GASTO_GENERAL, PAGO_DEUDA o ABONO_META. Monto 0 o nulo → IllegalArgumentException (400). Es la única clase que llama a `.negate()` sobre montos de movimientos.
- Productores obligados a usar SignoMovimiento: MovimientoService.registrarMovimiento, DeudaService.registrarPago, MetaAhorroService (abonar, retirar y el reintegro al eliminar meta) y SmartSplitProcessor.
- Invariante de metas: tras abonar, retirar, Smart-Split y eliminar, montoActual de la meta = −(suma de movimientos ABONO_META vinculados a esa meta). Al eliminar una meta con ahorro se crea un RETIRO_META (positivo) por el total ahorrado, sin vincular a la meta.
- CHECK CHK_MOVIMIENTO_SIGNO (V4) sobre MOVIMIENTO: (tipo IN ('INGRESO_GENERAL','RETIRO_META') AND monto > 0) OR (tipo IN ('GASTO_GENERAL','PAGO_DEUDA','ABONO_META') AND monto < 0). Creado NOT VALID: el histórico puede violarlo y el arranque no falla, pero desde ya rechaza INSERT/UPDATE inválidos. El script trae la consulta de diagnóstico agrupada por tipo y el VALIDATE CONSTRAINT comentados.
- Si se agrega un valor a TipoMovimiento hay que actualizar SignoMovimiento y el CHECK en la misma tarea.
- Smart-Split: INGRESO_GENERAL con aplicarPresupuesto=true, config.activo, automatizarAhorroEnMetas y %ahorro>0 → un ABONO_META (negativo) por asignación. Desde T4 deja el vínculo movimiento_origen_id hacia el ingreso que lo originó; el ingreso se guarda antes de repartir para conocer su id.
- Historial filtrado y edición (T4): GET /api/v1/movimientos/paginados/me acepta filtros opcionales (desde/hasta ISO inclusivos, tipoMovimiento, categoriaId, montoMin/montoMax contra la magnitud (valor absoluto) del monto, q case-insensitive en descripcion con comodines % y _ escapados) vía JPA Specifications, siempre filtrando por el RUT del token; 400 si desde>hasta, montoMin/montoMax negativos o montoMin>montoMax. PUT/DELETE /api/v1/movimientos/{id} solo aplican a INGRESO_GENERAL y GASTO_GENERAL sin deuda ni meta (el resto → 409 "se modifica desde su módulo"), exigen propiedad (ajeno → 403) y rechazan con 409 un ingreso que tenga movimientos derivados (movimiento_origen_id). La fecha no se edita y el monto se reescribe con SignoMovimiento. El PUT es parcial: descripcion, monto y categoriaId son opcionales y solo se aplican los enviados (no nulos); los omitidos conservan su valor. Los movimientos históricos sin vínculo no tienen esa restricción. La conversión LocalDate→java.util.Date del rango y la zona del negocio (America/Santiago, constante) viven en FechasMovimiento (service.movimiento): ninguna otra clase mezcla java.time con java.util.Date ni usa ZoneId.systemDefault().
- Deuda hoy (V3): montoPagado = Σ pagos PAGO_DEUDA + montoPagadoPrevio; cuotasPagadas = cuotas PAGADA de la imputación (incluye previas); PAGADA cuando pagado ≥ montoTotal; al editar sin pagos se recalculan todas las condiciones (motor MotorFinanciero) y se persisten; con pagos registrados solo se permiten nombre, descripcion, tipoDeuda y cuotasPagadasPrevias (recalcula montoPagadoPrevio desde el cronograma persistido) y enviar condiciones, montoTotal o cantidadCuotas → 409; cuotasPagadasPrevias < cantidadCuotas; amortización reconstruye la tabla sin persistirla.
- Modalidades y tasas (T1a/T1b/T1c): SIN_INTERES no admite tasa; TASA_CONOCIDA exige exactamente una de tasaMensual o tasaAnualEfectiva (en %; la anual se convierte con tasaAnualAMensual); CUOTA_CONOCIDA exige valorCuota > 0 y NO admite ninguna tasa (si llega alguna → 400, antes se descartaba en silencio). Los DTO exponen las tasas en porcentaje y el motor trabaja con fracciones: la aritmética ÷/×100 vive solo en la capa DTO (dto/PorcentajeTasa), nunca en los servicios ni en el motor.
- Pagos e imputación (T2): DeudaService.registrarPago es el ÚNICO productor de PAGO_DEUDA (POST /api/v1/movimientos con ese tipo delega en él). ImputacionPagos (clase pura, service.finanzas) reparte los pagos reales cuota por cuota y, dentro de cada cuota, interés → costo adicional → capital; las cuotasPagadasPrevias cuentan como completas ya pagadas y los pagos reales empiezan en la siguiente. Nada de la imputación se persiste. modoPago: CUOTA_CALCULADA cobra lo que falta de la próxima cuota impaga (cuota + costo adicional); MONTO_PERSONALIZADO (por defecto) exige monto > 0. Sobrepago o deuda no PENDIENTE → 409; deuda ajena → 403.
- DeudaResponseDTO es solo datos: las tasas, el valor de cuota, la fecha de la última cuota y los saldos los calcula DeudaService en memoria tras la proyección JPQL (motor + calendario + ImputacionPagos, sin consultas extra). También calcula la próxima cuota impaga (proximaCuotaNumero, proximaCuotaMontoPendiente y proximaFechaVencimiento si hay fechaPrimeraCuota) e interesesPagados (incluye las cuotas previas). totalPorPagar = montoTotal − (previas + pagos reales); saldoCapital = montoCapital − capital imputado (aproxima liquidar hoy; NO incluye el interés devengado del mes en curso). Si una deuda no se puede recalcular, sus campos derivados quedan en null y el listado sigue. Expone las tasas en porcentaje (tasaMensual, tasaAnualEfectiva) y oculta la fracción cruda (tasaMensualFraccion, @JsonIgnore); la conversión fracción→porcentaje la hace el propio DTO.
- Detalle e historial (T3): GET /api/v1/deudas/{id} devuelve la ficha enriquecida y valida propiedad (ajena → 403), igual que /amortizacion y el resto del módulo (SecurityUtils.validarPropietario, AccessDeniedException → 403; nunca SecurityException). GET /api/v1/deudas/{id}/pagos es paginado (fecha desc) y devuelve {id, fecha, monto (positivo), capital, interes, costos, descripcion}; el desglose de cada pago se calcula en memoria como imputación acumulada tras ese pago − la del anterior (base = cuotas previas), no se persiste y usa una sola consulta de pagos. GET /api/v1/deudas/{id}/amortizacion agrega por cuota estado (PAGADA|PARCIAL|PENDIENTE), montoPagado y previa vía ImputacionPagos. CuotaSimulacionDTO se arma con el mapper compartido `CuotaSimulacionDTO.from(tabla, fechas)`, usado por el simulador y el detalle de deuda.
- PAGO_DEUDA puede crearse por /api/v1/deudas/{id}/pagar y por POST /api/v1/movimientos, pero ambas rutas terminan en DeudaService.registrarPago: mismo movimiento (negativo, por SignoMovimiento) y mismo estado de deuda.
- Ejecución presupuestaria: presupuesto = ingresos del mes × %; gasto real = GASTO_GENERAL y PAGO_DEUDA clasificados por categoria.tipoPresupuesto; ahorroRealizado = SUM de ABONO_META del mes.
- Presupuesto por categoría e historial (T5): PresupuestoService calcula el mes completo en la ventana semiabierta [inicioDeMes, inicioDeMesSiguiente) de FechasMovimiento. GET/PUT /categorias/me lee/reemplaza el conjunto de límites (PUT valida que cada categoría exista, que el límite sea > 0 y que no haya categorías repetidas; si no → 400) siempre por el RUT del token. GET /categorias/ejecucion/me devuelve por categoría {categoria, limite, gastado, porcentajeUsado, estado}: gastado = suma en abs de GASTO_GENERAL y PAGO_DEUDA de esa categoría en el mes; estado = EstadoEjecucion.desdePorcentaje (constantes UMBRAL_ALERTA=80 y UMBRAL_EXCEDIDO=100) → OK (<80), ALERTA (≥80) o EXCEDIDO (≥100). GET /historial/me?desde&hasta (YYYY-MM, inclusivos) reutiliza calcularEjecucionGeneral mes a mes (no duplica el cálculo); máximo 24 meses, rango invertido o formato inválido → 400. Un mes sin ingresos devuelve ceros (sin dividir por 0). POST /me exige necesidades + deseos + ahorro = 100.00 con compareTo y ningún porcentaje negativo; PresupuestoService concentra el guardado y responde ConfiguracionPresupuestoResponseDTO (no la entidad).
- Contrato de datos para móviles (T8a): la zona del negocio es America/Santiago como constante de FechasMovimiento (sin ZoneId.systemDefault()); los instantes (Movimiento.fecha, Deuda.fechaCreacion, Usuario.fechaRegistro) se serializan ISO-8601 en UTC con sufijo Z y las fechas de calendario (LocalDate, incluida MetaAhorro.fechaLimite) como yyyy-MM-dd. JVM (-Duser.timezone=UTC), Hibernate (hibernate.jdbc.time_zone=UTC) y Jackson (spring.jackson.time-zone=UTC) persisten en UTC; los tests corren con -Duser.timezone=${test.timezone} (UTC por defecto). Dinero: número JSON con máximo 13 enteros + 2 decimales en todos los DTO de entrada (@Digits(13,2)); el servidor es la única fuente de aritmética y las tasas quedan fuera de esa regla.
- La categoría 'Deudas' (NECESIDAD) se asigna automáticamente a los pagos de deuda.
- Seguridad: JWT HMAC-SHA256 stateless; SecurityUtils.validarPropietario(rut).
- Sesión móvil (T9.1): access de 15 min + refresh opaco rotativo de 90 días persistido solo como hash SHA-256
  (ver RefreshTokenService). La rotación marca fechaUso/fechaRevocación del token consumido; el reuso de un token
  revocado revoca TODAS las sesiones del usuario (detección de robo). El logout revoca y la eliminación de cuenta
  borra los refresh tokens.
- Borrado de cuenta (T9.2): DELETE /api/v1/usuarios/me exige la contraseña actual (BCrypt; si no → 401
  CREDENCIALES_INVALIDAS genérico) y corre en una transacción (@Transactional en UsuarioService.eliminarCuenta):
  EliminacionDeCuentaService borra hijo→padre (asignaciones → presupuesto_categoria → configuración → movimientos
  → metas → deudas → refresh tokens) y al final se elimina el usuario. Desde V10 las FKs hacia USUARIO tienen
  ON DELETE CASCADE (respaldo); las FKs entre hijos siguen en NO ACTION a propósito. RUT/correo quedan libres
  para re-registro.
- Contraseñas (T9.3): PasswordService concentra recuperación y cambio. POST /auth/recuperar-contrasena responde
  200 genérico siempre (no existe el correo → no registra nada, solo un log INFO); si existe, invalida los tokens
  RESET_PASSWORD previos, guarda un token opaco (hash SHA-256, 1 h, un solo uso en TOKEN_VERIFICACION) y lo envía
  con CorreoService: app.mail.log-only=true lo loguea en consola (dev/test, sin SMTP); false usa JavaMailSender
  (exige spring.mail.* en prod; sin bean configurado falla con ERROR_INTERNO). POST /auth/restablecer-contrasena
  valida el hash, marca usado, guarda BCrypt y revoca TODAS las sesiones (RefreshTokenService.revocarTodas);
  token inválido/reusado/expirado → 400 TOKEN_VERIFICACION_INVALIDO con la misma superficie. PUT /usuarios/me/contrasena
  exige la contraseña actual (si no → 401 CREDENCIALES_INVALIDAS) y también revoca todas las sesiones; el JWT de
  acceso vigente mantiene su validez residual (máx. 15 min) porque es stateless. La URL opcional de restablecimiento
  se configura en app.mail.url-restablecer (vacía = el correo muestra solo el token).
- Versión mínima (T9.4): GET /api/v1/app/version compara la versión SemVer reportada por la app contra los umbrales
  por plataforma de application.properties (app.version.min-*/latest-* con MAJOR.MINOR.PATCH). version < min-* →
  actualizacionObligatoria=true (bloqueo), entre min-* y latest-* → actualizacionSugerida=true; los tres mensajes son
  constantes en VersionAppService. SemVerUtils (service.version) valida el formato (3 segmentos de hasta 3 dígitos;
  fallo → 400 DATOS_INVALIDOS) y compara numéricamente por segmentos ("1.10.0" > "1.9.1"), admitiendo ceros a la
  izquierda. Publicar release = subir latest-*; activar bloqueo = subir min-*. Las plataformas son el enum
  Plataforma (ANDROID, IOS): valor desconocido o parámetro faltante → 400 PARAMETRO_INVALIDO. Las URLs de tienda y
  las versiones de legales (app.version.terminos/privacidad) viajan en la respuesta para la pantalla de legales.
- Fuerza bruta en login (T9.5): LoginRateLimiterService (paquete security, caché Caffeine con ventana de 15 min
  re-expirada por cada fallo) cuenta los intentos fallidos por la clave email.toLowerCase()+":" +IP, con IP resuelta
  de la primera dirección de X-Forwarded-For o getRemoteAddr() (resolverIp, llamada desde el controller). El bloqueo
  se consulta ANTES de tocar la BD en UsuarioService.autenticarUsuario(loginDTO, ipCliente): 5 fallos del par → 429
  INTENTOS_LOGIN_EXCEDIDOS (controller no llega a findByEmail); cada 401 (contraseña incorrecta o correo inexistente)
  suma un intento; un login exitoso resetea el contador. Contador en memoria del proceso (una instancia en Render);
  al escalar horizontalmente mover la caché a Redis. El 429 se documenta en el contrato vía el customizer de
  OpenApiConfig por operationId="loginUsuario".
- Esquema: 10 tablas; V1 (DDL), V2 (seed de 10 categorías), V3 (deuda con condiciones de crédito), V4 (CHECK de signo de MOVIMIENTO), V5 (movimiento_origen_id: fk_movimiento_origen + idx_movimiento_origen_id, vínculo de Smart-Split), V6 (presupuesto_categoria: límite mensual por categoría, chk limite > 0, UNIQUE(usuario, categoria), RLS), V7 (renombra los nombres de constraint creados en V3–V6), V8 (los devuelve a minúscula, valida el CHECK de signo de MOVIMIENTO, agrega 6 índices de FK y filtro y activa RLS en las 8 tablas de dominio), V9 (T9.1: AUTH_REFRESH_TOKEN con índices y RLS), V10 (T9.2: ON DELETE CASCADE en las 6 FKs hacia USUARIO) y V11 (T9.3: TOKEN_VERIFICACION con FK CASCADE, hash único, índices y RLS) en db/migration/postgresql.

## Visión del módulo de deudas
Dos usos con un mismo motor de cálculo: (1) simular un endeudamiento antes de contraerlo (cuánto cuesta, por cuánto tiempo, efecto de tasa y plazo, con fines de decisión y educación financiera) y (2) llevar la deuda real al detalle (cuánto falta, historial de pagos). Una deuda guardada = condiciones de una simulación + pagos.

## Tareas pendientes (docs/tareas/)
[x] T0 BigDecimal · [x] T1a motor financiero y calendario · [x] T1b simulador (sin persistir) · [x] T1c deuda con condiciones, fechas y cuotas previas · [x] T2 pagos, imputación y saldos · [x] T3 detalle de deuda (ficha, cronograma, historial) · [x] T4 filtros y edición de movimientos · [x] T5 presupuesto por categoría e historial · [x] T8a contrato de datos (fechas, zona y dinero) · [x] T8b errores uniformes · [x] T8c OpenAPI, /api/v1 y guía Flutter · [x] T9.1 sesión móvil (refresh rotativo) · [x] T9.2 borrado de cuenta con cascada · [x] T9.3 contraseñas (recuperación y cambio) · [x] T9.4 versión mínima de la app · [x] T9.5 anti-fuerza bruta en login. **Fase A de T9 (T9.1–T9.5) concluida.** Pendientes de T9 (desacopladas): T9.6 push (FCM/APNs) y T9.7 legales — en docs/tareas/T9-sesion-movil.md. · BACKLOG.md (diferido).
Orden: [x] T0 → [x] T1a → [x] T1b (el frontend ya puede consumirlo) → [x] T1c → [x] T2 → [x] T3. T4 y T5 solo dependen de T0.
