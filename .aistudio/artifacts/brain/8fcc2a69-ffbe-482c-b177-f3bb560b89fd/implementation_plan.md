# Reestructuración del Catálogo de Modelos IA por Niveles de RAM (2 GB, 3 GB y 4 GB) [COMPLETADO]

Reemplazo integral del catálogo de modelos de Aura por tres modelos de razonamiento profundo escalonados según la capacidad de memoria RAM del teléfono inteligente, con selección abierta por el usuario y purga automática de modelos obsoletos para liberar almacenamiento.

---

## Decisiones Críticas y Preferencias del Usuario

> [!IMPORTANT]
> Decisiones confirmadas directamente por el usuario en la fase de clarificación:
> 1. **Selección del Modelo:** No se fuerza un modelo predeterminado rígido; la app presenta la lista categorizada de las 3 opciones (2 GB, 3 GB y 4 GB de RAM) y el usuario escoge la que mejor se adapta a su dispositivo.
> 2. **Gestión de Modelos Anteriores:** Los archivos GGUF de modelos descargados de versiones anteriores que ya no pertenezcan al catálogo se eliminan automáticamente del almacenamiento interno al actualizar la lista para recuperar valiosos gigabytes de espacio.

---

## 1. Nuevo Catálogo de Modelos por Capacidad de RAM

| Categoría | Nombre del Modelo | Parámetros | Archivo GGUF | Peso (Descarga) | RAM Utilizada | Plantilla / Razonamiento |
|---|---|---|---|---|---|---|
| **2 GB RAM** | **DeepSeek-R1-Distill-Qwen-1.5B (Q4_K_M)** | 1.54B | `DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf` | ~1.1 GB | ~1.5 GB | ChatML / `<think>` |
| **3 GB RAM** | **SmallThinker-3B-Preview (Q4_K_M)** | 3.00B | `SmallThinker-3B-Preview-Q4_K_M.gguf` | ~2.0 GB | ~2.5 GB | ChatML / `<think>` |
| **4 GB RAM** | **DeepSeek-R1-Distill-Qwen-7B (Q3_K_M)** | 7.61B | `DeepSeek-R1-Distill-Qwen-7B-Q3_K_M.gguf` | ~3.6 GB | ~3.9 GB | ChatML / `<think>` |

---

## 2. Arquitectura de Selección y Limpieza Automática

```
┌────────────────────────────────────────────────────────┐
│             Ajustes de IA / Selector de Modelos        │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 🟢 2 GB RAM: DeepSeek-R1 1.5B (Q4_K_M)           │  │
│  │    Ultraligero • 1.54B • ~1.1 GB GGUF            │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ 🟡 3 GB RAM: SmallThinker 3B Preview (Q4_K_M)    │  │
│  │    Razonamiento CoT • 3.00B • ~2.0 GB GGUF       │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ 🟣 4 GB RAM: DeepSeek-R1 7B (Q3_K_M)             │  │
│  │    Máximo Razonamiento • 7.61B • ~3.6 GB GGUF    │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│    [El usuario elige según la memoria de su teléfono]  │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│     Purga Automática de Modelos Obsoletos en Disco     │
│  • Detecta archivos en Download/Models o data/local    │
│  • Si el archivo no pertenece al nuevo trío:           │
│      - qwen2.5-3b-instruct-q4_k_m.gguf      -> ELIMINAR│
│      - qwen2.5-1.5b-instruct-q4_k_m.gguf    -> ELIMINAR│
│      - llama-3.2-3b-instruct-q4_k_m.gguf    -> ELIMINAR│
│      - DeepSeek-R1-Distill-Qwen-7B-Q4_K_M   -> ELIMINAR│
│  • Libera de 2 GB a 8 GB de almacenamiento local       │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│        Motor de Inferencia Unificado (llama.cpp)       │
│  • Los 3 modelos son variantes de razonamiento ChatML  │
│  • Las etiquetas <think> se canalizan automáticamente  │
│    al componente ThinkingAccordionCard en el chat      │
└────────────────────────────────────────────────────────┘
```

---

## 3. Cambios Implementados

### A. Catálogo de Modelos (`LlmModelCatalog.kt`)
- Sustituido el catálogo anterior con la terna oficial de 2 GB, 3 GB y 4 GB de RAM.
- Agregada la lista `LEGACY_MODEL_FILENAMES` con los nombres de modelos anteriores.

### B. Purga Automática de Modelos Obsoletos (`ModelDownloadVerifier.kt`)
- Agregada la función `purgeLegacyModels()`, que se ejecuta al arrancar el verificador:
  - Detecta archivos `.gguf` o `.downloading` obsoletos y los elimina de disco de inmediato.
- Actualizada la lógica de selección inicial (`determineInitialModel()`) para resolver preferentemente sobre los modelos descargados vigentes o el tier de 3 GB por defecto.

### C. Selector de Modelos Mejorado (`AuraModelSelectorCard.kt`)
- Actualizado el encabezado con la insignia "Según tu teléfono".
- Etiquetas cromáticas distintivas para cada nivel de RAM (Verde para 2 GB, Ámbar para 3 GB, Violeta para 4 GB).
