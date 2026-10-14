---
description: 
---

# nueva-feature
Implementa una tarea de docs/tareas/ con plan, código, tests y revisión. Uso: /nueva-feature docs/tareas/T1-deuda-interes-cae.md

1. Lee las reglas de .agents/rules y docs/CONTEXT.md. Lee SOLO la tarea indicada y los archivos de código que vayas a tocar. No leas la documentación completa (usa búsqueda puntual si necesitas un detalle).
2. Escribe el plan (máx. 10 líneas): archivos a tocar, migración (número y tablas) y tests. Detente y espera mi "ok".
3. Implementa en este orden: migración (postgresql) → entidad → repositorio → servicio → DTO → controlador.
4. Escribe los tests de los criterios de aceptación de la tarea.
5. Compila y corre los tests con el wrapper de Maven. Si fallan, corrige la causa; no desactives tests.
6. Revisa tu propio diff contra los criterios de aceptación y la regla de dinero (sin double). Lista lo que no cumpliste.
7. Actualiza docs/CONTEXT.md si cambió el modelo, los endpoints o las reglas.
8. Cierra con un resumen de máx. 10 líneas.
