# HU-01 — Cuenta y seguridad

Casos de uso: **UC1–UC3** · Endpoints: `/api/usuarios` · Diagrama de flujo:
[`09-flujo-auth.md`](../diagramas/09-flujo-auth.md).

### HU-01-01 — Registrarse
**Como** visitante, **quiero** crear una cuenta con mi RUT, nombre, apellido, email y contraseña, **para** empezar a registrar mis finanzas.
- Criterios de aceptación:
  - `POST /api/usuarios/register` crea el usuario y es **público** (no exige token).
  - La contraseña se guarda con hash **BCrypt**; nunca en claro ni en la respuesta.
  - Al registrarse se crea la meta **"Ahorros"** por defecto (marcada como protegida).
  - Email duplicado o datos inválidos → **400**.
- Trazabilidad: UC1 · `POST /api/usuarios/register` · base
- Acción en la app (Flutter): pantalla de registro.
- Estado: **implementada**

### HU-01-02 — Iniciar sesión
**Como** visitante registrado, **quiero** iniciar sesión con mi email y contraseña, **para** obtener un token que me identifique.
- Criterios de aceptación:
  - `POST /api/usuarios/login` autentica por **email** y contraseña; es **público**.
  - Devuelve un **JWT HMAC-SHA256** con `subject = RUT` y expiración de **24 h** (`jwt.expirationInMs=86400000`).
  - Credenciales inválidas → **401**.
- Trazabilidad: UC2 · `POST /api/usuarios/login` · base
- Acción en la app (Flutter): pantalla de login; guarda el token y lo envía como `Bearer`.
- Estado: **implementada**

### HU-01-03 — Ver mi perfil
**Como** usuario autenticado, **quiero** ver mis datos de perfil, **para** confirmar quién soy en la app.
- Criterios de aceptación:
  - `GET /api/usuarios/me` responde el perfil del usuario **del token** (no acepta RUT por parámetro).
  - Sin token válido → **401**.
- Trazabilidad: UC3 · `GET /api/usuarios/me` · base
- Acción en la app (Flutter): sección "Perfil".
- Estado: **implementada**

### HU-01-04 — Cerrar sesión
**Como** usuario, **quiero** cerrar sesión, **para** que nadie más use mi cuenta en el dispositivo.
- Criterios de aceptación:
  - El backend es **stateless**: no hay endpoint de logout; la app descarta el JWT.
  - `SecurityConfig` usa `SessionCreationPolicy.STATELESS` y CSRF deshabilitado.
- Trazabilidad: — (diseño stateless) · base
- Acción en la app (Flutter): botón "Cerrar sesión" que borra el token local.
- Estado: **parcial** (comportamiento del cliente)

### HU-01-05 — Registro rechazado por email duplicado o datos inválidos
**Como** visitante, **quiero** que un registro inválido se rechace, **para** no crear cuentas duplicadas o incompletas.
- Criterios de aceptación:
  - Email ya registrado, campos faltantes o formato inválido → **400** y no se crea el usuario.
- Trazabilidad: UC1 · `POST /api/usuarios/register` · base
- Acción en la app (Flutter): mensaje de error bajo el campo correspondiente.
- Estado: **implementada**

### HU-01-06 — Login rechazado por credenciales inválidas
**Como** visitante, **quiero** recibir un 401 cuando mis credenciales no sirven, **para** no quedar en un estado ambiguo.
- Criterios de aceptación:
  - Email inexistente o contraseña incorrecta → **401**, sin revelar cuál de los dos falló.
- Trazabilidad: UC2 · `POST /api/usuarios/login` · base
- Acción en la app (Flutter): mensaje genérico "credenciales inválidas".
- Estado: **implementada**

### HU-01-07 — Acceso bloqueado sin token válido
**Como** usuario, **quiero** que toda ruta protegida exija un JWT válido, **para** que solo yo acceda a mis datos.
- Criterios de aceptación:
  - Sin cabecera `Bearer`, token expirado o firma inválida → **401** (`ProblemDetail` JSON).
  - Públicos solo `register`, `login`, `OPTIONS` y `GET/HEAD /`.
- Trazabilidad: (aplica a todas las rutas) · `SecurityConfig` · base
- Acción en la app (Flutter): si el 401 llega, redirige a login.
- Estado: **implementada**
