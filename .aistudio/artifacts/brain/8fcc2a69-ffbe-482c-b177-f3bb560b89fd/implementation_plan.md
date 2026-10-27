# Plan de Implementación: Análisis Profundo en Chat y Garantía de Respuestas Completas sin Cortes

Este plan aborda las dos causas identificadas por el usuario:
1. **Comportamiento al pedir analizar/resumir una nota:** Aura devolvía erróneamente un `replace_selection` con viñetas cortas en lugar de un análisis explicativo y exhaustivo en el chat.
2. **Corte a media oración:** Las respuestas largas se truncaban por falta de margen de contexto y ausencia de auto-continuación cuando el modelo agotaba su búfer de generación.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas con el usuario:**
> 1. **Modo de Respuesta para Análisis y Resumen:**
>    - Al pedir *"analizar la nota"*, *"resumen"*, *"revisa mi nota"* o *"qué opinas"*, Aura generará una **explicación y análisis profundo en el chat** (`action: "conversation"` / `target_scope: "chat"`), con desglose estructurado en Markdown (temas clave, fortalezas, vacíos y conclusiones).
>    - No sustituirá bloques ni forzará `replace_selection` a menos que el usuario pida explícitamente *"reemplaza este párrafo"* o *"modifica la nota"*.
> 2. **Garantía Absoluta contra Respuestas Cortadas:**
>    - **Máximo margen de tokens:** Elevar la ventana de contexto a **16,384 tokens** (nativa en Qwen2.5-1.5B).
>    - **Extensión automática en segundo plano (Auto-Continuation):** Si una respuesta se interrumpe a media oración o el JSON queda incompleto, el sistema solicitará automáticamente a Qwen en segundo plano que continúe desde el punto exacto donde se quedó, concatenando el resultado hasta cerrarlo por completo antes de mostrárselo al usuario.

---

## 1. Arquitectura de Análisis y Detección de Intención

### System Prompt Refinado
- Incorporar en el System Prompt de Aura una regla explícita para solicitudes de análisis documental y de notas:
  - Cuando el usuario solicite analizar, revisar, resumir, explicar o comentar la nota, la acción DEBE ser `"conversation"`.
  - El campo `"content"` debe ser un desarrollo analítico completo con encabezados (`##`, `###`), viñetas, explicaciones de fondo y conclusiones sólidas.
  - Añadir el ejemplo *Few-Shot* de análisis de nota (usando el caso de estudio de notas fantásticas/documentales).

### Detección de Intención en `AetherViewModel.kt`
- En `sendUserMessageWithAttachments`:
  - Detectar intenciones analíticas (`analiz`, `análisis`, `resum`, `explica`, `revisa`, `opinión`, `qué trata`).
  - Añadir una directiva de apoyo dentro de `<user_request>` para que Aura no asuma que debe reescribir la nota, sino realizar una exégesis/análisis completo en el chat.

---

## 2. Motor Anti-Cortes: Máximo Margen de Tokens + Auto-Extensión

### A. Ventana de Contexto Ampliada a 16,384 Tokens
En `LocalLlmManager.kt`:
- Actualizar `DEFAULT_CONTEXT_SIZE = 16384` (el motor llama.cpp de Qwen2.5 gestiona 16k con un costo de memoria muy bajo gracias a GQA - Grouped Query Attention).
- En `ensureModelLoaded`: Validar que si el modelo cargado actualmente tiene un `contextSize < 16384`, se recargue con la nueva ventana para que el cambio tenga efecto inmediato sin requerir reinstalación.

### B. Bucle de Extensión Automática en Segundo Plano (`generateResponseWithAutoContinuation`)
En `LocalLlmManager.kt`:
- Al recibir la generación de Qwen, verificar si la salida está incompleta:
  - Criterio 1: El JSON inició con `{` pero no cerró con `}`.
  - Criterio 2: La última línea termina abruptamente (sin signo de puntuación `.`, `!`, `?` o comilla de cierre `"`).
- Si está incompleta, el motor ejecuta una continuación interna (hasta 3 iteraciones):
  - Inyecta al modelo el rol asistente con el texto ya generado y un trigger `"continúa exactamente aquí..."`.
  - Empalma los nuevos tokens con los anteriores sin duplicaciones.
  - Repite hasta que el JSON y las oraciones queden perfectamente sellados.

---

## 3. UI y Experiencia de Usuario

- **Feedback en Tiempo Real:** Mientras el motor realiza la auto-extensión en segundo plano, la barra de estado indicará: *"Aura está completando la respuesta..."*.
- **Visualización Limpia:** El usuario recibirá el análisis completo sin que falte una sola palabra ni se muestre sintaxis rota.
- **Acciones Opcionales:** Debajo del análisis en el chat se mantendrán las opciones secundarias ("Pasar a nueva nota" o "Añadir al final") si el usuario decide guardar el análisis generado.

---

## 4. Verification Plan

1. **Prueba de Análisis de Nota:**
   - Abrir una nota con contenido extenso (por ejemplo, notas de criaturas, elfos o apuntes de estudio).
   - Escribir en Aura: *"Analiza esta nota"*.
   - Verificar que Aura responda con un análisis analítico profundo en Markdown en el chat (no `replace_selection`).
2. **Prueba de Longitud y No-Cortes:**
   - Solicitar: *"Analiza detalladamente todos los puntos de la nota y desglosa sus implicaciones históricas"*.
   - Verificar que la respuesta se genere completa, de principio a fin, con conclusiones y sin cortarse a media frase.
3. **Verificación de Compilación:**
   - Ejecutar `compile_applet` para confirmar la ausencia de errores.
