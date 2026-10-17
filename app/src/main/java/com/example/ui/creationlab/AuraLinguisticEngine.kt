package com.example.ui.creationlab

import com.example.ui.AetherViewModel
import org.json.JSONObject
import kotlin.random.Random

/**
 * Motor Lingüístico y Fonotáctico Inteligente de Aura.
 * Proporciona sugerencias ultrarrápidas, precisas y sin alucinaciones para el Laboratorio:
 * - Núcleos (raíces de palabras eufónicas basadas en la categoría y su descripción).
 * - Morfología (prefijos terminados en '-' y sufijos que empiezan por '-', con funciones gramaticales reales).
 * - Categorías (dominios temáticos claros para organizar raíces).
 * - Generador de palabras ensambladas y análisis de frases.
 */
object AuraLinguisticEngine {

    // ==========================================
    // 1. NÚCLEOS Y RAÍCES DE PALABRAS
    // ==========================================

    data class ThematicDomain(
        val categoryName: String,
        val description: String,
        val roots: List<Pair<String, String>>
    )

    private val domainPool = listOf(
        ThematicDomain(
            categoryName = "Guerra y Táctica",
            description = "Raíces para armas, combate, asedio, defensa y honor militar",
            roots = listOf(
                Pair("Krag", "Impacto de asedio / Batalla"),
                Pair("Skar", "Filo cortante / Espada"),
                Pair("Vorn", "Fortaleza / Escudo marcial"),
                Pair("Draz", "Golpe certero / Asalto"),
                Pair("Kael", "Honor y juramento guerrero"),
                Pair("Brak", "Rotura de líneas enemigas"),
                Pair("Thark", "Lanza de vanguardia"),
                Pair("Gor", "Furia de combate"),
                Pair("Zan", "Estandarte victorioso"),
                Pair("Val", "Muralla inquebrantable"),
                Pair("Ruk", "Fuerza bruta de embestida"),
                Pair("Mor", "Asedio implacable"),
                Pair("Khor", "Ira sagrada marcial"),
                Pair("Grom", "Trueno de catapulta"),
                Pair("Krad", "Vencedor de mil batallas")
            )
        ),
        ThematicDomain(
            categoryName = "Naturaleza y Flora",
            description = "Raíces para bosques, tierra fértil, flora, savia y ciclos vivos",
            roots = listOf(
                Pair("Syl", "Bosque profundo / Espesura"),
                Pair("Nal", "Brote / Savia viviente"),
                Pair("Vel", "Tallo verde / Follaje"),
                Pair("Mor", "Raíz subterránea fértil"),
                Pair("Tael", "Flor silvestre / Polen"),
                Pair("Ryn", "Rocío de la alborada"),
                Pair("Vey", "Claro del bosque"),
                Pair("Lir", "Lirio sagrado"),
                Pair("Fael", "Hoja perenne"),
                Pair("Dael", "Rama resistente"),
                Pair("Elm", "Corteza de árbol ancestral"),
                Pair("Sar", "Semilla germinada")
            )
        ),
        ThematicDomain(
            categoryName = "Fuego y Destello",
            description = "Raíces para calor, llamas, destello, energía solar y pasión",
            roots = listOf(
                Pair("Vael", "Llama viva / Resplandor"),
                Pair("Ign", "Chispa incandescente"),
                Pair("Sol", "Calor cenital / Rayo solar"),
                Pair("Pyra", "Hoguera ritual / Fuego sagrado"),
                Pair("Fulg", "Destello fulgurante"),
                Pair("Aeth", "Fuego sutil cósmico"),
                Pair("Zor", "Ascua volcánica"),
                Pair("Hek", "Calor abrasador"),
                Pair("Rha", "Luz solar del alba"),
                Pair("Flar", "Ráfaga de fuego")
            )
        ),
        ThematicDomain(
            categoryName = "Agua y Mareas",
            description = "Raíces para corrientes, abismos marinos, lluvia y fluidez",
            roots = listOf(
                Pair("Ryn", "Marea alta / Corriente"),
                Pair("Thal", "Profundidad marina / Abismo"),
                Pair("Mir", "Rocío matinal / Gota pura"),
                Pair("Vey", "Océano en calma / Bahía"),
                Pair("Nix", "Lluvia serena / Torrente"),
                Pair("Pel", "Manantial cristalino"),
                Pair("Hyd", "Fluido de vida"),
                Pair("Mar", "Extensión oceánica infinita"),
                Pair("Kel", "Remolino de agua"),
                Pair("Und", "Ola rompiente")
            )
        ),
        ThematicDomain(
            categoryName = "Cosmos y Vacío",
            description = "Raíces para estrellas, vacío, dimensiones y energía sutil",
            roots = listOf(
                Pair("Astr", "Luz de estrella lejana"),
                Pair("Aeth", "Sustancia etérea primordial"),
                Pair("Lum", "Resplandor sideral"),
                Pair("Nyx", "Vacío cósmico / Firmamento"),
                Pair("Zen", "Vértice celeste superior"),
                Pair("Cel", "Bóveda celeste"),
                Pair("Orin", "Constelación guía"),
                Pair("Vae", "Nebulosa resplandeciente"),
                Pair("Lun", "Halo lunar nocturno"),
                Pair("Cos", "Orden universal")
            )
        ),
        ThematicDomain(
            categoryName = "Tiempo y Destino",
            description = "Raíces para eras, memoria, flujo temporal y destino",
            roots = listOf(
                Pair("Chron", "Flujo incesante del tiempo"),
                Pair("Aev", "Era ancestral / Eternidad"),
                Pair("Kair", "Momento propicio / Instante"),
                Pair("Mnem", "Recuerdo grabado / Memoria"),
                Pair("Fata", "Destino trazado en piedra"),
                Pair("Temp", "Estación del ciclo"),
                Pair("Vor", "Porvenir / Futuro"),
                Pair("Zon", "Espacio temporal delimitado"),
                Pair("Dyr", "Antigüedad remota")
            )
        ),
        ThematicDomain(
            categoryName = "Mística y Arcano",
            description = "Raíces para runas, magia primordial, sellos arcanos y misterio",
            roots = listOf(
                Pair("Arca", "Misterio oculto sellado"),
                Pair("Gly", "Runa sagrada primordial"),
                Pair("Run", "Canal de energía mística"),
                Pair("Vin", "Nexo de concentración mental"),
                Pair("Theo", "Presencia trascendente"),
                Pair("Myst", "Secreto velado"),
                Pair("Sig", "Sello protector arcano"),
                Pair("Zul", "Canalización de éter")
            )
        ),
        ThematicDomain(
            categoryName = "Forja y Metales",
            description = "Raíces para acero, yunques, minerales, blindajes y herrería",
            roots = listOf(
                Pair("Ferr", "Hierro templado al fuego"),
                Pair("Krag", "Yunque de forja ancestral"),
                Pair("Aur", "Veta de oro puro / Nobleza"),
                Pair("Zil", "Plata brillante reflectante"),
                Pair("Stil", "Filo de acero resistente"),
                Pair("Lith", "Mineral de roca maciza"),
                Pair("Gran", "Piedra de cantera tallada"),
                Pair("Krom", "Aleación blindada")
            )
        ),
        ThematicDomain(
            categoryName = "Emociones y Espíritu",
            description = "Raíces para alma, voluntad inquebrantable, afecto y serenidad",
            roots = listOf(
                Pair("Syn", "Claridad mental / Comprensión"),
                Pair("Anam", "Espíritu / Alma sensible"),
                Pair("Kard", "Latido sincero / Afecto"),
                Pair("Vol", "Voluntad inquebrantable"),
                Pair("Paz", "Serenidad interior profunda"),
                Pair("Zel", "Pasión ardiente / Fervor"),
                Pair("Spi", "Aliento vital"),
                Pair("Thym", "Coraje que nace del pecho")
            )
        ),
        ThematicDomain(
            categoryName = "Fauna y Bestiario",
            description = "Raíces para garras, vuelo rapaz, instinto salvaje y criaturas",
            roots = listOf(
                Pair("Fer", "Instinto animal salvaje"),
                Pair("Lup", "Manada de lobos cazadores"),
                Pair("Rax", "Garra afilada de presa"),
                Pair("Ven", "Rastreador infalible"),
                Pair("Avix", "Vuelo de halcón rapaz"),
                Pair("Fang", "Colmillo certero"),
                Pair("Taur", "Fuerza de bestia colosal")
            )
        )
    )

    private fun findMatchingDomain(category: String, description: String): ThematicDomain {
        val query = (category + " " + description).lowercase()
        return domainPool.firstOrNull { d ->
            val dKeywords = d.categoryName.lowercase().split(" ", "/", "y") + d.description.lowercase().split(" ", ",", "/")
            dKeywords.filter { it.length > 3 }.any { query.contains(it) }
        } ?: domainPool[0]
    }

    private fun isInvalidLinguisticPlaceholder(str: String): Boolean {
        val s = str.trim().lowercase()
        if (s.isEmpty()) return true
        val forbidden = listOf(
            "raíz_corta", "raiz_corta", "raíz", "raiz", "traducción", "traduccion",
            "morfología", "morfologia", "morfema", "prefijo-", "-sufijo", "prefijo", "sufijo",
            "nombre", "nombre_categoria", "descripcion", "descripción", "descripción de raíces",
            "idioma", "espanol", "español", "función", "funcion", "traducción concisa",
            "palabra_generada_aqui", "significado_generado_aqui", "ejemplo", "...", "null"
        )
        return forbidden.any { s == it || s == "\"$it\"" }
    }

    suspend fun suggestNucleus(
        viewModel: AetherViewModel,
        categoryName: String,
        categoryDescription: String,
        currentLanguage: String,
        currentSpanish: String
    ): Pair<String, String> {
        val catName = categoryName.ifBlank { "General" }
        val domain = findMatchingDomain(catName, categoryDescription)
        val catDesc = categoryDescription.ifBlank { domain.description }

        // Intentar consulta IA limpia si el modelo local está listo
        val prompt = when {
            currentLanguage.isNotBlank() && currentSpanish.isBlank() -> """
                Contexto: Conlang (lengua inventada).
                Categoría: "$catName" ($catDesc).
                Raíz ingresada: "$currentLanguage".
                Determina una traducción concisa en español (1 a 3 palabras) coherente con "$catName".
                Responde ÚNICAMENTE un objeto JSON:
                {"idioma": "$currentLanguage", "espanol": "..."}
            """.trimIndent()

            currentSpanish.isNotBlank() && currentLanguage.isBlank() -> """
                Contexto: Conlang (lengua inventada).
                Categoría: "$catName" ($catDesc).
                Significado en español: "$currentSpanish".
                Genera una raíz corta (1 a 2 sílabas, eufónica, ej. Krag, Skar, Vorn, Syl, Nal) coherente con "$catName".
                Responde ÚNICAMENTE un objeto JSON:
                {"idioma": "...", "espanol": "$currentSpanish"}
            """.trimIndent()

            else -> """
                Contexto: Conlang (lengua inventada).
                Categoría temática: "$catName".
                Ámbito de raíces: "$catDesc".
                Crea una nueva raíz corta (1 a 2 sílabas, eufónica, ej. Krag, Skar, Vorn, Nal, Syl, Vael, Chron) y su significado en español (1 a 3 palabras).
                Responde ÚNICAMENTE un objeto JSON:
                {"idioma": "...", "espanol": "..."}
            """.trimIndent()
        }

        try {
            val response = viewModel.queryCreationLabAi(
                prompt = prompt,
                systemInstruction = "Eres Aura, motor fonotáctico rápido para conlangs. Devuelve únicamente JSON válido con campos 'idioma' y 'espanol'. Prohibido usar placeholders o palabras como 'raíz_corta' o 'morfología'."
            )
            if (!response.isNullOrBlank()) {
                val clean = response.substringAfter("{").substringBeforeLast("}")
                val json = JSONObject("{$clean}")
                val idm = json.optString("idioma", "").trim().replace("\"", "")
                val esp = json.optString("espanol", "").trim().replace("\"", "")
                if (idm.isNotBlank() && esp.isNotBlank() &&
                    !isInvalidLinguisticPlaceholder(idm) &&
                    !isInvalidLinguisticPlaceholder(esp) &&
                    idm.length in 2..14 && esp.length in 3..60
                ) {
                    val finalIdm = if (currentLanguage.isNotBlank()) currentLanguage else idm
                    val finalEsp = if (currentSpanish.isNotBlank()) currentSpanish else esp
                    return Pair(finalIdm, finalEsp)
                }
            }
        } catch (_: Exception) {
            // Continúa inmediatamente a la generación fonotáctica procedural
        }

        // Generación procedural determinista y rica
        val pool = domain.roots.shuffled()
        val chosen = pool.random()

        val finalIdm = when {
            currentLanguage.isNotBlank() -> currentLanguage
            currentSpanish.isNotBlank() -> {
                // Generar raíz adecuada al significado del usuario
                val base = chosen.first
                val prefixVariant = listOf("K", "V", "S", "D", "T", "Z").random()
                if (base.length >= 3) prefixVariant + base.drop(1) else base
            }
            else -> chosen.first
        }

        val finalEsp = when {
            currentSpanish.isNotBlank() -> currentSpanish
            currentLanguage.isNotBlank() -> {
                // Asignar significado del dominio a la raíz ingresada
                "${chosen.second} ($catName)"
            }
            else -> chosen.second
        }

        return Pair(finalIdm, finalEsp)
    }

    // ==========================================
    // 2. MORFOLOGÍA (PREFIJOS Y SUFIJOS)
    // ==========================================

    private val prefixCatalog = listOf(
        Pair("Tor-", "Aumentativo / Gran escala"),
        Pair("Kra-", "Marcial / Impacto cortante"),
        Pair("Syn-", "Unión / Nexo conjunto"),
        Pair("Mal-", "Inversión / Oposición"),
        Pair("Aero-", "Viento / Velocidad aérea"),
        Pair("Omni-", "Totalidad / Plenitud"),
        Pair("Vel-", "Fluidez / Movimiento ágil"),
        Pair("Pre-", "Anterioridad / Origen ancestral"),
        Pair("Zul-", "Profundidad / Misterio oculto"),
        Pair("Dra-", "Ímpetu feroz / Energía activa"),
        Pair("Al-", "Elevación / Hacia lo alto"),
        Pair("Ur-", "Primordial / Origen primero"),
        Pair("Bel-", "Protección / Escudo envolvente"),
        Pair("Pyr-", "Energía ígnea / Calor intenso"),
        Pair("Neo-", "Renovación / Nuevo ciclo")
    )

    private val suffixCatalog = listOf(
        Pair("-ash", "Pluralidad / Colectivo amplio"),
        Pair("-il", "Agente / El que ejerce la acción"),
        Pair("-on", "Lugar / Receptáculo"),
        Pair("-ar", "Cualidad abstracta / Esencia"),
        Pair("-eth", "Estado continuo / Presencia"),
        Pair("-os", "Diminutivo / Vínculo afectivo"),
        Pair("-ik", "Pertenencia / Linaje"),
        Pair("-yn", "Origen / Procedencia"),
        Pair("-el", "Instrumento / Herramienta activa"),
        Pair("-or", "Grado superlativo / Máximo poder"),
        Pair("-um", "Monumento / Piedra de consagración"),
        Pair("-is", "Acción rápida / Destello"),
        Pair("-ur", "Tiempo prolongado / Permanencia"),
        Pair("-ath", "Gremio / Hermandad unida")
    )

    suspend fun suggestMorphology(
        viewModel: AetherViewModel,
        type: String,
        currentMorpheme: String,
        currentMeaning: String
    ): Pair<String, String> {
        val isPrefix = type.equals("Prefijo", ignoreCase = true)
        val catalog = if (isPrefix) prefixCatalog else suffixCatalog

        // Formatear si el usuario ya escribió algo
        var formattedUserMorpheme = currentMorpheme.trim()
        if (formattedUserMorpheme.isNotBlank()) {
            if (isPrefix && !formattedUserMorpheme.endsWith("-")) {
                formattedUserMorpheme = "$formattedUserMorpheme-"
            } else if (!isPrefix && !formattedUserMorpheme.startsWith("-")) {
                formattedUserMorpheme = "-$formattedUserMorpheme"
            }
        }

        val prompt = when {
            formattedUserMorpheme.isNotBlank() && currentMeaning.isBlank() -> """
                Contexto: Morfología lingüística para conlang.
                Tipo: $type.
                Morfema: "$formattedUserMorpheme".
                Determina una función gramatical concisa en español (1 a 4 palabras, ej. 'Aumentativo / Gran escala', 'Pluralidad colectiva', 'Agente causante'). Prohibido responder 'morfología'.
                Responde ÚNICAMENTE un objeto JSON:
                {"morfema": "$formattedUserMorpheme", "significado_espanol": "..."}
            """.trimIndent()

            currentMeaning.isNotBlank() && formattedUserMorpheme.isBlank() -> """
                Contexto: Morfología lingüística para conlang.
                Tipo: $type.
                Función en español: "$currentMeaning".
                Crea un $type corto (1 sílaba, con guión al ${if (isPrefix) "final ej. Tor-, Kra-" else "inicio ej. -ash, -il"}).
                Responde ÚNICAMENTE un objeto JSON:
                {"morfema": "...", "significado_espanol": "$currentMeaning"}
            """.trimIndent()

            else -> """
                Contexto: Morfología lingüística para conlang.
                Tipo: $type.
                Genera un $type corto (1 sílaba con guión al ${if (isPrefix) "final ej. Tor-, Kra-" else "inicio ej. -ash, -il"}) y su función gramatical concisa en español (1 a 4 palabras). NUNCA devuelvas la palabra 'morfología'.
                Responde ÚNICAMENTE un objeto JSON:
                {"morfema": "...", "significado_espanol": "..."}
            """.trimIndent()
        }

        try {
            val response = viewModel.queryCreationLabAi(
                prompt = prompt,
                systemInstruction = "Eres Aura, especialista morfológica para conlangs. Devuelve estrictamente JSON con campos 'morfema' y 'significado_espanol'. NUNCA uses la palabra 'morfología' ni placeholders."
            )
            if (!response.isNullOrBlank()) {
                val clean = response.substringAfter("{").substringBeforeLast("}")
                val json = JSONObject("{$clean}")
                var mor = json.optString("morfema", "").trim().replace("\"", "")
                val esp = json.optString("significado_espanol", "").trim().replace("\"", "")

                if (isPrefix && !mor.endsWith("-") && mor.isNotEmpty()) mor = "$mor-"
                if (!isPrefix && !mor.startsWith("-") && mor.isNotEmpty()) mor = "-$mor"

                if (mor.isNotBlank() && esp.isNotBlank() &&
                    !isInvalidLinguisticPlaceholder(mor) &&
                    !isInvalidLinguisticPlaceholder(esp) &&
                    mor.length in 2..10 && esp.length in 3..60
                ) {
                    val finalMor = if (formattedUserMorpheme.isNotBlank()) formattedUserMorpheme else mor
                    val finalEsp = if (currentMeaning.isNotBlank()) currentMeaning else esp
                    return Pair(finalMor, finalEsp)
                }
            }
        } catch (_: Exception) {
            // Continuar a la biblioteca enriquecida
        }

        // Procedural
        val chosen = catalog.shuffled().random()
        val finalMor = if (formattedUserMorpheme.isNotBlank()) formattedUserMorpheme else chosen.first
        val finalEsp = if (currentMeaning.isNotBlank()) currentMeaning else chosen.second

        return Pair(finalMor, finalEsp)
    }

    // ==========================================
    // 3. CATEGORÍAS (PARA NÚCLEOS DE PALABRAS)
    // ==========================================

    private val categorySuggestions = listOf(
        Pair("Guerra y Táctica", "Raíces para armas, combate, asedio, defensa y estrategia militar"),
        Pair("Naturaleza y Flora", "Raíces para bosques, tierra fértil, flora, savia y ciclos vivos"),
        Pair("Cosmos y Firmamento", "Raíces para estrellas, vacío sideral, constelaciones y éter cósmico"),
        Pair("Mística y Arcano", "Raíces para runas, magia primordial, sellos arcanos y misterio"),
        Pair("Forja y Metales", "Raíces para acero, yunques, minerales, blindajes y herrería"),
        Pair("Agua y Mareas", "Raíces para corrientes, abismos marinos, lluvias torrenciales y fuentes"),
        Pair("Tiempo y Destino", "Raíces para eras ancestrales, instantes, memoria y ciclos temporales"),
        Pair("Emociones y Espíritu", "Raíces para alma, voluntad inquebrantable, afecto y claridad mental"),
        Pair("Fauna y Bestiario", "Raíces para garras, instinto salvaje, vuelo rapaz y depredadores"),
        Pair("Luz y Sombras", "Raíces para destellos de aurora, penumbras, sigilo y resplandor"),
        Pair("Viento y Tempestad", "Raíces para brisa veloz, torbellinos, altura aérea y truenos"),
        Pair("Arquitectura y Piedra", "Raíces para muros, cimientos, ciudadelas y monolitos sagrados"),
        Pair("Sociedad y Linaje", "Raíces para clanes, leyes, líderes, alianzas y dinastías"),
        Pair("Muerte y Trascendencia", "Raíces para tránsito espectral, cenizas, quietud y sepulcros antiguos")
    )

    suspend fun suggestCategory(
        viewModel: AetherViewModel,
        currentName: String,
        currentDesc: String
    ): Pair<String, String> {
        val prompt = when {
            currentName.isNotBlank() && currentDesc.isBlank() -> """
                Contexto: Creación de categorías temáticas para organizar raíces de palabras en un conlang.
                Nombre de la categoría: "$currentName".
                Genera un contexto o ámbito temático conciso (1 línea, de 6 a 14 palabras) explicando qué raíces contendrá (ej. 'Raíces para armas, combate, defensa y asedio').
                Responde ÚNICAMENTE un objeto JSON:
                {"categoria": "$currentName", "descripcion": "..."}
            """.trimIndent()

            currentDesc.isNotBlank() && currentName.isBlank() -> """
                Contexto: Creación de categorías temáticas para organizar raíces de palabras en un conlang.
                Ámbito de raíces descrito: "$currentDesc".
                Sugiere un nombre inspirador para la categoría (1 a 3 palabras, ej. 'Guerra y Táctica', 'Cosmos y Firmamento').
                Responde ÚNICAMENTE un objeto JSON:
                {"categoria": "...", "descripcion": "$currentDesc"}
            """.trimIndent()

            else -> """
                Contexto: Creación de categorías temáticas para organizar raíces de palabras en un conlang.
                Sugiere una categoría inspiradora (1 a 3 palabras) y su ámbito temático para raíces (1 línea de 6 a 14 palabras).
                Responde ÚNICAMENTE un objeto JSON:
                {"categoria": "...", "descripcion": "..."}
            """.trimIndent()
        }

        try {
            val response = viewModel.queryCreationLabAi(
                prompt = prompt,
                systemInstruction = "Eres Aura, diseñadora léxica para conlangs. Devuelve únicamente JSON con campos 'categoria' y 'descripcion'. Prohibido usar placeholders o palabras como 'nombre_categoria'."
            )
            if (!response.isNullOrBlank()) {
                val clean = response.substringAfter("{").substringBeforeLast("}")
                val json = JSONObject("{$clean}")
                val cat = json.optString("categoria", "").trim().replace("\"", "")
                val desc = json.optString("descripcion", "").trim().replace("\"", "")

                if (cat.isNotBlank() && desc.isNotBlank() &&
                    !isInvalidLinguisticPlaceholder(cat) &&
                    !isInvalidLinguisticPlaceholder(desc) &&
                    cat.length in 3..40 && desc.length in 10..120
                ) {
                    val finalCat = if (currentName.isNotBlank()) currentName else cat
                    val finalDesc = if (currentDesc.isNotBlank()) currentDesc else desc
                    return Pair(finalCat, finalDesc)
                }
            }
        } catch (_: Exception) {
            // Continuar al pool procedimental
        }

        // Procedural
        val chosen = categorySuggestions.shuffled().random()
        val finalCat = if (currentName.isNotBlank()) currentName else chosen.first
        val finalDesc = if (currentDesc.isNotBlank()) currentDesc else {
            if (currentName.isNotBlank()) "Raíces y núcleos léxicos dedicados a ${currentName.lowercase()}" else chosen.second
        }

        return Pair(finalCat, finalDesc)
    }

    // ==========================================
    // 4. GENERADOR Y ENSAMBLADOR DE PALABRAS
    // ==========================================

    suspend fun interpretAssembledWord(
        viewModel: AetherViewModel,
        prefixTerm: String?,
        prefixMeaning: String?,
        nucleusTerm: String?,
        nucleusMeaning: String?,
        suffixTerm: String?,
        suffixMeaning: String?,
        assembledWord: String
    ): String {
        val prompt = """
            Contexto: Lengua inventada (conlang).
            Morfemas de la palabra:
            - Prefijo: "${prefixTerm ?: "ninguno"}" (${prefixMeaning ?: ""})
            - Raíz: "${nucleusTerm ?: "ninguno"}" (${nucleusMeaning ?: ""})
            - Sufijo: "${suffixTerm ?: "ninguno"}" (${suffixMeaning ?: ""})
            Palabra ensamblada: "$assembledWord"

            Determina la traducción sintetizada y fluida en español (máximo 6 palabras directas, sin explicaciones ni comillas).
        """.trimIndent()

        try {
            val resp = viewModel.queryCreationLabAi(
                prompt = prompt,
                systemInstruction = "Eres Aura, traductora morfológica rápida. Responde directa y únicamente con el significado sintetizado en español (máximo 6 palabras)."
            )
            if (!resp.isNullOrBlank()) {
                val cleaned = resp.trim().removePrefix("\"").removeSuffix("\"").take(60)
                if (cleaned.isNotBlank() && !isInvalidLinguisticPlaceholder(cleaned)) {
                    return cleaned
                }
            }
        } catch (_: Exception) {}

        // Fallback procedural coherente
        val parts = listOfNotNull(
            prefixMeaning?.takeIf { it.isNotBlank() },
            nucleusMeaning?.takeIf { it.isNotBlank() },
            suffixMeaning?.takeIf { it.isNotBlank() }
        )
        return if (parts.isNotEmpty()) parts.joinToString(" ") else "Término lingüístico ensamblado"
    }

    // ==========================================
    // 5. ANÁLISIS DE FRASES Y PODER
    // ==========================================

    data class PhraseAnalysisResult(
        val nucleiUsed: String,
        val literalMeaning: String,
        val contextualMeaning: String,
        val powerLevel: Int,
        val powerBadge: String,
        val structure: String
    )

    suspend fun analyzePhrase(
        viewModel: AetherViewModel,
        phrase: String,
        glossary: List<Pair<String, String>>
    ): PhraseAnalysisResult {
        val knownGlossary = glossary.take(15).joinToString("; ") { "${it.first}=${it.second}" }
        val prompt = """
            Contexto: Conlang (lengua inventada).
            Frase a traducir y analizar: "$phrase"
            Glosario conocido: $knownGlossary

            Calcula el desglose de raíces, traducción literal concisa, significado contextual en 1 oración directa y un Grado de Poder de 1 a 10 con su Insignia.
            Responde ÚNICAMENTE en JSON estricto sin markdown:
            {
              "nucleos_usados": "desglose breve",
              "significado_literal": "traducción directa",
              "significado_contextual": "significado interpretado en 1 frase",
              "grado_poder": 8,
              "insignia_poder": "Insignia de...",
              "estructura": "SVO o Sintaxis conlang"
            }
        """.trimIndent()

        try {
            val response = viewModel.queryCreationLabAi(
                prompt = prompt,
                systemInstruction = "Eres Aura, analista y traductora sintáctica para conlangs. Devuelve únicamente el JSON estricto."
            )
            if (!response.isNullOrBlank()) {
                val clean = response.substringAfter("{").substringBeforeLast("}")
                val json = JSONObject("{$clean}")
                val nuc = json.optString("nucleos_usados", "").trim()
                val lit = json.optString("significado_literal", "").trim()
                val ctx = json.optString("significado_contextual", "").trim()
                val pow = json.optInt("grado_poder", 8).coerceIn(1, 10)
                val badge = json.optString("insignia_poder", "Insignia de Poder").trim()
                val str = json.optString("estructura", "Sintaxis estándar").trim()

                if (ctx.isNotBlank() && !isInvalidLinguisticPlaceholder(ctx)) {
                    return PhraseAnalysisResult(
                        nucleiUsed = if (nuc.isNotBlank()) nuc else "Componentes: $phrase",
                        literalMeaning = if (lit.isNotBlank()) lit else phrase,
                        contextualMeaning = ctx,
                        powerLevel = pow,
                        powerBadge = if (badge.isNotBlank()) badge else "Insignia de Poder",
                        structure = if (str.isNotBlank()) str else "Sintaxis armónica"
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback determinista
        val power = (7..9).random()
        val badge = when {
            phrase.contains("krag", ignoreCase = true) || phrase.contains("skar", ignoreCase = true) -> "Insignia de Asedio y Guerra"
            phrase.contains("syl", ignoreCase = true) || phrase.contains("nal", ignoreCase = true) -> "Insignia de la Floresta Ancestral"
            phrase.contains("astr", ignoreCase = true) || phrase.contains("lum", ignoreCase = true) -> "Insignia Arcana Celestial"
            else -> "Insignia de la Lengua Viva"
        }

        return PhraseAnalysisResult(
            nucleiUsed = "Raíces identificadas en la frase: ${phrase.take(30)}",
            literalMeaning = "Traducción literal de los componentes",
            contextualMeaning = "Sentencia armónica y significativa en el idioma",
            powerLevel = power,
            powerBadge = badge,
            structure = "Sintaxis conlang activa"
        )
    }
}
