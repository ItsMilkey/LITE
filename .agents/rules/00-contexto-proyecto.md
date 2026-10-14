# SaveUp Lite — contexto base (Activation: Always On)

Backend REST de finanzas personales. Spring Boot 3.3.0 · Java 21 · Maven.
BD: Supabase PostgreSQL (session pooler, SSL) en dev y prod. Migraciones: Flyway 10.
Paquete raíz: com.example.saveup — controller → service → repository → model (+ dto, config).
Mapa del dominio (entidades, endpoints, reglas): docs/CONTEXT.md
Glosario financiero: docs/GLOSSARY.md ("CAE" tiene dos significados; en el código usa siempre cargaAnualEquivalente para el indicador financiero).
Documentación completa: docs/DOCUMENTACION_COMPLETA_SAVEUP_LITE.txt (65 KB: NO la leas completa; busca solo la sección que necesites).

## Reglas duras
1. Identidad solo desde el JWT (SecurityUtils.getAuthenticatedRut()). Nunca aceptar usuarioRut en el body. Todo recurso accedido por ID valida propiedad (anti-IDOR).
2. Dinero: BigDecimal en entidades, DTOs, servicios y repositorios. Prohibido double/Double en código nuevo. Redondeo explícito (RoundingMode.HALF_UP), escala 2.
3. Migraciones: nunca editar scripts ya aplicados. Cada cambio de esquema = nuevo script V{n+1} en db/migration/postgresql. Las entidades deben pasar ddl-auto=validate.
4. Los controladores no contienen lógica. Los servicios llevan @Transactional y devuelven DTOs, nunca entidades.
5. Regla de negocio violada → 409; recurso ajeno → 403; validación → 400. Sigue el formato de error ya usado en el proyecto.
6. Todo cálculo o regla de negocio nueva lleva tests unitarios JUnit 5. Los tests unitarios no deben requerir conexión a la base de datos.

## Forma de trabajar
- Cambia solo lo que pide la tarea. No refactorices código no relacionado.
- Antes de editar: lista los archivos que vas a leer/modificar y un plan de máximo 10 líneas. Si algo es ambiguo, pregunta (una sola ronda).
- Compila y corre los tests (wrapper de Maven del repo) antes de dar la tarea por terminada.
- Si cambias modelo, endpoints o reglas, actualiza la sección correspondiente de docs/CONTEXT.md.
- Parámetros y secretos solo en variables de entorno del sistema o del IDE (nunca en archivos del repo ni .env). El agente no tiene acceso a la base de datos: no pidas ni escribas credenciales.
- Cierra con un resumen de máx. 10 líneas: qué cambió, qué migraciones agregaste, qué falta.
