# Plan de Implementación: Sistema Estructurado de Contexto y Acciones JSON para Aura

Configuración del motor de Aura con el protocolo estructurado de contexto de notas en Markdown, directivas de comportamiento, ejemplos few-shot y formato de salida JSON unificado (`action`, `target_scope`, `explanation`, `content`), junto con tarjetas de acción en la interfaz para aplicar los cambios en la nota con un solo toque.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones de diseño y arquitectura confirmadas:**
> 1. **Estructura del Contexto de Entrada:**
>    - Cada consulta a Aura inyectará las secciones delimitadas:
>      - `<full_note>`: Contenido completo de la nota activa en Markdown limpio.
>      - `<selected_text>`: Texto o bloque enfocado por el usuario (si existe).
>      - `<cursor_position>`: Indicación del punto de inserción (al final de la nota o en el bloque activo).
>      - `<attached_file>`: Texto extraído del documento o archivo PDF adjunto (si existe).
> 2. **Esquema de Salida JSON Estricto:**
>    - Aura responderá siempre con un objeto JSON:
>      ```json
>      {
>        "action": "insert_at_cursor" | "replace_selection" | "replace_full_note" | "conversation",
>        "target_scope": "cursor_position" | "selected_text" | "full_note" | "chat",
>        "explanation": "Resumen claro del cambio realizado",
>        "content": "Markdown listo para ser insertado en la nota"
>      }
>      ```
> 3. **Interacción y Control en la Interfaz:**
>    - Cuando Aura proponga una modificación en la nota, la tarjeta del mensaje en el chat mostrará la explicación (`explanation`), una vista previa del contenido (`content`) y botones claros para ejecutar la acción:
>      - `[ ➕ Insertar al cursor / al final ]` (para `insert_at_cursor`).
>      - `[ 🔄 Reemplazar selección ]` (para `replace_selection`).
>      - `[ 📝 Reemplazar nota completa ]` (para `replace_full_note`).
>    - Para consultas generales sin modificación, la acción será `conversation` y se mostrará de forma natural en el chat.
> 4. **Parser Tolerante a Fallos:**
>    - Se implementará un deserializador resiliente que limpie delimitadores de código (como ````json`), espacios o posibles caracteres iniciales, garantizando que el modelo local Qwen2.5 nunca rompa la interfaz.

---

## 1. Overview & Core Concept

El objetivo es transformar a Aura en un copiloto de notas modular de alta precisión (similar a Cursor o Notion AI):
- Aura deja de emitir texto no estructurado o rodeos innecesarios.
- El modelo recibe el estado exacto de la nota y del archivo adjunto entre etiquetas XML claras.
- La app interpreta el JSON resultante y sabe con exactitud matemática qué bloque o fragmento de la nota modificar, preservando el formato original del usuario.

---

## 2. User Experience & Visual Design

### Flujo de Interacción en el Chat
```
┌─────────────────────────────────────────────────────────────┐
│ 💬 Chat de Aura con Contexto de Nota Activa                 │
│ Usuario: "Continúa la nota con 2 ideas más de marketing"    │
├─────────────────────────────────────────────────────────────┤
│ 🔮 Aura ha procesado tu solicitud:                          │
│                                                             │
│ "Se añadieron dos estrategias adicionales manteniendo       │
│ el estilo de la lista."                                     │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Vista previa del Markdown:                              │ │
│ │ - SEO y Blog: Publicar 2 artículos semanales...         │ │
│ │ - Colaboraciones: Crear alianzas con creadores...       │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [ ➕ Insertar en la nota ]             [ ✕ Solo en chat ]   │
└─────────────────────────────────────────────────────────────┘
```

- **Acción inmediata:** Al pulsar el botón de aplicar, la app inserta o reemplaza los bloques correspondientes en tiempo real en la nota abierta, actualiza el editor y guarda automáticamente en la base de datos local.
- **Feedback visual:** La tarjeta muestra un estado de confirmación verde ("✓ Aplicado a la nota").

---

## 3. Key Product Decisions & Trade-Offs

- **Compatibilidad con Preguntas Abiertas (`conversation`):**
  - *Decisión:* Incluir la acción `"conversation"` en el prompt y en el parser.
  - *Por qué:* Si el usuario saluda o pregunta algo teórico sin querer alterar la nota, Aura responde fluidamente sin forzar una inserción vacía en el documento.
- **Transición Automática Markdown -> Bloques:**
  - *Decisión:* Conectar el campo `"content"` directamente con `parseTextContentToBlocks()`.
  - *Por qué:* Permite que Aura genere Markdown estándar (títulos, viñetas, casillas, tablas, citas) y la app lo convierta de forma nativa a los bloques de edición de la app (`EditorBlock`).

---

## 4. Technical Architecture & Component Mapping

```
┌───────────────────────────────────────────────────────────────────────────┐
│                     ARQUITECTURA DE CONTEXTO Y ACCIÓN                     │
└───────────────────────────────────────────────────────────────────────────┘
                                       │
                 ┌─────────────────────┴─────────────────────┐
                 ▼                                           ▼
      [ Context Assembly ]                        [ Response & Action ]
       AetherViewModel.kt                          AetherViewModel.kt
                 │                                           │
  - Ensambla:                               - Parser resiliente JSON de Aura
    <full_note>                              - Extracción de action, scope,
    <selected_text>                            explanation y content
    <cursor_position>                        - Métodos:
    <attached_file>                            applyAuraActionToNote(...)
  - Inyecta System Prompt estructurado       - Inserción en bloques Room
                 │                                           │
                 └─────────────────────┬─────────────────────┘
                                       │
                                       ▼
                         MainActivity.kt / Chat UI
  - Renderizado de AuraActionCard en cada mensaje del asistente
  - Botones interactivos según `action`
  - Vista previa de Markdown y feedback de aplicación
```

### Detalle de Modificaciones:

1. **`app/src/main/java/com/example/ui/AetherViewModel.kt`**:
   - Crear el modelo de datos `AuraActionResponse(action, targetScope, explanation, content)`.
   - Modificar `buildAgentSystemPrompt` con las directivas exactas proporcionadas por el usuario, estructura de contexto, reglas de comportamiento y ejemplos few-shot.
   - En `sendUserMessageWithAttachments`:
     - Empaquetar la nota activa en `<full_note>`.
     - Empaquetar el texto enfocado en `<selected_text>`.
     - Empaquetar la posición en `<cursor_position>`.
     - Empaquetar el PDF o archivo en `<attached_file>`.
   - Implementar `parseAuraJsonResponse(rawText: String): AuraActionResponse` con regex y fallback seguro.
   - Implementar `applyAuraAction(action: AuraActionResponse)` para modificar o insertar en la nota según `target_scope`.

2. **`app/src/main/java/com/example/MainActivity.kt`**:
   - En el componente de mensaje del Chatbot, detectar si el mensaje contiene una propuesta de acción de Aura (`AuraActionResponse`).
   - Mostrar la tarjeta con la explicación, vista previa del contenido y los botones interactivos:
     - `insert_at_cursor`: "Insertar en la nota".
     - `replace_selection`: "Reemplazar selección".
     - `replace_full_note`: "Reemplazar toda la nota".
   - Conectar los clics con `viewModel.applyAuraAction()`.

---

## 5. Verification Plan

1. **Prueba de Continuación (`insert_at_cursor`):**
   - Enviar a Aura: *"Continúa la nota con 2 ideas más"*.
   - Comprobar que responda con JSON válido conteniendo `action: "insert_at_cursor"`.
   - Verificar que en el chat aparezca la tarjeta con la explicación y el botón para insertar en la nota.
   - Pulsar el botón y verificar que los nuevos bloques aparezcan al final de la nota en el editor.
2. **Prueba de Corrección de Selección (`replace_selection`):**
   - Seleccionar un párrafo con errores ortográficos y pedir *"Corrige la gramática de esta selección"*.
   - Comprobar que responda con `action: "replace_selection"` y que al pulsar el botón se actualice dicho párrafo.
3. **Prueba de Integración de Documento (`attached_file`):**
   - Adjuntar un documento y pedir *"Pasa la información a una tabla"*.
   - Verificar que Aura genere una tabla en Markdown y permita insertarla directamente.
4. **Prueba de Pregunta General (`conversation`):**
   - Preguntar algo casual como *"Hola Aura, ¿cómo estás?"*.
   - Comprobar que responda como conversación en el chat sin botones erróneos de modificación.
5. **Compilación y estabilidad:**
   - Ejecutar `compile_applet` para certificar una compilación limpia.
