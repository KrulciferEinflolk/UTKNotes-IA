# Integración de DeepSeek-R1-Distill-Qwen-7B (Q4_K_M) y Razonamiento Desplegable en Aura [COMPLETADO]

Plan para incorporar el modelo de razonamiento profundo `DeepSeek-R1-Distill-Qwen-7B (Q4_K_M)` al catálogo de IA local de Aura, permitiendo su descarga, gestión de memoria nativa y visualización de la cadena de pensamiento (`<think> ... </think>`) mediante un bloque colapsable interactivo en la interfaz.

---

## Decisiones Críticas y Preferencias del Usuario

> [!IMPORTANT]
> Confirmadas por el usuario en la fase de clarificación:
> 1. **Modelo y Cuantización:** Añadir directamente `DeepSeek-R1-Distill-Qwen-7B` cuantizado en `Q4_K_M` (~4.7 GB) sin restricciones.
> 2. **Visualización de Razonamiento:** Mostrar las trazas de pensamiento de DeepSeek (`<think>`) en un **bloque desplegable colapsable** dentro de la burbuja de respuesta en el chat.

---

## 1. Arquitectura y Flujo de Procesamiento

```
┌────────────────────────────────────────────────────────┐
│             Catálogo de Modelos (Ajustes)              │
│  • Qwen2.5 3B (Recomendado 6 GB)                       │
│  • Qwen2.5 1.5B (Ultraligero)                          │
│  • Llama-3.2 3B (Instrucciones)                        │
│  • DeepSeek-R1 Distill Qwen 7B (Razonamiento R1) [OK]  │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│       Descarga y Verificación (Hugging Face GGUF)      │
│  • URL directa a CDN bartowski GGUF Q4_K_M             │
│  • Validación de integridad (> 4.4 GB)                 │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│          Inferencia llama.cpp con DeepSeek-R1          │
│  • Generación de tokens de pensamiento: <think> ...    │
│  • Generación de respuesta final y acciones JSON       │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│            Visualización en Chat (Compose)             │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ✨ Proceso de razonamiento (R1) (Desplegable ▼)  │  │
│  │ [Pensamiento paso a paso, análisis crítico]     │  │
│  └──────────────────────────────────────────────────┘  │
│  [Respuesta final formateada en Markdown limpio]       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Especificación del Modelo Implementado

- **Identificador:** `deepseek_r1_distill_qwen_7b`
- **Nombre comercial:** `DeepSeek-R1 Distill Qwen 7B`
- **Parámetros:** `7.61B`
- **Archivo GGUF:** `DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf`
- **Tamaño de descarga:** `~4.7 GB`
- **Memoria RAM estimada:** `~5.2 GB`
- **Etiqueta UI:** `Razonamiento R1`
- **Formato de plantilla:** ChatML (`<|im_start|>` / `<|im_end|>`)
- **Fuente de descarga:** `https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf`

---

## 3. Cambios Implementados

### A. Catálogo (`LlmModelCatalog.kt`)
- Agregado `DEEPSEEK_R1_7B` a `LlmModelCatalog` con URL oficial de bartowski, umbral mínimo de 4.4 GB y `ChatTemplateType.CHATML`.
- Incorporado a la lista `ALL_MODELS` para que aparezca en el panel de descarga de modelos de Aura.

### B. Extracción de Pensamiento (`AetherViewModel.kt`)
- Agregado el campo `val thought: String = ""` a la estructura `AuraActionResponse`.
- Actualizado `parseAuraJsonResponse`:
  - Detecta y extrae el bloque `<think> ... </think>`.
  - Si la etiqueta de cierre aún no llega (por streaming o truncamiento), extrae de forma resiliente el contenido generado.
  - Elimina los tags `<think>` del texto restante antes de procesar el JSON o Markdown para que la respuesta conversacional final quede limpia y sin sintaxis rota.

### C. Componente Visual de Razonamiento (`MainActivity.kt`)
- Creado `ThinkingAccordionCard`:
  - Cabecera estilizada con borde sutil, icono de chispa/IA, etiqueta "Proceso de razonamiento (R1)" y chevron de alternancia expandir/contraer.
  - Animación suave `expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()`.
  - Muestra el texto de pensamiento con estilo distintivo (itálica, color secundario, divisor).
  - Integrado en el contenedor de mensajes del asistente justo antes de la respuesta Markdown.
