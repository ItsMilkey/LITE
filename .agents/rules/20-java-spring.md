# Convenciones Java / Spring (Activation: Glob  **/*.java)

- Sigue el estilo de las clases vecinas (nombres de dominio en español: Deuda, Movimiento, cuotas…).
- Inyección por constructor; sin @Autowired en campos.
- Consultas: derivadas o @Query JPQL. Evita N+1 usando proyecciones a DTO como en DeudaRepository. Listados paginados devuelven PageResponseDTO.
- Validación de formato en DTOs (Bean Validation); reglas de negocio en el servicio.
- No mezcles java.util.Date con java.time dentro de la misma clase: sigue el tipo de la entidad que tocas.
- Enums de dominio en model/enums. Sus valores se replican en los CHECK de la BD: si agregas un valor, actualiza también la migración.
- Sin números mágicos: constantes con nombre para umbrales (p. ej. alerta al 80 %).
- Si agregas un valor a TipoMovimiento, actualiza SignoMovimiento y el CHECK de MOVIMIENTO en la misma tarea.
- Comentarios solo para explicar el "por qué"; Javadoc breve en servicios públicos.
