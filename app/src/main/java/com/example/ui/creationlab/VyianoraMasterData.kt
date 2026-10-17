package com.example.ui.creationlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * ARCHIVO MAESTRO DE VYIANÓRA
 * Definiciones canónicas oficiales: Trazos base (Vyio Visual System),
 * Raíces fundamentales, Modificadores, Categorías y Reglas fonéticas.
 */

data class VyioPrimaryShape(
    val symbolChar: String,
    val name: String,
    val meaning: String,
    val description: String
)

data class CanonicalRoot(
    val root: String,
    val meaningSpanish: String,
    val category: String,
    val conceptualDomain: String
)

data class CanonicalModifier(
    val affix: String,
    val type: String, // "Prefijo" o "Sufijo"
    val meaningSpanish: String,
    val semanticImpact: String
)

object VyianoraMasterData {

    // 10 FORMAS PRIMARIAS (TRAZOS BASE DEL VYIO VISUAL SYSTEM)
    val primaryShapes = listOf(
        VyioPrimaryShape("•", "Núcleo sólido", "Esencia / Existencia", "Centro de presencia pura"),
        VyioPrimaryShape("◯", "Núcleo vacío", "Potencial / Vacío", "Espacio no manifestado"),
        VyioPrimaryShape("│", "Línea vertical", "Poder / Jerarquía", "Ascenso, autoridad y dominio"),
        VyioPrimaryShape("─", "Línea horizontal", "Estabilidad / Control", "Fundamento y equilibrio"),
        VyioPrimaryShape("╱", "Diagonal ascendente", "Acción / Avance", "Dirección de ataque o impulso"),
        VyioPrimaryShape("╲", "Diagonal descendente", "Consecuencia / Caída", "Efecto o desenlace inevitable"),
        VyioPrimaryShape("⌒", "Curva abierta", "Emoción / Flujo", "Onda vibratoria de la voluntad"),
        VyioPrimaryShape("⌢", "Curva cerrada", "Unión / Contención", "Vínculo o envoltura protectora"),
        VyioPrimaryShape("⟡", "Forma angular", "Conflicto / Ruptura", "Punto de colisión o choque"),
        VyioPrimaryShape("⊙", "Núcleo expandido", "Energía activa", "Fuerza liberada en expansión")
    )

    // 9 RAÍCES FUNDAMENTALES CANÓNICAS
    val canonicalRoots = listOf(
        CanonicalRoot("kai", "Poder / Energía interna", "Jerarquía", "Fuerza motriz y absorción"),
        CanonicalRoot("shen", "Emoción / Voluntad", "Emoción", "Sentimiento que dirige el espíritu"),
        CanonicalRoot("ryo", "Naturaleza / Batalla", "Guerra", "Fuerza primordial y combate"),
        CanonicalRoot("zai", "Superioridad / Dominio", "Jerarquía", "Mando y señorío supremo"),
        CanonicalRoot("mi", "Yo / Existencia propia", "Vínculo", "Identidad y núcleo del ser"),
        CanonicalRoot("ta", "Otro / Vínculo externo", "Vínculo", "Alteridad y relación con el exterior"),
        CanonicalRoot("no", "Vacío / Ausencia", "Naturaleza", "Espacio no ocupado y negación"),
        CanonicalRoot("ra", "Flujo / Movimiento", "Naturaleza", "Corriente vital en constante cambio"),
        CanonicalRoot("kyu", "Control / Precisión", "Guerra", "Maestría en la contención y dirección")
    )

    // 11 MODIFICADORES OFICIALES (PREFIJOS Y SUFIJOS)
    val canonicalModifiers = listOf(
        // Prefijos
        CanonicalModifier("vy-", "Prefijo", "Perfección / Superioridad absoluta", "Eleva el concepto al nivel supremo"),
        CanonicalModifier("no-", "Prefijo", "Negación / Vacío", "Anula o vacía la raíz"),
        CanonicalModifier("kai-", "Prefijo", "Absorción / Apropiación", "Incorpora la energía del concepto"),
        // Sufijos
        CanonicalModifier("-ka", "Sufijo", "Colectivo / Expansión", "Extiende la cualidad a un grupo"),
        CanonicalModifier("-shi", "Sufijo", "Futuro / Intención", "Proyecta hacia lo venidero"),
        CanonicalModifier("-ta", "Sufijo", "Pasado / Acción completada", "Marca un suceso consolidado"),
        CanonicalModifier("-en", "Sufijo", "Estado interno / Sustantivo", "Forma sustantivos de cualidad"),
        CanonicalModifier("-yo", "Sufijo", "Intensidad / Énfasis / Adjetivo", "Forma adjetivos dominantes"),
        CanonicalModifier("-ra", "Sufijo", "Plural / Multiplicidad", "Reitera la manifestación"),
        CanonicalModifier("-ne", "Sufijo", "Duda / Inestabilidad", "Introduce vibración incierta"),
        CanonicalModifier("-dor", "Sufijo", "Conflicto / Ruptura", "Expresa combate o fractura")
    )

    // CATEGORÍAS CANÓNICAS OFICIALES
    val canonicalCategories = listOf(
        "Emoción",
        "Guerra",
        "Naturaleza",
        "Jerarquía",
        "Vínculo"
    )

    /**
     * Validador fonético canónico de Vyianóra:
     * - Terminar en vocal o 'n'
     * - Máximo 3 sílabas idealmente
     * - Evitar choques duros como "kt", "tsr"
     */
    fun validatePhonetics(word: String): Pair<Boolean, String> {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return false to "La palabra está vacía."

        val endsCorrectly = clean.endsWith("a") || clean.endsWith("e") ||
                clean.endsWith("i") || clean.endsWith("o") ||
                clean.endsWith("u") || clean.endsWith("n")

        if (!endsCorrectly) {
            return false to "Debe terminar en vocal o 'n' para cumplir la fonética recomendada."
        }

        // Conteo aproximado de sílabas por vocales
        val vowels = clean.count { it in listOf('a', 'e', 'i', 'o', 'u') }
        if (vowels > 4) {
            return true to "Aviso: supera las 3 sílabas recomendadas."
        }

        return true to "Fonética fluida y válida en el nuevo idioma."
    }

    /**
     * Genera trazo vectorial correspondiente a cada una de las 10 Formas Primarias
     * dentro de un lienzo de ancho y alto dados con centro (centerX, centerY).
     */
    fun generateShapePath(symbolChar: String, center: Offset, radius: Float): List<Offset> {
        val points = mutableListOf<Offset>()
        when (symbolChar) {
            "•" -> { // Núcleo sólido: punto central expandido en radio pequeño
                for (angle in 0..360 step 30) {
                    val rad = Math.toRadians(angle.toDouble())
                    points.add(Offset((center.x + Math.cos(rad) * radius * 0.35f).toFloat(), (center.y + Math.sin(rad) * radius * 0.35f).toFloat()))
                }
            }
            "◯" -> { // Núcleo vacío: anillo circular exterior
                for (angle in 0..360 step 15) {
                    val rad = Math.toRadians(angle.toDouble())
                    points.add(Offset((center.x + Math.cos(rad) * radius * 0.8f).toFloat(), (center.y + Math.sin(rad) * radius * 0.8f).toFloat()))
                }
            }
            "│" -> { // Línea vertical: poder / jerarquía
                points.add(Offset(center.x, center.y - radius * 0.9f))
                points.add(Offset(center.x, center.y + radius * 0.9f))
            }
            "─" -> { // Línea horizontal: estabilidad / control
                points.add(Offset(center.x - radius * 0.9f, center.y))
                points.add(Offset(center.x + radius * 0.9f, center.y))
            }
            "╱" -> { // Diagonal ascendente: acción / avance
                points.add(Offset(center.x - radius * 0.7f, center.y + radius * 0.7f))
                points.add(Offset(center.x + radius * 0.7f, center.y - radius * 0.7f))
            }
            "╲" -> { // Diagonal descendente: consecuencia / caída
                points.add(Offset(center.x - radius * 0.7f, center.y - radius * 0.7f))
                points.add(Offset(center.x + radius * 0.7f, center.y + radius * 0.7f))
            }
            "⌒" -> { // Curva abierta: emoción / flujo
                for (xStep in -30..30 step 5) {
                    val normX = xStep / 30f
                    val arcY = -(1f - normX * normX) * radius * 0.5f
                    points.add(Offset(center.x + normX * radius * 0.8f, center.y + arcY))
                }
            }
            "⌢" -> { // Curva cerrada: unión / contención
                for (xStep in -30..30 step 5) {
                    val normX = xStep / 30f
                    val arcY = (1f - normX * normX) * radius * 0.5f
                    points.add(Offset(center.x + normX * radius * 0.8f, center.y + arcY))
                }
            }
            "⟡" -> { // Forma angular: conflicto / ruptura
                points.add(Offset(center.x, center.y - radius * 0.8f))
                points.add(Offset(center.x + radius * 0.6f, center.y))
                points.add(Offset(center.x, center.y + radius * 0.8f))
                points.add(Offset(center.x - radius * 0.6f, center.y))
                points.add(Offset(center.x, center.y - radius * 0.8f))
            }
            "⊙" -> { // Núcleo expandido: energía activa (círculo exterior)
                for (angle in 0..360 step 20) {
                    val rad = Math.toRadians(angle.toDouble())
                    points.add(Offset((center.x + Math.cos(rad) * radius * 0.85f).toFloat(), (center.y + Math.sin(rad) * radius * 0.85f).toFloat()))
                }
            }
            else -> {
                points.add(Offset(center.x, center.y - radius * 0.5f))
                points.add(Offset(center.x, center.y + radius * 0.5f))
            }
        }
        return points
    }
}
