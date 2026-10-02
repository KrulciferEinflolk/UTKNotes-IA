package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R

val MontserratFontFamily = FontFamily(Font(R.font.montserrat, FontWeight.Normal))
val PlayfairFontFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))
val PacificoFontFamily = FontFamily(Font(R.font.pacifico, FontWeight.Normal))
val CaveatFontFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal))
val CinzelFontFamily = FontFamily(Font(R.font.cinzel, FontWeight.Normal))
val DancingScriptFontFamily = FontFamily(Font(R.font.dancing_script, FontWeight.Normal))
val OswaldFontFamily = FontFamily(Font(R.font.oswald, FontWeight.Normal))
val LobsterFontFamily = FontFamily(Font(R.font.lobster, FontWeight.Normal))
val FiraCodeFontFamily = FontFamily(Font(R.font.fira_code, FontWeight.Normal))

data class AppFontOption(
    val id: String,
    val name: String,
    val category: String,
    val sampleText: String,
    val fontFamily: FontFamily
)

val APP_FONTS = listOf(
    AppFontOption(
        id = "Sans",
        name = "Sans (Sistema)",
        category = "Limpia y Estándar",
        sampleText = "Claridad moderna y lectura fluida",
        fontFamily = FontFamily.SansSerif
    ),
    AppFontOption(
        id = "Montserrat",
        name = "Montserrat",
        category = "Geométrica y Moderna",
        sampleText = "Diseño contemporáneo y elegante",
        fontFamily = MontserratFontFamily
    ),
    AppFontOption(
        id = "Playfair Display",
        name = "Playfair Display",
        category = "Editorial y Refinada",
        sampleText = "Estilo clásico con alto contraste",
        fontFamily = PlayfairFontFamily
    ),
    AppFontOption(
        id = "Caveat",
        name = "Caveat",
        category = "Manuscrita y Apuntes",
        sampleText = "Notas hechas a mano con calidez",
        fontFamily = CaveatFontFamily
    ),
    AppFontOption(
        id = "Pacifico",
        name = "Pacifico",
        category = "Brush Retro y Alegre",
        sampleText = "Pincelada vintage y atractiva",
        fontFamily = PacificoFontFamily
    ),
    AppFontOption(
        id = "Dancing Script",
        name = "Dancing Script",
        category = "Caligráfica y Fluida",
        sampleText = "Trazos artísticos y elegantes",
        fontFamily = DancingScriptFontFamily
    ),
    AppFontOption(
        id = "Cinzel",
        name = "Cinzel",
        category = "Romana e Imperial",
        sampleText = "Inspiración en epigrafía clásica",
        fontFamily = CinzelFontFamily
    ),
    AppFontOption(
        id = "Oswald",
        name = "Oswald",
        category = "Titular e Impacto",
        sampleText = "Condensada para portadas potentes",
        fontFamily = OswaldFontFamily
    ),
    AppFontOption(
        id = "Lobster",
        name = "Lobster",
        category = "Póster y Vintage",
        sampleText = "Cursiva llamativa con personalidad",
        fontFamily = LobsterFontFamily
    ),
    AppFontOption(
        id = "Fira Code",
        name = "Fira Code",
        category = "Programación y Monospace",
        sampleText = "Espaciado uniforme para código y listas",
        fontFamily = FiraCodeFontFamily
    ),
    AppFontOption(
        id = "Serif",
        name = "Serif (Clásica)",
        category = "Tradicional",
        sampleText = "Tipografía literaria atemporal",
        fontFamily = FontFamily.Serif
    ),
    AppFontOption(
        id = "Monospace",
        name = "Monospace (Máquina)",
        category = "Terminal clásica",
        sampleText = "Ancho fijo para precisión técnica",
        fontFamily = FontFamily.Monospace
    ),
    AppFontOption(
        id = "Cursive",
        name = "Cursive (Sistema)",
        category = "Escritura cursiva",
        sampleText = "Escritura fluida del sistema",
        fontFamily = FontFamily.Cursive
    )
)

fun getAppFontFamily(fontId: String?): FontFamily {
    if (fontId.isNullOrBlank()) return FontFamily.SansSerif
    val normalized = fontId.trim()
    val match = APP_FONTS.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
    if (match != null) return match.fontFamily

    return when (normalized.lowercase()) {
        "sans", "sans-serif", "sansserif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "montserrat" -> MontserratFontFamily
        "playfair", "playfair display" -> PlayfairFontFamily
        "caveat" -> CaveatFontFamily
        "pacifico" -> PacificoFontFamily
        "dancing script", "dancingscript" -> DancingScriptFontFamily
        "cinzel" -> CinzelFontFamily
        "oswald" -> OswaldFontFamily
        "lobster" -> LobsterFontFamily
        "fira code", "firacode" -> FiraCodeFontFamily
        else -> FontFamily.SansSerif
    }
}

fun getAppFontDisplayName(fontId: String?): String {
    if (fontId.isNullOrBlank()) return "Sans"
    val match = APP_FONTS.firstOrNull { it.id.equals(fontId.trim(), ignoreCase = true) }
    return match?.name?.split(" ")?.firstOrNull() ?: fontId
}

@Composable
fun TypographyPickerDialog(
    selectedFontId: String,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF131720),
            border = BorderStroke(1.dp, Color(0xFF2A3142))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2F80ED).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = Color(0xFF64FFDA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Paquete de Tipografías",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Elige el estilo de letra para tu contenido",
                                color = Color(0xFF9E9E9E),
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF9E9E9E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Font List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(APP_FONTS) { fontOption ->
                        val isSelected = fontOption.id.equals(selectedFontId, ignoreCase = true)
                        
                        OutlinedCard(
                            onClick = {
                                onFontSelected(fontOption.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) Color(0xFF202636) else Color(0xFF181D29)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF64FFDA) else Color(0xFF2A3142)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = fontOption.name,
                                            fontFamily = fontOption.fontFamily,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) Color(0xFF64FFDA) else Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF262D3D)
                                        ) {
                                            Text(
                                                text = fontOption.category,
                                                fontSize = 10.sp,
                                                color = Color(0xFF8AB4F8),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = fontOption.sampleText,
                                        fontFamily = fontOption.fontFamily,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFFB0BEC5)
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF64FFDA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seleccionado",
                                            tint = Color(0xFF131720),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = Color(0xFF8AB4F8), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
