# Plan de Implementación: Respuestas Adaptativas Inteligentes para Aura

## Objetivo
Permitir que Aura adapte la longitud y profundidad de sus respuestas de forma inteligente según la complejidad de la consulta del usuario:
- Saludos simples ("hola", "¿cómo estás?", etc.): Respuestas breves, cálidas, naturales y sin rodeos innecesarios.
- Preguntas complejas, análisis de notas, explicaciones conceptuales o solicitudes de desarrollo: Respuestas sustanciosas, enriquecidas y hasta 4 veces más extensas con fundamentación paso a paso, ejemplos y formato Markdown.

---

## Cambios Clave

### 1. Detección Inteligente de Complejidad de la Consulta (`AetherViewModel.kt`)
- Analizar el mensaje del usuario antes de ensamblar el prompt:
  - **Mensajes simples / Saludos**: Identificar saludos breves, despedidas o preguntas de una sola palabra/frase cotidiana.
  - **Consultas complejas o analíticas**: Identificar preguntas temáticas, solicitudes de análisis, resúmenes, explicaciones, resolución de problemas o notas citadas con múltiples bloques.
- Inyectar directrices de longitud calibradas de forma contextual en el System Prompt:
  - Para saludos: *"El usuario ha saludado o hecho una pregunta casual. Responde de forma cordial, cercana y concisa en 1-2 oraciones naturales sin generar muros de texto innecesarios."*
  - Para temas complejos: *"Esta consulta requiere un desarrollo profundo y exhaustivo. Tienes permiso de extender tu respuesta hasta 4 veces más de lo habitual: divide la explicación con títulos (#, ##, ###), profundiza en cada concepto, proporciona ejemplos prácticos, antecedentes, consideraciones y conclusiones sólidas."*

### 2. Calibración en `buildAgentSystemPrompt` y Parámetros de Generación
- Instruir claramente a la arquitectura de Aura sobre la regla de adaptación elástica:
  - Nunca inflar un saludo simple con texto innecesario.
  - Desarrollar con amplitud cuando se trate de análisis de temas, proyectos, materias de estudio o notas con múltiples párrafos.
- Asegurar que el buffer de salida de tokens y límites de generación admitan respuestas largas sin cortes.

### 3. Verificación de Compilación y Estabilidad
- Validar mediante `compile_applet` que los cambios en Kotlin no generen regresiones en el flujo de chat o en la persistencia local de la sesión.
