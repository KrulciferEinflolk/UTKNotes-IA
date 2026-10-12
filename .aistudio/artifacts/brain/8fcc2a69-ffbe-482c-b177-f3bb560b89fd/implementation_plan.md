# Optimización de Segmentación de Párrafos y Listas en UTK Notes

Reestructuración del procesador de texto para importación con Google ML Kit y vector digital (iText), respetando la terminación fiel de párrafos ante signos de puntuación (puntos simples, tres puntos/elipsis, punto y coma) y convirtiendo cada línea perteneciente a listas en un ítem de lista independiente para abarcar la totalidad de su contenido.

## Decisiones Críticas y Preferencias del Usuario

> [!IMPORTANT]
> A partir de las aclaraciones del usuario, se confirman las siguientes reglas de procesamiento documental:

- **Segmentación de Párrafos por Puntuación Terminal**: Cuando una línea o fragmento termine con signos de cierre o separación de ideas (punto simple `.`, puntos suspensivos `...` / `…`, punto y coma `;`, dos puntos `:`, signos de exclamación `!` o interrogación `?`), se respetará el salto de línea o se generará un bloque de párrafo independiente, en lugar de forzar la unión continua.
- **Cobertura Total de Listas**: Cada renglón o línea detectada dentro de una sección de listas (viñetas con guiones, puntos, asteriscos, o listas numeradas) se convertirá en un ítem de lista independiente (`isBullet = true` o `isNumbered = true`), garantizando que no se omita ni se trunque ninguna línea o contenido.

---

### 1. Visión General y Comportamiento del Editor

```
┌────────────────────────────────────────────────────────┐
│                   Documento Original                   │
│  - Línea con punto y coma;                             │
│  - Línea con puntos suspensivos...                     │
│  - Párrafo regular terminado en punto.                 │
│  • Elemento A de la lista                              │
│    Segunda línea o continuación de la lista            │
│  • Elemento B de la lista                              │
└──────────────────────────┬─────────────────────────────┘
                           │
             Procesamiento Fiel (ML Kit / iText)
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│             Bloques Resultantes en UTK Notes           │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bloque 1: Texto terminado en punto y coma;       │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bloque 2: Texto terminado en suspensivos...      │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bloque 3: Párrafo regular con punto final.       │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bloque 4 (Viñeta): Elemento A de la lista        │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bloque 5 (Viñeta): Segunda línea de la lista     │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bloque 6 (Viñeta): Elemento B de la lista        │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

### 2. Arquitectura de Cambios Técnicos

#### A. Segmentación en `LocalMediaAnalyzer.kt` (Google ML Kit OCR)
1. **Detección de Fin de Párrafo por Signos (`flushCurrentParagraph`)**:
   - Identificar si la línea actual culmina en puntuación terminal o de pausa mayor:
     - Punto simple (`.`, `."`, `.”`)
     - Puntos suspensivos (`...`, `…`)
     - Punto y coma (`;`)
     - Dos puntos (`:`)
     - Cierres enfáticos (`!`, `?`, `»`, `)`)
   - Tras procesar dicha línea, ejecutar `flushCurrentParagraph()` de modo que la siguiente línea comience un nuevo bloque de párrafo limpio.
2. **Preservación Integral de Listas**:
   - Si la línea inicia con viñeta (`•`, `-`, `*`, `–`, `◦`, `▪`) o numeración (`1.`, `(1)`, `1)`): registrar de inmediato el ítem en la lista de bloques.
   - Si una línea subsiguiente forma parte de la lista pero no repite el símbolo (o si se produce un salto dentro del entorno de lista), convertirla en un ítem independiente manteniendo la coherencia de la lista (`isBullet` o `isNumbered`), garantizando que abarque el 100% del texto.
3. **Manejo de Guiones de Separación de Palabras**:
   - Si una palabra se divide con guion ortográfico al final de línea (ej. `interope-`), unirla de forma natural a la siguiente palabra antes de evaluar la puntuación terminal.

#### B. Segmentación en `MainActivity.kt` (`processPageElements` para PDFs con Texto Digital)
1. **Refinamiento del `isPuntoYAparte` y Cortes de Puntuación**:
   - Extender la condición de corte de párrafo para incluir explícitamente punto y coma (`;`), puntos suspensivos (`...`, `…`) y saltos de línea de texto digital.
   - En el procesamiento de líneas agrupadas en listas, emitir cada línea secundaria como ítem independiente con su respectiva sangría y bandera de lista (`isBullet` / `isNumbered`), evitando que se colapsen o se pierdan en el párrafo posterior.

---

### 3. Fases de Implementación

1. **Ajuste de `LocalMediaAnalyzer.kt`**:
   - Modificar la función `processMlKitVisionTextToBlocks` para gestionar la terminación estricta tras signos de puntuación y la generación de ítems de lista independientes para cada línea.
2. **Ajuste de `processPageElements` en `MainActivity.kt`**:
   - Alinear la lógica de división de párrafos y listas en la extracción digital de PDFs para que coincida exactamente con las reglas de ML Kit.
3. **Verificación de Compilación y Pruebas**:
   - Ejecutar `compile_applet` para garantizar que no existan errores de sintaxis ni regresiones.
