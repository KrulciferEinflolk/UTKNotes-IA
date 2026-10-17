# Plan de Implementación: Desacoplamiento Léxico Total, Lienzo de Escritura y Optimización Rápida de Aura IA

Erradicación integral de nombres y referencias fijas (*Vyanora*, *Vyianora*, *Vyanji*, *Vyio*) en todo el laboratorio y pantallas de presentación/onboarding, adoptando **Lienzo de escritura** y dotando a la IA Aura de un motor de asistencia ultrarrápido, eufónico y preciso con autocompletado contextual y previsualización interactiva (Aplicar / Descartar).

---

## Decisiones Confirmadas del Usuario

> [!IMPORTANT]
> A través de la interacción de clarificación se han ratificado los siguientes lineamientos esenciales:

1. **Estilo de Términos de Aura**: Cortos, directos, eufónicos con una estructura estricta y con términos variados según la categoría temátiva (ej. términos marciales concisos para *Guerra*, fluidos para *Agua/Éter*, etc.), sin textos explicativos ni alucinaciones.
2. **Renombrado del Sistema Gráfico**: Sustituir definitivamente "Lienzo Vyianji / Vyianji" por **Lienzo de escritura** en toda la interfaz (botones de barra superior, diálogo de dibujo libre, pestañas de biblioteca y tarjetas de onboarding).
3. **Flujo de Asistencia de Aura en Formularios**: En lugar de sobrescribir a ciegas los campos del usuario, Aura presentará una **tarjeta previa interactiva** con los valores generados/autocompletados y dos botones claros: **Aplicar** (copia a los campos) y **Descartar** (cancela la sugerencia).
4. **Simplificación del Formulario de Morfología**: Se elimina de forma definitiva el campo *"Significado en Vyianora"*, dejando únicamente el identificador del morfema (ej. prefijo/sufijo) y su significado en español.
5. **Autocompletado Contextual Bidireccional**: Si el usuario ya completó la categoría o uno de los campos (ej. solo el término o solo el significado en español), Aura detectará qué campo falta y generará el complemento adecuado de manera inmediata.

---

## 1. Visión General y Concepto

El **Laboratorio Lingüístico** se convierte en un entorno 100% agnóstico y personalizable, donde el usuario es el único creador de su conlang (lengua construida). 

- **Cero sesgo o marcas residuales**: Eliminación completa de términos heredados (*Vyianora*, *Vyio*, *Vyianji*) en código, títulos, descripciones, marcadores de posición (*placeholders*), mensajes de retroalimentación y onboarding tutorial.
- **Asistente Aura de Alta Precisión**: Respuestas atómicas en formato JSON estricto, prompts sin ruido metafórico innecesario, baja latencia, fallbacks consistentes y coherentes basados en la categoría activa.
- **Lienzo de Escritura**: Espacio de dibujo táctil para plasmar caracteres, glifos o ideogramas del idioma en creación, con catalogación limpia en la biblioteca.

---

## 2. Experiencia de Usuario y Diseño Visual

### 2.1 Flujo de Usuario: Léxico y Núcleos / Raíces
1. El usuario selecciona o crea una categoría (ej. *"Guerra"* o *"Naturaleza"*).
2. Si los campos de término o español están vacíos, presiona **"Forjar con Aura"**:
   - Aura responde en milisegundos con un término corto, estricto y eufónico (ej. para *Guerra*: término `"Krag"`, español `"Batalla / Filo"`).
   - Se despliega una tarjeta de vista previa con acento cian:
     ```
     ┌────────────────────────────────────────────────────────┐
     │ ✨ Sugerencia de Aura (Guerra)                          │
     │ Término: Krag   •   Español: Batalla / Asedio          │
     │ [ Descartar ]                     [ Aplicar a Campos ] │
     └────────────────────────────────────────────────────────┘
     ```
3. Si el usuario ya escribió `"Valen"` en término pero dejó vacío el español, Aura autocompleta únicamente la traducción coherente en español, sin sobreescribir lo ya escrito.

### 2.2 Flujo de Usuario: Modificadores Morfológicos
- Selector de tipo: `Prefijo` o `Sufijo`.
- Se remueve por completo el campo *"Significado en Vyianora"*.
- Campos activos: **Morfema** (ej. `Tor-`, `-ash`) y **Significado en español** (ej. `Aumentativo de fuerza`).
- Asistencia de Aura genera morfemas concisos con tarjeta de confirmación previa.

### 2.3 Flujo de Usuario: Categorías y Dominios
- Campos: **Nombre del dominio** y **Contexto de la categoría**.
- Aura sugiere dominios concisos y contextualmente ricos para organizar el léxico.

### 2.4 Flujo de Usuario: Lienzo de Escritura
- Botón superior e ítem de biblioteca renombrados a **"Lienzo de escritura"**.
- Diálogo de dibujo caligráfico con grid táctil para trazar glifos del idioma.
- Guardado en la biblioteca bajo la categoría limpia `"Glifo de Escritura"`.

### 2.5 Pantalla de Presentación / Onboarding
- En `CreationLabOnboarding.kt`, el paso 3 se actualiza de *"Lienzo Vyianji"* a *"Lienzo de Escritura (Canvas)"*, con descripción enfocada en diseñar glifos, caracteres y runas para el idioma propio.

---

## 3. Decisiones Técnicas y Arquitectura

```
┌──────────────────────────────────────────────────────────────────┐
│                   CREATION LAB SCREEN                            │
│  [ Nombre Dinámico (Lápiz) ]        [ Lienzo de Escritura (Btn) ]│
├──────────────────────────────────────────────────────────────────┤
│  [1. Léxico]        [2. Generador]   [3. Frases]   [4. Biblioteca]│
│       │                                                          │
│   ┌───┴──────────────────────────────────────────┐               │
│   │ Submenú Núcleos / Morfología / Categorías    │               │
│   │ ──────────────────────────────────────────   │               │
│   │  Inputs de Texto + Selector de Categoría     │               │
│   │  [ Botón Asistencia Aura ]                   │               │
│   │         │                                    │               │
│   │         ▼                                    │               │
│   │  ┌────────────────────────────────────────┐  │               │
│   │  │ Tarjeta Preview (Aplicar / Descartar)  │  │               │
│   │  └────────────────────────────────────────┘  │               │
│   └──────────────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────────────┘
                                │
                                ▼
                     [ AetherViewModel / Gemini ]
             Prompt Estricto: JSON Corto, Eufónico, Sin Alucinaciones
             Fallback instantáneo según categoría si falla red
```

### 3.1 Estructura de Prompts de Aura
Los prompts pasan de tener un tono místico/poético denso que provocaba respuestas largas y alucinaciones, a instrucciones concisas y directas:
- **Núcleo**:
  `"Eres un generador fonotáctico. Categoría: '$category'. Palabra existente: '$currentWord'. Español existente: '$currentSpanish'. Genera una raíz corta (1-2 sílabas, eufónica, estricta) y su significado en español. Responde SOLO JSON: {\"idioma\": \"...\", \"espanol\": \"...\"}"`
- **Morfema**:
  `"Genera un $type (prefijo o sufijo) breve para este idioma y su significado directo en español. Responde SOLO JSON: {\"morfema\": \"...\", \"significado_espanol\": \"...\"}"`
- **Categoría**:
  `"Sugiere una categoría temática y una descripción breve para un diccionario conlang. Responde SOLO JSON: {\"categoria\": \"...\", \"descripcion\": \"...\"}"`

---

## 4. Plan de Archivos y Tareas de Ejecución

1. **`CreationLabOnboarding.kt`**:
   - Reemplazar referencias a *Vyianji* por *Lienzo de Escritura* y glifos/caracteres del idioma.
   - Limpiar cualquier mención a *Vyianora* en títulos y descripciones del onboarding.

2. **`CreationLabScreen.kt`**:
   - **TopBar y Diálogo**: Actualizar el botón de la cabecera a `"Lienzo"` / `"Escritura"`. Renombrar `VyianjiCanvasDialog` a `WritingCanvasDialog`.
   - **Núcleos (`NucleiSubMenu`)**:
     - Implementar estado para tarjeta de previsualización (`AuraSuggestionPreview(idioma, espanol)`).
     - Incorporar autocompletado bidireccional si alguno de los campos ya tiene texto.
     - Botones *"Aplicar"* y *"Descartar"*.
     - Actualizar títulos ("Núcleos del Idioma" en lugar de "Núcleos de Vyianora").
   - **Morfología (`MorphologySubMenu`)**:
     - Eliminar el campo `meaningLanguage` ("Significado en Vyianora").
     - Implementar tarjeta de previsualización para aplicar/descartar sugerencias.
     - Ajustar prompt de Aura para generar respuestas ultra cortas y precisas.
   - **Categorías (`CategoriesSubMenu`)**:
     - Implementar tarjeta de previsualización para aplicar/descartar sugerencias de categoría.
   - **Generador y Frases (`GeneratorMorphemesModule`, `PhrasesModule`)**:
     - Limpiar textos descriptivos y prompts de IA para que Aura sea rápida y analítica.
   - **Biblioteca (`LibraryLabModule`)**:
     - Actualizar la pestaña de `"Vyianji"` a `"Escritura"` o `"Glifos"`.
     - Actualizar etiquetas y placeholders que mencionaban "Vyianora".

3. **Verificación y Compilación**:
   - Ejecutar `compile_applet` para garantizar que la compilación sea limpia, sin errores de tipado ni referencias rotas.
