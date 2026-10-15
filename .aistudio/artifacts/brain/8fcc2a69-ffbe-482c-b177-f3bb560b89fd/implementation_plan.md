# Integración del Sistema Maestro de Vyianóra (Vyio Visual & Generative System)

Adaptación integral del módulo para alinearlo con los documentos maestros del creador de Vyianóra: el **Sistema Visual de Ilustración (Vyio Visual System)**, la **Base Generativa de Palabras** y las reglas canónicas de dominancia conceptual (*"Una palabra/símbolo no describe… impone significado"*).

---

### User Review & Critical Decisions

> [!IMPORTANT]
> - **Glosario Canónico vs. Creación Libre**: Selector de modo en el Léxico y Generador que permite al usuario alternar entre el **Glosario Canónico Oficial de Vyianóra** (raíces `kai`, `shen`, `ryo`, `zai`, `mi`, `ta`, `no`, `ra`, `kyu`; prefijos `vy-`, `no-`, `kai-`; sufijos `-ka`, `-shi`, `-ta`, `-en`, `-yo`, `-ra`, `-ne`, `-dor`) y el modo de **Creación Libre Personalizada**.
> - **Lienzo Vyianji con Estampado de Trazos Primarios**: Panel selector con las **10 formas primarias canónicas** (`•`, `◯`, `│`, `─`, `╱`, `╲`, `⌒`, `⌢`, `⟡`, `⊙`) para estamparlas directamente sobre el lienzo caligráfico o dibujar trazos libres con el dedo.
> - **Validación Estricta de Aura IA**: El motor de Aura aplicará las reglas canónicas: terminar en vocal o "n", máximo 3 sílabas, evitar choques consonánticos duros, estructura de frase `[Sujeto] + [Concepto] + [Acción]`, y entrega en formato de **Diccionario Visual Vyio** (Significado literal, Significado simbólico, Construcción y Uso en contexto).

---

### 1. Overview & Core Concept

- **Qué hace**: Convierte el Laboratorio en la herramienta canónica de autoría y consulta de la lengua y simbología de Vyianóra:
  1. **Léxico Vyio**: Gestión de Núcleos, Morfología y Categorías oficiales (*Emoción*, *Guerra*, *Naturaleza*, *Jerarquía*, *Vínculo*) y personalizadas.
  2. **Generador Lingüístico**: Constructor guiado por las reglas de sufijación/prefijación (Sustantivos `raíz + en`, Verbos `raíz`, Adjetivos `raíz + yo`) con ajuste fonético estricto.
  3. **Frases Sintácticas**: Constructor bajo el orden canónico `[Sujeto] + [Concepto] + [Acción]` con análisis morfológico de Aura y cálculo del Grado de Poder (1-10) con insignia.
  4. **Lienzo Vyianji con 10 Formas Primarias**: Herramienta visual con catálogo interactivo de formas base para construir ideogramas y catalogarlos con su lectura Vyio, nivel y significado simbólico.
  5. **Biblioteca Clasificada**: Almacenamiento Room permanente para núcleos, morfemas, palabras y frases.

---

### 2. User Experience & Visual Design

#### Flujo de Usuario y Pantallas
1. **Léxico**:
   - Pestaña para alternar: **[Canónico Vyio]** | **[Creación Libre]**.
   - En Canónico: Se despliegan las 9 raíces fundamentales y los 11 afijos oficiales con sus significados puros (`kai` = poder, `shen` = emoción, `ryo` = batalla, `zai` = dominio, etc.).
   - En Libre: Formulario para añadir nuevos étimos con botón de autollenado por Aura.
2. **Generador de Palabras**:
   - Pasos guiados según la plantilla oficial:
     - [1] Categoría (`Emoción`, `Guerra`, `Naturaleza`, `Jerarquía`, `Vínculo`).
     - [2] Raíz principal y secundarias.
     - [3] Modificadores (Prefijo `vy-`, `no-`, `kai-` y Sufijos `-ka`, `-shi`, `-ta`, `-en`, `-yo`, etc.).
   - Visualización de la palabra forjada con validación fonética en vivo (alerta si no termina en vocal/n o si supera 3 sílabas).
   - Generación del **Diccionario Visual con Aura**: Significado literal, significado simbólico ("impone el concepto") y uso en contexto.
3. **Lienzo Vyianji (Vyio Visual System)**:
   - Barra superior de herramientas con las **10 Formas Primarias**:
     - `•` Esencia / Existencia
     - `◯` Potencial / Vacío
     - `│` Poder / Jerarquía
     - `─` Estabilidad / Control
     - `╱` Acción / Avance
     - `╲` Consecuencia / Caída
     - `⌒` Emoción / Flujo
     - `⌢` Unión / Contención
     - `⟡` Conflicto / Ruptura
     - `⊙` Energía activa
   - Al tocar cualquier forma, se estampa en el lienzo con posicionamiento (Arriba, Centro, Abajo, etc.), pudiendo complementarla con dibujo libre a mano alzada.
   - Botón para catalogar el símbolo en la biblioteca visual de Vyianji.
4. **Frases y Sentencias**:
   - Plantilla sintáctica: `[Sujeto: Mi / Ta] + [Concepto: ryo / shen] + [Acción: nara / zai]`.
   - Análisis con Aura devolviendo el Grado de Poder (1-10) con insignias (*Insignia Terrenal*, *Resonante*, *Arcana*, *Ancestral Éter*, *Cósmica Suprema*).
5. **Biblioteca**:
   - Pestañas independientes: Núcleos, Morfología, Palabras y Frases, con persistencia Room.

---

### 3. Key Product Decisions & Trade-Offs

- **Reglas Fonéticas y Semánticas de Aura**:
  - *Decisión*: Inyectar las reglas del archivo maestro de Vyianóra directamente en el prompt del sistema de Aura (`viewModel.sendMessage`), instruyéndole explícitamente: *"Una palabra o símbolo no describe, impone significado. Nunca produzcas explicaciones débiles o caóticas"*.
  - *Ventaja*: Resultados 100% fieles al espíritu del autor y coherentes con la mitología de Vyianóra.
- **Selector de Formas Primarias en Canvas**:
  - *Decisión*: Renderizar las 10 formas primarias vectorialmente en Compose Canvas mediante paths matemáticos precisos cuando el usuario pulsa para estampar.
  - *Ventaja*: Trazos firmes, sin temblores y con proporciones áureas perfectas según el archivo maestro.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    CreationLabScreen                        │
│                                                             │
│  [ Léxico | Generador | Frases | Biblioteca ]               │
│                     │                                       │
│                     ├── Glosario Canónico vs Libre          │
│                     ├── Plantilla Oficial de 8 Pasos        │
│                     └── Lienzo Vyianji con 10 Formas Base   │
└──────────────────────────────┬──────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
┌──────────────────────────────┐    ┌──────────────────────────────┐
│     VyianoraRulesEngine      │    │     Aura AI Linguistic       │
│  - 10 Formas Primarias       │    │  - Reglas de Dominancia      │
│  - 9 Raíces Base Canónicas   │    │  - Diccionario Visual Vyio   │
│  - 11 Afijos Oficiales       │    │  - Grado de Poder 1 a 10     │
│  - Validación fonética       │    │  - Sintaxis [Suj]+[Con]+[Acc]│
└──────────────────────────────┘    └──────────────────────────────┘
```

#### Plan de Archivos:
1. **`app/src/main/java/com/example/ui/creationlab/VyianoraMasterData.kt`**:
   - Archivo que encapsula las 9 raíces base, los 11 afijos, las 10 formas primarias del sistema visual con sus significados simbólicos, y el validador fonético.
2. **`app/src/main/java/com/example/ui/creationlab/CreationLabScreen.kt`**:
   - Integración del toggle Canónico/Libre en Léxico.
   - Barra de estampas de las 10 formas primarias en `VyianjiCanvasDialog`.
   - Generador y analizador de frases con la sintaxis canónica `[Sujeto] + [Concepto] + [Acción]` y plantilla de diccionario visual de Aura.
