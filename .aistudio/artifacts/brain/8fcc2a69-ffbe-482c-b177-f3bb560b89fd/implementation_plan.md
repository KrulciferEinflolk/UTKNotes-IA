# Plan de Implementación: Conversión Directa de Saltos de Línea a Párrafos en PDF

El objetivo es ajustar el importador de documentos PDF para que **cada salto de línea del documento original se traduzca directamente en un párrafo o bloque nuevo e independiente** (ya sea una línea corta o larga), evitando la coalescencia excesiva previa que creaba bloques gigantes de texto.

---

## 1. Diagnóstico del Problema Actual
- En la versión previa, el extractor utilizaba un umbral vertical amplio (`deltaY >= lastLineFontSize * 2.2f`) y un acumulador de texto continuo para evitar que los párrafos se cortaran.
- Esto provocó el efecto adverso de agrupar líneas consecutivas en párrafos artificialmente gigantescos, ignorando los saltos de línea físicos del documento original.
- El usuario ha confirmado la preferencia explícita: **cada salto de línea original debe generar su propio bloque de párrafo en el editor**.

---

## 2. Cambios a Implementar

### A. Segmentación 1:1 por Salto de Línea en `processPageElements` (`MainActivity.kt`)
- En el paso 8 de `processPageElements` (acumulación de texto normal), eliminar la unión de líneas continuas (`currentParagraph.append(" ").append(rawText)`).
- Cada línea de texto extraída (`ExtractedElement.Text` resultante de `mergeChunksToLine`) se registrará directamente como su propio bloque `EditorBlock.Text`.
- Manejar la limpieza de guiones de corte de palabra al final de línea (`-` cuando una palabra se corta al cambiar de renglón si aplica, o mantener el texto original intacto según sea adecuado).

### B. Preservación de Formato y Estructuras Especiales
- Mantener intactas las detecciones prioritarias previas:
  - Títulos y encabezados desplegables (`▼`, flechas vectoriales).
  - Listas de viñetas (bullets circulares, cuadrados y jerárquicos).
  - Listas numeradas y tareas pendientes (`[ ]`, checkboxes).
  - Citas destacadas (`|`, barra de cita).
  - Bloques de código.
  - Supresión de números de página y encabezados/pies repetitivos.
- Mantener la normalización de sangría: descuento de los dos niveles de tabulación iniciales (`(indentLevel - 2).coerceAtLeast(0)`) tal como solicitó el usuario en pasos anteriores.

---

## 3. Plan de Verificación

### Pruebas de Compilación y Comportamiento
1. **Verificación de compilación:** Ejecutar `compile_applet` para asegurar cero errores de sintaxis y tipos en `MainActivity.kt`.
2. **Validación de importación de líneas:** Comprobar que:
   - Cada renglón de texto extraído se convierte en un bloque independiente.
   - No se agrupan párrafos en bloques gigantes.
   - Los elementos con sangría, listas y desplegables conservan sus propiedades.
