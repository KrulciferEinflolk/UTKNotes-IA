package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun parseHexColorSafe(hexString: String?, defaultColor: Color = Color(0xFF907CFF)): Color {
    if (hexString.isNullOrBlank()) return defaultColor
    val trimmed = hexString.trim()
    return try {
        when {
            trimmed.startsWith("#") && (trimmed.length == 7 || trimmed.length == 9) -> {
                Color(android.graphics.Color.parseColor(trimmed))
            }
            trimmed.startsWith("#") && trimmed.length == 4 -> {
                // Short hex #RGB -> #RRGGBB
                val r = trimmed[1]
                val g = trimmed[2]
                val b = trimmed[3]
                Color(android.graphics.Color.parseColor("#$r$r$g$g$b$b"))
            }
            trimmed.length == 6 -> {
                Color(android.graphics.Color.parseColor("#$trimmed"))
            }
            trimmed.length == 8 -> {
                Color(android.graphics.Color.parseColor("#$trimmed"))
            }
            trimmed.equals("Normal", ignoreCase = true) -> Color(0xFFE2E8F0)
            trimmed.equals("Purple", ignoreCase = true) -> Color(0xFFD0BCFF)
            trimmed.equals("Blue", ignoreCase = true) -> Color(0xFF8AB4F8)
            trimmed.equals("Green", ignoreCase = true) -> Color(0xFF81C784)
            trimmed.equals("Red", ignoreCase = true) -> Color(0xFFE57373)
            trimmed.equals("Amber", ignoreCase = true) -> Color(0xFFFFB74D)
            trimmed.equals("Cyan", ignoreCase = true) -> Color(0xFF80DEEA)
            trimmed.equals("Pink", ignoreCase = true) -> Color(0xFFF48FB1)
            trimmed.equals("Slate", ignoreCase = true) -> Color(0xFF37474F)
            trimmed.equals("None", ignoreCase = true) -> Color.Transparent
            else -> defaultColor
        }
    } catch (e: Exception) {
        defaultColor
    }
}

fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

val CURATED_HEX_PALETTE = listOf(
    // Neutrals & Basics
    "#FFFFFF", "#F3F4F6", "#CBD5E1", "#94A3B8", "#64748B", "#334155", "#1E1C24", "#000000",
    // Violets & Cyans (Aether / Gemini tones)
    "#907CFF", "#D0BCFF", "#BB86FC", "#7C4DFF", "#64FFDA", "#00E5FF", "#80DEEA", "#00B0FF",
    // Blues & Teals
    "#8AB4F8", "#2F80ED", "#1D4ED8", "#0EA5E9", "#14B8A6", "#009688", "#26A69A", "#0F766E",
    // Greens & Limes
    "#81C784", "#4CAF50", "#22C55E", "#15803D", "#84CC16", "#AEEA00", "#CDDC39", "#4D7C0F",
    // Yellows & Ambers
    "#FDE047", "#FACC15", "#FFD54F", "#FFB74D", "#FB923C", "#F97316", "#FF5722", "#EA580C",
    // Reds & Pinks
    "#EF4444", "#E57373", "#DC2626", "#B91C1C", "#F43F5E", "#EC4899", "#F48FB1", "#BE185D",
    // Pastels & Aesthetic
    "#FFE4E6", "#FFEDD5", "#FEF08A", "#DCFCE7", "#E0F2FE", "#EDE9FE", "#FCE7F3", "#F1F5F9"
)

@Composable
fun HexColorPickerDialog(
    initialColorHex: String,
    title: String = "Paleta de Color HEX",
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialColor = remember(initialColorHex) { parseHexColorSafe(initialColorHex) }
    
    var rVal by remember { mutableFloatStateOf(initialColor.red * 255f) }
    var gVal by remember { mutableFloatStateOf(initialColor.green * 255f) }
    var bVal by remember { mutableFloatStateOf(initialColor.blue * 255f) }
    
    val currentColor = Color(
        red = (rVal.toInt().coerceIn(0, 255)) / 255f,
        green = (gVal.toInt().coerceIn(0, 255)) / 255f,
        blue = (bVal.toInt().coerceIn(0, 255)) / 255f,
        alpha = 1f
    )
    
    var hexInputText by remember { mutableStateOf(colorToHex(currentColor)) }

    fun updateFromRgb(r: Float, g: Float, b: Float) {
        rVal = r
        gVal = g
        bVal = b
        val c = Color((r.toInt().coerceIn(0, 255)) / 255f, (g.toInt().coerceIn(0, 255)) / 255f, (b.toInt().coerceIn(0, 255)) / 255f, 1f)
        hexInputText = colorToHex(c)
    }

    fun updateFromHex(hex: String) {
        hexInputText = hex
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        if (clean.matches(Regex("^#([A-Fa-f0-9]{6})$"))) {
            val c = parseHexColorSafe(clean)
            rVal = c.red * 255f
            gVal = c.green * 255f
            bVal = c.blue * 255f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF131720),
            border = BorderStroke(1.dp, Color(0xFF2A3142))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Header
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
                                .background(Color(0xFF907CFF).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color(0xFF907CFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ajusta con selector hex o matriz RGB",
                                color = Color(0xFF9E9E9E),
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF9E9E9E))
                    }
                }

                // Color Box Preview & Hex Input Row
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2130)),
                    border = BorderStroke(1.dp, Color(0xFF2A3142)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // The prominent Color Box (Recuadro)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentColor)
                                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Colorize,
                                contentDescription = null,
                                tint = if (currentColor.red * 0.299 + currentColor.green * 0.587 + currentColor.blue * 0.114 > 0.6) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Hex text input field
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Código Hexadecimal:",
                                color = Color(0xFF9E9E9E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = hexInputText,
                                onValueChange = { input ->
                                    val formatted = if (!input.startsWith("#") && input.isNotEmpty()) "#$input" else input
                                    updateFromHex(formatted.uppercase())
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF64FFDA),
                                    unfocusedBorderColor = Color(0xFF2A3142),
                                    focusedContainerColor = Color(0xFF131720),
                                    unfocusedContainerColor = Color(0xFF131720)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // RGB Mixing Sliders
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181D29)),
                    border = BorderStroke(1.dp, Color(0xFF2A3142)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ajuste Fino RGB",
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Red slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(20.dp))
                            Slider(
                                value = rVal,
                                onValueChange = { updateFromRgb(it, gVal, bVal) },
                                valueRange = 0f..255f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF5252),
                                    activeTrackColor = Color(0xFFFF5252)
                                )
                            )
                            Text("${rVal.toInt()}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        }

                        // Green slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("G", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(20.dp))
                            Slider(
                                value = gVal,
                                onValueChange = { updateFromRgb(rVal, it, bVal) },
                                valueRange = 0f..255f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF69F0AE),
                                    activeTrackColor = Color(0xFF69F0AE)
                                )
                            )
                            Text("${gVal.toInt()}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        }

                        // Blue slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("B", color = Color(0xFF40C4FF), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(20.dp))
                            Slider(
                                value = bVal,
                                onValueChange = { updateFromRgb(rVal, gVal, it) },
                                valueRange = 0f..255f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF40C4FF),
                                    activeTrackColor = Color(0xFF40C4FF)
                                )
                            )
                            Text("${bVal.toInt()}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        }
                    }
                }

                // Palette Swatches
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Colores Prediseñados (HEX)",
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(38.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        items(CURATED_HEX_PALETTE) { hexCode ->
                            val itemColor = parseHexColorSafe(hexCode)
                            val isSelected = colorToHex(currentColor).equals(hexCode, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(itemColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF64FFDA) else Color(0xFF334155),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        updateFromHex(hexCode)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (itemColor.red * 0.299 + itemColor.green * 0.587 + itemColor.blue * 0.114 > 0.6) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color(0xFF9E9E9E))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val finalHex = colorToHex(currentColor)
                            onColorSelected(finalHex)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F80ED)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Aplicar Color", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Reusable Color Box (Recuadro) Composable that opens the Hex Color Picker dialog.
 * Works seamlessly in text toolbar, graphic elements settings, and book cover customization.
 */
@Composable
fun HexColorBox(
    selectedColorHex: String?,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false,
    boxSize: Int = 32
) {
    var showDialog by remember { mutableStateOf(false) }
    val displayColor = remember(selectedColorHex) { parseHexColorSafe(selectedColorHex) }
    val formattedHex = remember(selectedColorHex) {
        if (!selectedColorHex.isNullOrBlank() && selectedColorHex.startsWith("#")) selectedColorHex.uppercase()
        else colorToHex(displayColor)
    }

    if (compact) {
        // Compact recuadro for toolbars
        Box(
            modifier = modifier
                .size(boxSize.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(displayColor)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable { showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Elegir color hex",
                tint = if (displayColor.red * 0.299 + displayColor.green * 0.587 + displayColor.blue * 0.114 > 0.6) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size((boxSize * 0.55).dp)
            )
        }
    } else {
        // Full row recuadro with label and hex text for settings and book covers
        Column(modifier = modifier) {
            if (label != null) {
                Text(
                    text = label,
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B2130),
                border = BorderStroke(1.dp, Color(0xFF2A3142)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(displayColor)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedHex,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Toca para abrir paleta HEX",
                            color = Color(0xFF64FFDA),
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = Color(0xFF8AB4F8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        HexColorPickerDialog(
            initialColorHex = formattedHex,
            title = label ?: "Elegir Color HEX",
            onColorSelected = { newHex ->
                onColorSelected(newHex)
            },
            onDismiss = { showDialog = false }
        )
    }
}
