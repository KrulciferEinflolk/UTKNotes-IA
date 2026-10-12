# Motor de Importación Documental con Google ML Kit

Implementación de un sistema de escaneo y extracción documental 100% nativo y offline basado en Google ML Kit Text Recognition para UTK Notes, capaz de procesar documentos y libros escaneados de cualquier extensión (sin límites de páginas) en segundo plano con indicador de progreso en tiempo real, garantizando que cada párrafo real del documento original se reconstruya como un único bloque continuo en la aplicación sin renglones fragmentados.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario:**
> - **Reconstrucción 1:1 de párrafos reales:** Cada párrafo del documento original debe convertirse en un único bloque continuo en UTK Notes, eliminando los cortes de línea forzados por el ancho de la página o los márgenes del PDF.
> - **Procesamiento de páginas ilimitadas en segundo plano:** No habrá límites arbitrarios de páginas (se elimina el tope previo de 5 páginas). Documentos de 10, 30 o 50+ páginas se procesarán secuencialmente con una barra de progreso informativa.
> - **Motor 100% Local (Google ML Kit):** Aprovechamiento directo de la biblioteca local `com.google.mlkit:text-recognition` ya incorporada en Android, sin dependencias de servidores externos ni consumo de datos.

---

## 1. Overview & Core Concept

- **Qué hace:** Procesa documentos escaneados e imágenes de páginas mediante el motor óptico local de Google ML Kit, analizando la geometría de los `TextBlock` y `Line` para reconstruir la estructura exacta del texto: párrafos cohesivos, encabezados limpios, listas y notas, conservando la lectura original de corrido.
- **Público objetivo:** Usuarios que importan lecturas, libros, artículos científicos en PDF o fotos de apuntes físicos y desean editarlos o consultarlos en UTK Notes sin tener que arreglar manualmente las líneas cortadas.
- **Valor clave:** Cero esfuerzo de limpieza posterior: el texto entra a UTK Notes exactamente con la misma separación de párrafos que tenía el original en papel o pantalla.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Selección de Documento (PDF o Múltiples Imágenes):**
   - El usuario pulsa "Importar Documento" o adjunta un archivo en UTK Notes.
   - El sistema detecta la cantidad de páginas en el archivo.
2. **Progreso No Bloqueante en Segundo Plano:**
   - Si el archivo tiene 1 a 2 páginas, se procesa casi instantáneamente.
   - Si el archivo contiene más páginas (ej. 20 páginas), aparece una elegante tarjeta flotante o barra en la parte superior/inferior:
     - *"Escaneando con Google ML Kit: Página 7 de 24 (29%)"*
     - `LinearProgressIndicator` animado con los colores neón cósmicos de la app.
     - Botón sutil de cancelar si el usuario decide detener la importación.
3. **Apertura de la Nota Reconstruida:**
   - Al finalizar, la nota se crea o abre automáticamente en el editor con:
     - Títulos en su propio bloque `EditorBlock.Text(isHeader = true)`.
     - Párrafos de texto continuo en `EditorBlock.Text(isHeader = false)` sin renglones sueltos.
     - Tareas y listas preservadas.

### Visual Identity & Theme

- **Paleta Cósmica M3:**
  - Fondo de la barra de progreso: `SurfaceContainerDark = #1B1530` con borde `0.8.dp` en `CosmicCardBorder = #2E2550`.
  - Barra de progreso activa: Gradiente `GeminiCyanAccent = #00E5FF` hacia `GeminiPurpleAccent = #7C4DFF`.
  - Tipografía: Inter / Roboto clara con escalado accesible en `sp`.

---

## 3. Key Product Decisions & Trade-Offs

### Decisión 1: Algoritmo de Detección de Fin de Párrafo Real
- **Enfoque adoptado:**
  1. **Dentro de un `TextBlock` de ML Kit:** Las líneas físicas del PDF que terminan sin signo de puntuación de cierre (`.`, `!`, `?`, `:`) y cuya siguiente línea inicia en minúscula o continúa la cláusula se unen con un solo espacio en blanco.
  2. **Manejo de guiones de corte de palabra (*hyphenation*):** Si una línea termina en guion (`-`) y una letra antes del guion (ej. `"desarro-"` + `"llo"`), se elimina el guion y se concatenan las palabras sin espacio (`"desarrollo"`).
  3. **Identificación de punto y aparte genuino:** Una línea solo inicia un nuevo bloque de párrafo si la anterior termina con puntuación final Y hay un salto de espaciado vertical mayor al interlineado común, o si la siguiente línea inicia con sangría, viñeta o formato de encabezado.
- **Por qué:** Los PDFs y OCRs comunes dividen el texto cada 70-80 caracteres por el ancho de la columna visual. Este algoritmo reconstituye la intención semántica del autor.

### Decisión 2: Procesamiento Multi-Página con Seguridad de Memoria (Anti-OOM)
- **Enfoque adoptado:**
  - Renderizado página por página mediante `PdfRenderer`.
  - Reciclaje inmediato del `Bitmap` con `bitmap.recycle()` tras cada llamada asíncrona a `recognizer.process(inputImage).awaitTask()`.
  - Liberación del descriptor de archivo `pfd` y el `PdfRenderer` en bloques `finally`.
- **Por qué:** Renderizar un PDF de 40 páginas a alta resolución sin reciclar Bitmaps consumiría más de 800 MB de memoria nativa y provocaría que Android mate la aplicación por Out-Of-Memory.

---

## 4. Technical Architecture & Data Strategy

### Diagrama de Flujo y Pipeline de ML Kit

```
┌────────────────────────────────────────────────────────┐
│           Archivo PDF o Documento Seleccionado         │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│          PdfRenderer (Bucle Página por Página)         │
│  - Renderiza página N a Bitmap (resolución optimizada) │
│  - InputImage.fromBitmap(bitmap, 0)                    │
│  - Notifica callback onProgress(actual, total)         │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│        Google ML Kit Text Recognition (On-Device)      │
│  - recognizer.process(inputImage)                      │
│  - Bitmap reciclado inmediatamente (bitmap.recycle())  │
│  - Retorna visionText con TextBlocks y Lines           │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│       Algoritmo de Reconstrucción de Párrafos Reales   │
│  1. Unificación de palabras con guion ("pala-" + "bra")│
│  2. Fusión de renglones continuos sin puntuación final │
│  3. Detección de títulos (tamaño, mayúsculas, negrita) │
│  4. Creación de bloques cohesivos de EditorBlock.Text  │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               Editor UTK Notes / SQLite                │
│       Nota guardada con párrafos idénticos al original │
└────────────────────────────────────────────────────────┘
```

### Componentes Específicos a Modificar

1. **`com.example.util.LocalMediaAnalyzer`:**
   - Crear función `extractPdfWithMlKitDetailed(context, uri, onProgress: (Int, Int) -> Unit): List<RawExtractedParagraph>`.
   - Eliminar el límite fijo de 5 páginas (`coerceAtMost(5)` $\rightarrow$ procesar todas las páginas existentes).
   - Implementar el algoritmo de fusión de palabras partidas y continuidad de párrafos sobre la estructura nativa de `visionText.textBlocks`.
2. **`com.example.ui.AetherViewModel`:**
   - Exponer estado `importProgress: StateFlow<Pair<Int, Int>?>` (página actual y total) y `isImportingDocument: StateFlow<Boolean>`.
   - Lanzar la importación en `Dispatchers.IO` dentro de una corrutina cancelable.
3. **`com.example.MainActivity`:**
   - Agregar barra visual o diálogo de progreso elegante con `LinearProgressIndicator` cuando `isImportingDocument` sea verdadero.
   - Enlazar la inserción de los bloques normalizados al editor activo de la nota.
