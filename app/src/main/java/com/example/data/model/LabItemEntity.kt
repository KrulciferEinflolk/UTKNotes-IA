package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class LabItemType {
    NUCLEUS,      // Núcleo / Raíz
    MORPHOLOGY,   // Morfología (Prefijo / Sufijo)
    CATEGORY,     // Categoría léxica
    WORD,         // Palabra / Morfema compuesto generado
    PHRASE        // Frase o combinación compleja
}

@Entity(tableName = "lab_items")
data class LabItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userEmail: String = "",
    val itemType: LabItemType,
    
    // Términos principales
    val termLanguage: String = "",    // Cómo se dice en el idioma inventado
    val termSpanish: String = "",     // Cómo se dice en español
    val categoryOrType: String = "",  // Categoría léxica o Tipo ("Prefijo", "Sufijo")
    
    // Para morfología (significado en el idioma y significado en español)
    val meaningLanguage: String = "",
    val meaningSpanish: String = "",
    
    // Para palabras y frases
    val literalMeaning: String = "",
    val contextualMeaning: String = "",
    val powerLevel: Int = 1,          // Escala del 1 al 10
    val powerBadge: String = "",      // Ej: "Insignia Común", "Insignia Arcana", "Insignia Cósmica"
    val usedComponents: String = "",  // Componentes o núcleos usados separados por comas
    val grammaticalStructure: String = "", // Estructura sintáctica/morfológica
    
    val createdAt: Long = System.currentTimeMillis()
)
