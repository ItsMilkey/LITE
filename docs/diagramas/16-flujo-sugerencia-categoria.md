# 16 — Flujo: sugerencia de categoría por texto

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código de T10b)

## Qué responde
Cómo el backend convierte un texto libre ("supermercado", "cena aniversario") en hasta 3 categorías con confianza y
motivo, sin persistir nada ni registrar el texto del usuario.

## Fuentes
- `src/main/java/com/example/saveup/controller/CategoriaController.java`
- `src/main/java/com/example/saveup/service/categoria/SugerenciaCategoriaService.java`
- `src/main/java/com/example/saveup/service/categoria/SugeridorCategoria.java`
- `src/main/java/com/example/saveup/service/categoria/DiccionarioCategorias.java`
- `src/main/java/com/example/saveup/service/categoria/NormalizadorTexto.java`
- `src/main/java/com/example/saveup/service/categoria/DistanciaEdicion.java`
- `src/main/java/com/example/saveup/service/categoria/FuenteHistorialCategoria.java`
- `src/main/resources/categorias/diccionario.json`
- `src/test/resources/categorias/sugerencias-golden.csv`

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as CategoriaController
    participant S as SugerenciaCategoriaService
    participant R as CategoriaRepository
    participant D as DiccionarioCategorias
    participant G as SugeridorCategoria
    participant H as FuenteHistorialCategoria

    U->>C: GET /api/v1/categorias/sugerencia?texto&contexto
    C->>C: SecurityUtils.getAuthenticatedRut()
    C->>S: sugerir(rut, texto, contexto)
    S->>S: validar texto.trim() de hasta 80 caracteres
    alt texto ausente o mayor a 80
        S-->>C: ParametroInvalidoException (400 PARAMETRO_INVALIDO)
    else
        S->>R: findAll()
        R-->>S: catalogo de 18 categorias
        S->>D: entradasValidas(codigos)
        D-->>S: entradas del catalogo (WARN por codigo desconocido)
        S->>G: sugerir(texto, contexto, catalogo, entradas, rut, historial)
        G->>G: N(texto), tokens y palabras vacias
        alt menos de 3 caracteres normalizados o sin tokens
            G-->>S: lista vacia
        else
            G->>G: frase 1.00 / palabra 0.90 / prefijo 0.75 / aproximada 0.65 (solo 1 token)
            G->>H: codigoPara(rut, textoNormalizado)
            H-->>G: Optional(codigo)
            G->>G: max(puntaje, 0.95) HISTORIAL, filtrar mayor o igual a 0.60, ordenar y limitar a 3
        end
        G-->>S: SugerenciaCategoriaResponseDTO
        S-->>C: DTO con texto original y ambigua
        C-->>U: 200
    end
```

## Notas y "por confirmar"
- La identidad sale **solo** del JWT (`SecurityUtils.getAuthenticatedRut()`); el endpoint no acepta `usuarioRut`.
- `contexto` es opcional (`GASTO` por defecto). `GASTO` excluye `SUELDO` y `AHORRO`; `INGRESO` solo permite `SUELDO` y
  `OTROS`. Un valor desconocido lo rechaza Spring como `MethodArgumentTypeMismatchException` → 400 `PARAMETRO_INVALIDO`.
- El puntaje efectivo es `base × peso` con `BigDecimal` escala 2 (`HALF_UP`). La coincidencia **aproximada** solo se
  evalúa cuando el texto normalizado tiene **un único token**: una frase no se "corrige" por una letra.
- `ambigua` es verdadera si hay al menos 2 sugerencias y la diferencia entre la primera y la segunda es
  **estrictamente menor** que 0,10. Los empates se ordenan por `codigo` alfabético.
- El historial es una interfaz con implementación vacía en T10b (`FuenteHistorialCategoriaVacia`); T10c la reemplaza
  por el historial real. Cuando devuelve un código, esa categoría sube a 0,95 con motivo `HISTORIAL`.
- El diccionario se carga una vez desde `classpath:categorias/diccionario.json`; los códigos que no existen en el
  catálogo se ignoran con un WARN y no fallan el arranque.
- No hay persistencia ni log del texto (G9). La respuesta ecoa el texto original exacto para que Flutter descarte
  respuestas atrasadas cuando el usuario siguió escribiendo.
