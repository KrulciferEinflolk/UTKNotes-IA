# Plan de Implementación: Unificación de Líneas y Párrafos Continuos en PDF

El objetivo es evitar que los párrafos se corten erróneamente en múltiples fragmentos durante la importación de documentos PDF, garantizando que el texto de un mismo párrafo se mantenga continuo sin importar sangrías de primera línea ni variaciones leves de espaciado.

---

## 1. Causa Raíz Identificada

1. **Ruptura por sangría de primera línea (*First-line indent*):**
   - El algoritmo detectaba un cambio de sangría entre la primera línea (que tiene sangría inicial en base a su coordenada X) y las líneas siguientes (que regresan al margen base). Al evaluar `currentParagraphIndent != indentLevel`, ejecutaba un corte automático (`flushParagraph()`).
2. **Corte por puntuación con interlineado habitual:**
   - La condición `isSentenceGapBreak` dividía el párrafo si una línea terminaba en punto y la siguiente línea tenía un salto vertical moderado (`deltaY >= 1.85f * fontSize`), cortando oraciones intermedias en textos con interlineado de 1.5x o justificado amplio.
3. **Variaciones sutiles de tamaño de fuente:**
   - Variaciones pequeñas de estilo dentro de una misma oración podían provocar cortes inesperados si se superaba el umbral de detección.

---

## 2. Cambios Propuestos

### Archivo afectado: `app/src/main/java/com/example/MainActivity.kt`

#### A. Adaptación de la regla de sangría para soportar sangría de primera línea
- No cortar el párrafo si la línea actual es la continuación del mismo bloque y la diferencia de sangría corresponde a una sangría de primera línea (*first-line indent* o indentación de retorno al margen izquierdo).
- Solo considerar un corte real por sangría si se inicia una nueva estructura explícita (como viñetas, listas numeradas, citas en bloque o encabezados).

#### B. Separación vertical basada exclusivamente en doble interlineado
- Modificar el criterio de separación entre líneas ordinarias para que **solo se separe el párrafo si el espacio vertical supera el doble del interlineado normal** (`deltaY >= lastLineFontSize * 2.2f` a `2.5f`), sin que un punto seguido intermedio fuerce la ruptura si la distancia vertical es la de una línea normal de texto.

#### C. Preservación del post-procesamiento de tabuladores
- Mantener la normalización previamente implementada que descuenta los 2 niveles de indentación base agregados por el documento, asegurando una presentación limpia en el editor de notas.

---

## 3. Plan de Verificación

### Pruebas de Compilación y Sintaxis
- Ejecutar `compile_applet` para garantizar que no existan errores de compilación ni regresiones de tipos.

### Verificación Lógica y de Casos de Uso
1. **Párrafo con sangría inicial:**
   - Verificar que una primera línea con sangría de párrafo seguida de líneas alineadas al margen se unan en un único `EditorBlock.Text`.
2. **Párrafo con punto y seguido:**
   - Verificar que oraciones que terminan en punto dentro del mismo flujo vertical continuo no se fragmenten en dos bloques.
3. **Puntos y aparte reales:**
   - Confirmar que los saltos de párrafo reales (donde el espacio vertical supera holgadamente el interlineado normal) se sigan reconociendo como párrafos separados.
4. **Encabezados y elementos especiales:**
   - Confirmar que títulos, viñetas, citas y desplegables mantengan su separación y formato respectivo.
