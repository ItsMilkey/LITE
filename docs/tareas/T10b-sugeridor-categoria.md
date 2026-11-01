# T10b — Sugeridor de categoría

Depende de: T10a (catálogo con `codigo`). Reglas comunes: ver T10-decisiones.md (G1 a G10). Modelo sugerido: Sonnet 4.6 Thinking. Empieza con el Paso 0 y espera el "ok".

## 1. Objetivo

Dado un texto libre que escribe el usuario ("supermercado", "cuenta entel", "cena aniversario"), devolver hasta 3 categorías del catálogo con una confianza, para preseleccionar el menú desplegable al crear una partida o registrar un gasto.

Principios: determinista, explicable, sin IA externa, nunca asigna en silencio (el usuario siempre puede cambiar la selección), y no inventa: si no está seguro, no sugiere.

## 2. Paso 0 — Auditoría (solo lectura)

1. Que T10a esté aplicada: las 18 categorías con `codigo`.
2. Forma de los controladores y servicios de `categorias` (para ubicar el endpoint nuevo).
3. Cómo cargan recursos otros componentes (patrón de lectura de archivos del classpath, si existe).
4. Si el proyecto ya tiene alguna utilidad de normalización de texto o distancia de edición (no duplicarla).
5. Que `GlobalExceptionHandler` mapea parámetros faltantes o inválidos a PARAMETRO_INVALIDO.

## 3. Contrato

`GET /api/v1/categorias/sugerencia?texto=<texto>&contexto=<GASTO|INGRESO>`

- `texto`: obligatorio, entre 0 y 80 caracteres después de recortar espacios. Más de 80 o ausente → 400 PARAMETRO_INVALIDO. Menos de 3 caracteres (ya normalizado) → 200 con lista vacía (el servidor tolera que la app escriba rápido).
- `contexto`: opcional, por defecto GASTO.
  - GASTO: excluye las categorías SUELDO y AHORRO (las partidas y los gastos no las usan; ver DC14 y DC15).
  - INGRESO: solo SUELDO y OTROS.

Respuesta 200:

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

- `sugerencias`: de 0 a 3 elementos ordenados por confianza descendente y, si empatan, por `codigo` alfabético.
- `texto` devuelve el texto recibido, para que la app descarte respuestas atrasadas.
- `motivo`: PALABRA_CLAVE (frase o palabra exacta), PREFIJO, APROXIMADA o HISTORIAL.
- El endpoint está protegido por JWT y no persiste nada. No registra el texto en logs (G9).

Guía para la app (se documenta en `CLIENTE_FLUTTER.md`): esperar unos 400 ms sin teclear antes de llamar, ignorar respuestas cuyo `texto` ya no coincida con el campo, preseleccionar la primera sugerencia, mostrar las demás como opciones rápidas y, si `ambigua` es verdadera, pedir confirmación visible. Si la lista viene vacía, no preseleccionar nada.

## 4. Algoritmo

Todos los puntajes son `BigDecimal` de escala 2 (HALF_UP). Nada de `double`.

**Normalización** `N(s)`: pasar a minúsculas, quitar tildes (descomposición Unicode y eliminación de marcas), reemplazar todo carácter que no sea `a-z` ni `0-9` por espacio, colapsar espacios y recortar. Se obtiene una lista de tokens. Se descartan las palabras vacías: de, del, la, las, el, los, un, una, y, e, en, a, al, para, por, con, mi, mis, tu, tus, su, sus.

**Diccionario:** archivo `src/main/resources/categorias/diccionario.json`. Por cada `codigo`, una lista de entradas `{ "t": "<texto de 1 o más palabras>", "peso": 1.00 }` (el peso es opcional y vale 1,00 por defecto). Al cargar, cada entrada se normaliza con la misma `N()` (incluida la eliminación de palabras vacías), de modo que "permiso de circulación" se guarda como las palabras "permiso circulacion". Al cargar, se valida que cada `codigo` exista en el catálogo; los que no existan se ignoran con un WARN.

**Puntaje de una entrada contra el texto del usuario:**

| Tipo de coincidencia | Condición | Puntaje base |
|---|---|---|
| Frase | La entrada tiene 2 o más palabras y aparecen consecutivas entre los tokens | 1,00 |
| Palabra exacta | La entrada tiene 1 palabra y es igual a un token | 0,90 |
| Prefijo | La entrada (una sola palabra) empieza con el token que el usuario va escribiendo, y el token tiene al menos 4 caracteres. No se aplica al revés (un token que empieza con una entrada no es coincidencia: las derivaciones como plurales las cubre la coincidencia aproximada) | 0,75 |
| Aproximada | Distancia de Damerau-Levenshtein ≤ 1 si ambas palabras tienen al menos 5 caracteres, o ≤ 2 si ambas tienen al menos 9; solo entradas de 1 palabra | 0,65 |

El puntaje efectivo es `base × peso` (escala 2). Para cada categoría se toma el máximo entre todas sus entradas y todos los tokens; el tipo de coincidencia del máximo define el `motivo`.

**Historial** (se enchufa después, en T10c): interfaz `FuenteHistorialCategoria` con un método que, dado el usuario y el nombre normalizado, devuelve opcionalmente un `codigo`. En T10b la implementación por defecto no hace nada. Cuando devuelve un valor, el puntaje de esa categoría pasa a `max(puntaje, 0,95)` con motivo HISTORIAL.

**Selección:** se conservan las categorías con puntaje ≥ 0,60 y permitidas por el `contexto`; se ordenan; se devuelven hasta 3. `ambigua` es verdadera si hay al menos 2 candidatos y la diferencia entre el primero y el segundo es **estrictamente menor** que 0,10. `confianza` es el puntaje.

Sin coincidencia suficiente la lista es vacía; el servidor nunca devuelve "Otros" por su cuenta.

## 5. Contenido del diccionario

El agente propone el diccionario inicial a partir de la tabla de semilla siguiente y del catálogo de T10a. El archivo es revisable y debe pasar la revisión del usuario antes de cerrar la tarea. Toda palabra agregada debe poder justificarse con un caso de la sección 7.

Semilla mínima (palabras y marcas de uso en Chile; el agente puede completar sinónimos, sin quitar estas):

| Categoría | Entradas mínimas |
|---|---|
| ALIMENTACION_BASE | supermercado, super, jumbo, lider, unimarc, santa isabel, tottus, feria, abarrotes, verduras, carniceria, panaderia, comida (peso 1,00) |
| RESTAURANTES_SALIDAS | restaurante, restoran, delivery, pedidosya, pedidos ya, uber eats, rappi, sushi, pizza, cafe, cafeteria, bar, cena, almuerzo fuera, salida, comida (peso 0,90) |
| TRANSPORTE_MOVILIDAD | bip, metro, micro, colectivo, bencina, copec, shell, petrobras, tag, peaje, estacionamiento, uber, cabify, didi, pasaje, transporte |
| VIVIENDA_SUMINISTROS | arriendo, dividendo, gastos comunes, luz, agua, gas, enel, aguas andinas, metrogas, internet, fibra, wifi, entel, movistar, claro, wom, vtr, casa |
| SALUD_BIENESTAR | farmacia, cruz verde, salcobrand, ahumada, doctor, medico, dentista, consulta, examen, isapre, fonasa, copago, kinesiologo, psicologo, lentes, remedios |
| EDUCACION_FORMACION | colegio, universidad, arancel, matricula, curso, utiles, libro, jardin infantil, magister, educacion |
| SEGUROS_IMPUESTOS | seguro, contribuciones, patente, permiso de circulacion, revision tecnica, notaria, tramite, impuesto |
| MASCOTAS | perro, gato, mascota, veterinario, vacunas mascota, alimento perro, alimento gato |
| HOGAR_CONSUMIBLES | aseo, limpieza, detergente, ampolleta, gasfiter, ferreteria, hogar, consumibles |
| CUIDADO_PERSONAL_VESTUARIO | ropa, zapatillas, peluqueria, corte de pelo, barberia, maquillaje, cosmetica, perfume, vestuario |
| EQUIPAMIENTO_TECNOLOGIA | mueble, sofa, refrigerador, lavadora, notebook, computador, audifonos, televisor, tecnologia |
| SUSCRIPCIONES_DIGITAL | netflix, spotify, disney, hbo max, prime video, youtube premium, ps plus, game pass, icloud, suscripcion |
| REGALOS_CELEBRACIONES | regalo, cumpleanos, aniversario, matrimonio, celebracion, detalle |
| OCIO_RECREACION | cine, teatro, concierto, gimnasio, gym, deporte, cancha, viaje, hotel, vacaciones, juegos, panorama, entrada |
| DEUDAS | cuota, credito, tarjeta, prestamo, deuda, repactacion |
| AHORRO (excluida en GASTO) | ahorro, fondo de emergencia, fondo de reserva |
| SUELDO (solo INGRESO) | sueldo, salario, honorarios |
| OTROS (solo INGRESO) | otros ingresos |

Nota: "comida" aparece en dos categorías con pesos distintos a propósito: es ambigua y la respuesta debe decirlo.

## 6. Diseño de código

- `service/categoria/NormalizadorTexto` (puro): normalización y tokens.
- `service/categoria/DistanciaEdicion` (puro): Damerau-Levenshtein con tope.
- `service/categoria/DiccionarioCategorias` (carga y valida el archivo).
- `service/categoria/SugeridorCategoria` (puro respecto de la base de datos: recibe el diccionario, el catálogo y la fuente de historial; sin Spring en la lógica).
- Controlador delgado en `categorias`. Sin `try/catch` ni `ResponseEntity<?>`.

## 7. Casos de prueba

Los casos G01 a G50 son una tabla en `src/test/resources/categorias/sugerencias-golden.csv` (texto, contexto, códigos esperados en orden, ambigua). La suite lee el archivo y verifica cada fila; agregar un caso es agregar una fila.

| ID | Texto | Contexto | Esperado (código y ambigua) |
|---|---|---|---|
| G01 | supermercado | GASTO | ALIMENTACION_BASE |
| G02 | SUPERMERCADO | GASTO | ALIMENTACION_BASE (igual que G01) |
| G03 | "  super   mercado " | GASTO | ALIMENTACION_BASE |
| G04 | Jumbo | GASTO | ALIMENTACION_BASE |
| G05 | Líder | GASTO | ALIMENTACION_BASE |
| G06 | feria | GASTO | ALIMENTACION_BASE |
| G07 | supermercado tu parte | GASTO | ALIMENTACION_BASE |
| G08 | superm | GASTO | ALIMENTACION_BASE, motivo PREFIJO, confianza 0,75 |
| G09 | supermecado | GASTO | ALIMENTACION_BASE, motivo APROXIMADA, confianza 0,65 |
| G10 | comida | GASTO | ALIMENTACION_BASE y RESTAURANTES_SALIDAS, ambigua |
| G11 | delivery sushi | GASTO | RESTAURANTES_SALIDAS |
| G12 | café | GASTO | RESTAURANTES_SALIDAS |
| G13 | pedidos ya | GASTO | RESTAURANTES_SALIDAS (por la entrada "pedidos ya") |
| G14 | cena aniversario | GASTO | REGALOS_CELEBRACIONES y RESTAURANTES_SALIDAS, ambigua |
| G15 | bip | GASTO | TRANSPORTE_MOVILIDAD |
| G16 | bencina copec | GASTO | TRANSPORTE_MOVILIDAD |
| G17 | uber | GASTO | TRANSPORTE_MOVILIDAD |
| G18 | arriendo | GASTO | VIVIENDA_SUMINISTROS |
| G19 | dividendo | GASTO | VIVIENDA_SUMINISTROS |
| G20 | gastos comunes | GASTO | VIVIENDA_SUMINISTROS |
| G21 | luz | GASTO | VIVIENDA_SUMINISTROS |
| G22 | internet | GASTO | VIVIENDA_SUMINISTROS |
| G23 | cuenta entel | GASTO | VIVIENDA_SUMINISTROS |
| G24 | cuota casa | GASTO | DEUDAS y VIVIENDA_SUMINISTROS, ambigua |
| G25 | tarjeta mach | GASTO | DEUDAS |
| G26 | cuota crédito | GASTO | DEUDAS |
| G27 | netflix | GASTO | SUSCRIPCIONES_DIGITAL |
| G28 | netflx | GASTO | SUSCRIPCIONES_DIGITAL, APROXIMADA |
| G29 | ps plus | GASTO | SUSCRIPCIONES_DIGITAL |
| G30 | spotify premium | GASTO | SUSCRIPCIONES_DIGITAL |
| G31 | farmacia | GASTO | SALUD_BIENESTAR |
| G32 | cruz verde | GASTO | SALUD_BIENESTAR |
| G33 | dentista | GASTO | SALUD_BIENESTAR |
| G34 | colegio | GASTO | EDUCACION_FORMACION |
| G35 | arancel | GASTO | EDUCACION_FORMACION |
| G36 | seguro auto | GASTO | SEGUROS_IMPUESTOS |
| G37 | contribuciones | GASTO | SEGUROS_IMPUESTOS |
| G38 | permiso de circulación | GASTO | SEGUROS_IMPUESTOS |
| G39 | veterinario | GASTO | MASCOTAS |
| G40 | alimento perro | GASTO | MASCOTAS, sin ambigüedad |
| G41 | detergente | GASTO | HOGAR_CONSUMIBLES |
| G42 | peluquería | GASTO | CUIDADO_PERSONAL_VESTUARIO |
| G43 | sofá | GASTO | EQUIPAMIENTO_TECNOLOGIA |
| G44 | regalo mamá | GASTO | REGALOS_CELEBRACIONES |
| G45 | cine | GASTO | OCIO_RECREACION |
| G46 | gimnasio | GASTO | OCIO_RECREACION |
| G47 | sueldo | INGRESO | SUELDO |
| G48 | sueldo | GASTO | vacío (SUELDO excluida) |
| G49 | ahorro | GASTO | vacío (AHORRO excluida) |
| G50 | uber eats | GASTO | RESTAURANTES_SALIDAS primero (frase, 1,00) y TRANSPORTE_MOVILIDAD segundo (0,90); `ambigua` falsa porque la diferencia es exactamente 0,10 |

Casos adicionales (unitarios, no en el CSV):

| ID | Caso | Esperado |
|---|---|---|
| U01 | Texto de 2 caracteres | 200, lista vacía |
| U02 | Texto "xyzqwerty" | 200, lista vacía |
| U03 | Texto "gasto personal libre" | 200, lista vacía |
| U04 | Texto de 81 caracteres | 400 PARAMETRO_INVALIDO |
| U05 | Sin parámetro `texto` | 400 PARAMETRO_INVALIDO |
| U06 | `contexto=XYZ` | 400 PARAMETRO_INVALIDO |
| U07 | Orden de tokens ("pago netflix" y "netflix pago") | Mismo resultado |
| U08 | Con tildes y sin tildes ("educación" y "educacion") | Mismo resultado |
| U09 | Máximo 3 sugerencias aunque haya más candidatas | 3 elementos |
| U10 | Empate de puntajes | Orden por `codigo` alfabético, estable entre llamadas |
| U11 | Diferencia exactamente 0,10 entre primero y segundo | `ambigua` falsa |
| U12 | Diferencia 0,09 | `ambigua` verdadera |
| U13 | Dos entradas de la misma categoría coinciden | Se cuenta una vez (el máximo) |
| U14 | Código del diccionario inexistente en el catálogo | Se ignora con WARN; la carga no falla |
| U15 | Fuente de historial devuelve un código | Esa categoría sube a 0,95 con motivo HISTORIAL |
| U16 | Todos los códigos del diccionario | Pertenecen a las 18 categorías de T10a |
| U17 | Sin token | 401 problem+json |
| U18 | Log de la llamada | No contiene el texto enviado |
| U19 | Rendimiento (1.000 llamadas seguidas con el diccionario completo) | Sin consultas a la base de datos y en un tiempo razonable en la suite |

## 8. Criterios de aceptación

- [ ] Todas las filas del CSV y los casos U01 a U19 pasan.
- [ ] El diccionario fue revisado por el usuario y no contiene palabras sin caso que las justifique.
- [ ] Endpoint documentado en `openapi.json` y descrito en `CLIENTE_FLUTTER.md` (debounce, descarte de respuestas atrasadas, preselección sin asignación silenciosa).
- [ ] Suite verde con UTC y Asia/Tokyo.

## 9. Fuera de alcance

Aprendizaje automático, llamadas a servicios de IA, historial real del usuario (llega con T10c), sugerencias para los movimientos ya guardados, edición del diccionario por el usuario.
