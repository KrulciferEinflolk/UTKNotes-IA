# Plan de Implementación: Agrupación de Párrafos Estilo Notion en Importación de PDF

## Diagnóstico y Contexto
En la importación de PDF anterior, cada línea física del PDF se convirtió de forma estricta en un bloque independiente de texto (`EditorBlock.Text`). Dado que los documentos PDF representan los párrafos dividiendo el texto en renglones individuales con saltos de línea físicos, esto provocó que los párrafos aparecieran fragmentados en renglones diminutos e inconexos (como se observa en la primera captura). En contraste, Notion (segunda captura) concatena las líneas continuas de un mismo párrafo fluido y únicamente crea un nuevo bloque cuando existe un salto de párrafo real (espacio vertical extra entre párrafos, cambio de sangría o cambio de tipo de bloque).

## Cambios Propuestos

### 1. Acumulación y Unión de Líneas Continuas en `processPageElements`
- Restaurar y perfeccionar el acumulador `currentParagraph` con `StringBuilder`.
- Al recibir una línea de texto normal:
  - Calcular la distancia vertical relativa `deltaY = Math.abs(prevY - element.y)` respecto a la línea anterior.
  - Comprobar si existe un salto de párrafo genuino:
    1. **Espacio vertical extra (interlínea mayor / línea en blanco):** Si `deltaY > lastLineFontSize * 1.6f` (o mayor a 22 puntos en escala estándar), se considera un salto de párrafo (`flushParagraph()`).
    2. **Cambio de sangría (`indentLevel`):** Si la línea entrante tiene diferente nivel de sangría que el párrafo actual, se vacía el párrafo anterior para iniciar uno nuevo con su respectiva sangría.
    3. **Punto y aparte:** Si la línea anterior termina en puntuación de cierre (`.`, `?`, `!`, `:`) **Y** existe una separación vertical perceptible (`deltaY > lastLineFontSize * 1.35f`), se vacía el párrafo anterior. Si el espaciado vertical es el regular de lectura continua, se unen con un espacio, permitiendo que oraciones como *"mis sospechas iniciales. ¿Son tan inteligentes...?"* fluyan en el mismo bloque tal como se ve en la captura de Notion.
    4. **Unión de texto:** Si no se cumple ninguna condición de corte de párrafo, concatenar la línea al párrafo actual:
       - Si la línea anterior termina con guion de corte de palabra (`-`), unir eliminando el guion.
       - De lo contrario, unir agregando un espacio en blanco entre palabras.

### 2. Detección y Respeto de Bloques Especiales
- Asegurar que los elementos estructurales siempre ejecuten `flushParagraph()` antes de crearse:
  - Encabezados (H1, H2, H3).
  - Listas de viñetas (`•`, `-`, `*`).
  - Listas numeradas (`1.`, `2.`).
  - Citas / Admonitions (`>`).
  - Checkboxes (`[ ]`, `[x]`).
  - Bloques desplegables / Toggles (`▼`, `▶`).
  - Imágenes.

### 3. Preservación de Formatos e Indentación
- Mantener la normalización de sangría (`(b.indentLevel - 2).coerceAtLeast(0)`) para eliminar los dos tabuladores iniciales de base y respetar las sangrías personalizadas subsiguientes.
- Asegurar que `isBold`, `fontSize` y `parentToggleId` se hereden correctamente en los bloques de párrafo unificados.

## Plan de Verificación
- Ejecutar `compile_applet` para confirmar que el compilador no encuentre ningún error de sintaxis ni tipos.
- Comprobar que el flujo de procesamiento de texto no cause bucles ni cortes abruptos de palabras.
