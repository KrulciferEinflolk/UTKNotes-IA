# Plan de Implementación: Selector y Gestor de Modelos de IA en Ajustes

## Objetivo
Implementar un catálogo interactivo en la sección de **Ajustes** que permita al usuario seleccionar, descargar, activar o eliminar distintos modelos locales de IA para Aura, adaptados a la capacidad de hardware del dispositivo (6 GB de RAM):
- **Qwen2.5-1.5B (Base ligera):** ~1.1 GB en disco, ~1.5 GB RAM (Ultra rápido y bajo consumo).
- **Qwen2.5-3B (Recomendado para 6 GB):** ~2.0 GB en disco, ~2.6 GB RAM (Gran salto en comprensión, análisis y español sin riesgo de cierre).
- **Llama-3.2-3B (Alternativa estructurada):** ~2.0 GB en disco, ~2.5 GB RAM (Excelente precisión en seguimiento de instrucciones).

---

## Componentes a Modificar / Crear

### 1. Definición del Catálogo de Modelos (`ModelCatalog.kt` / `ModelDownloadVerifier.kt`)
- Crear la estructura `LlmModelInfo`:
  - `id`: Identificador único (ej. `qwen_1_5b`, `qwen_3b`, `llama_3_2_3b`).
  - `displayName`: Nombre amigable (ej. *Qwen2.5 3B Instruct*).
  - `fileSizeMb`: Tamaño de descarga en MB.
  - `ramRecommended`: Estimación de RAM requerida (ej. "2.6 GB").
  - `tag`: Etiqueta distintiva (*Recomendado para 6 GB*, *Ultraligero*, etc.).
  - `downloadUrl`: Enlace directo de descarga HuggingFace GGUF oficial (cuantización Q4_K_M).
  - `fileName`: Nombre del archivo GGUF en almacenamiento local.
- Persistir la selección del modelo activo en preferencias locales (`AuraPreferences`).

### 2. Gestión de Descarga y Cambio Dinámico (`LocalLlmManager.kt` & `ModelDownloadVerifier.kt`)
- Permitir verificar el estado independiente de cada modelo:
  - *No descargado*, *Descargando (con porcentaje)*, *Listo en almacenamiento*, *Cargado en memoria RAM*.
- Función para cambiar de modelo:
  1. Descarga del modelo seleccionado con barra de progreso en segundo plano.
  2. Descarga de la memoria RAM del modelo previo (`unloadModel()`).
  3. Carga en memoria nativa del nuevo modelo (`loadModel()`).
- Función para eliminar modelos no usados y recuperar espacio de almacenamiento (1 a 2 GB).

### 3. Interfaz de Usuario en Ajustes (`MainActivity.kt` / `SettingsDialog`)
- Añadir sección dedicada: **"Motor de Inteligencia Artificial (Aura)"**.
- Selector con tarjetas visuales para cada modelo:
  - Indicadores de peso, consumo de RAM y compatibilidad con 6 GB.
  - Estado actual: Botón *Descargar*, *Activar / Cargar en RAM*, o *Eliminar archivo*.
  - Progreso de descarga en vivo en la tarjeta del modelo.
- Alerta visual preventiva si se seleccionan modelos pesados.

---

## Verificación
1. Validar que la descarga y descompresión/verificación del archivo GGUF funcione correctamente.
2. Confirmar que al cambiar de modelo se libere la RAM del modelo anterior y se monte el nuevo sin fallos `Context is busy` ni fugas de memoria.
3. Verificar compilación completa con `compile_applet`.
