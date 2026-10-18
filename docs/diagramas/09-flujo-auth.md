# 09 — Flujo: autenticación y JWT

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código en HEAD `9e9385e`)

## Qué responde
Cómo se registra y autentica un usuario, cómo se emite el JWT en el login y cómo se valida en cada petición posterior.

## Fuentes
- `src/main/java/com/example/saveup/controller/UsuarioController.java`
- `src/main/java/com/example/saveup/service/UsuarioService.java`
- `src/main/java/com/example/saveup/security/JwtService.java`, `JwtAuthenticationFilter.java`, `SecurityUtils.java`
- `src/main/java/com/example/saveup/config/SecurityConfig.java`

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as UsuarioController
    participant S as UsuarioService
    participant R as UsuarioRepository
    participant MR as MetaAhorroRepository
    participant PE as PasswordEncoder BCrypt
    participant J as JwtService
    participant F as JwtAuthenticationFilter

    Note over U,F: Registro
    U->>C: POST /api/usuarios/register
    C->>S: registrarUsuario(dto)
    S->>R: existsById(rut)
    S->>R: existsByEmail(email)
    alt RUT o email ya existen
        S-->>C: IllegalStateException
        C-->>U: 409 error
    else datos libres
        S->>PE: encode(contrasena)
        PE-->>S: hash BCrypt
        S->>R: save(usuario)
        S->>MR: save(meta "Ahorros" por defecto)
        C-->>U: 201 message
    end

    Note over U,J: Login
    U->>C: POST /api/usuarios/login
    C->>S: autenticarUsuario(dto)
    S->>R: findByEmail(email)
    S->>PE: matches(contrasena, hash)
    alt credenciales invalidas
        S-->>C: BadCredentialsException
        C-->>U: 401 error
    else credenciales validas
        S-->>C: Usuario
        C->>J: generateToken(rut, email)
        J-->>C: JWT HS256 (subject=rut, exp 24h)
        C-->>U: 200 token
    end

    Note over U,F: Peticion autenticada
    U->>F: Authorization Bearer token
    F->>J: isTokenValid(token)
    alt token invalido o expirado
        F-->>U: 401 ProblemDetail (CustomAuthenticationEntryPoint)
    else token valido
        J-->>F: true
        F->>J: extractRut(token)
        J-->>F: rut
        F->>F: SecurityContext principal=rut, ROLE_USER
        F-->>U: continua al controlador
    end
```

## Notas y "por confirmar"
- El token es **HS256**; el `subject` es el RUT y lleva el `email` como claim. La clave se toma de `jwt.secret` y se rellena a 32 bytes si es más corta.
- `register` y `login` son los únicos endpoints públicos junto con `OPTIONS /**` y `GET/HEAD /`.
- La identidad de cada recurso se resuelve siempre con `SecurityUtils.getAuthenticatedRut()`; nunca se acepta el RUT desde el cuerpo.
- `POST /api/movimientos` con tipo `PAGO_DEUDA` no aplica aquí: se resuelve en el flujo de pago de deuda ([`11-flujo-pago-deuda.md`](11-flujo-pago-deuda.md)).
