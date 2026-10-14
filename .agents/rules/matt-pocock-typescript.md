---
trigger: manual
---

# Matt Pocock Skills — Índice

Skills instalados en `.agents/skills/`, disponibles en este workspace.

## Engineering (Ingeniería de código)

### User-invoked (invocación manual)

| Skill | Descripción |
|---|---|
| `ask-matt` | Router de skills: te dice qué skill o flujo encaja en tu situación. |
| `grill-with-docs` | Sesión de grilling que construye el modelo de dominio del proyecto, sharpea terminología y actualiza `GLOSSARY.md` y ADRs en línea. |
| `triage` | Mueve issues a través de un state machine de roles de triage. |
| `improve-codebase-architecture` | Escanea el codebase en busca de oportunidades de mejora, presenta un reporte HTML visual, y grillea la opción elegida. |
| `setup-matt-pocock-skills` | Configura el repo para las engineering skills (issue tracker, triage labels, domain doc layout). Ejecutar una vez por repo. |
| `to-spec` | Convierte la conversación actual en un spec y lo publica en el issue tracker. |
| `to-tickets` | Divide cualquier plan, spec o conversación en tickets tracer-bullet con dependencias bloqueantes. |
| `implement` | Construye el trabajo descrito por un spec o set de tickets, usando /tdd en seams pre-acordados y cerrando con /code-review. |
| `implement-spec` | Implementa un spec completo en una integration branch, trabajando tickets como task graph con subagentes concurrentes. |
| `wayfinder` | Planifica un chunk enorme de trabajo como mapa compartido de decision tickets en el issue tracker, resolviéndolos uno a uno. |
| `retro` | Sugiere mejoras al entorno del coding agent (navegación, checks automatizados, coding standards, tooling) tras una sesión. |

### Model-invoked (invocación automática por el agente)

| Skill | Descripción |
|---|---|
| `prototype` | Construye un prototipo desechable para responder una pregunta de diseño (HTML o variaciones de UI). |
| `diagnosing-bugs` | Loop de diagnóstico disciplinado para bugs difíciles: feedback loop → minimizar → hipótesis → instrumentar → fix → regression test. |
| `research` | Investiga una pregunta contra fuentes primarias de alta confianza y captura los hallazgos en un Markdown citado. |
| `tdd` | Desarrollo guiado por tests con loop red-green-refactor. Construye features o corrige bugs un slice vertical a la vez. |
| `domain-modeling` | Construye y sharpea el modelo de dominio del proyecto, desafía términos contra el glosario y actualiza `GLOSSARY.md` y ADRs. |
| `codebase-design` | Disciplina compartida para diseñar deep modules: mucho comportamiento tras una interfaz pequeña, en un seam limpio y testeable. |
| `code-review` | Review de dos ejes del diff (Standards + Spec) como subagentes paralelos para que ninguno contamine al otro. |
| `pr` | Define la forma que debe tomar el cuerpo de un pull request: resumen visual, evidencia before/after, y llamada al peligro de merge. |
| `wizard` | Genera un wizard bash interactivo para guiar a un humano en pasos que solo él puede hacer (infra, credenciales, migraciones). |

---

## Productivity (Flujo de trabajo general)

### User-invoked (invocación manual)

| Skill | Descripción |
|---|---|
| `grill-me` | Entrevista relentless sobre un plan o diseño hasta resolver cada rama del árbol de decisión. |
| `handoff` | Compacta la conversación actual en un documento de handoff para que otro agente continúe el trabajo. |
| `teach` | Enseña al usuario una nueva skill o concepto en múltiples sesiones, usando el directorio actual como workspace de enseñanza. |
| `to-questionnaire` | Convierte una decisión que no puedes responder solo en un cuestionario Markdown para la persona indicada. |
| `wait-what` | Actívalo cuando un mensaje no aterriza. El agente lo re-pitchea con el contexto que te falta, en inglés llano. |

### Model-invoked (invocación automática por el agente)

| Skill | Descripción |
|---|---|
| `grilling` | Entrevista al usuario sobre un plan, decisión o idea hasta resolver cada rama del árbol de decisión. Primitivo reutilizable. |
| `writing-for-agents` | Guía para escribir documentos para agentes: skills, AGENTS.md/CLAUDE.md y cualquier doc al que apunte un agente. |

---

> Skills source: [mattpocock/skills](https://github.com/mattpocock/skills) — instalados en `.agents/skills/`
