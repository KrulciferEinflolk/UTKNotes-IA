package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

fun parseHexColorSafe(hexString: String?, defaultColor: Color = Color(0xFF907CFF)): Color {
    if (hexString.isNullOrBlank()) return defaultColor
    val trimmed = hexString.trim()
    return try {
        when {
            trimmed.startsWith("#") && (trimmed.length == 7 || trimmed.length == 9) -> {
                Color(android.graphics.Color.parseColor(trimmed))
            }
            trimmed.startsWith("#") && trimmed.length == 4 -> {
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
    } catch (_: Exception) {
        defaultColor
    }
}

fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

private fun colorToHsvArray(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun hsvToColor(hue: Float, sat: Float, value: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            sat.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    )
    return Color(argb)
}

private fun isLightSwatch(color: Color): Boolean {
    return (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.6f
}

private val RAINBOW_SPECTRUM_COLORS = listOf(
    Color(0xFFFF0000),
    Color(0xFFFF8000),
    Color(0xFFFFFF00),
    Color(0xFF80FF00),
    Color(0xFF00FF00),
    Color(0xFF00FF80),
    Color(0xFF00FFFF),
    Color(0xFF0080FF),
    Color(0xFF0000FF),
    Color(0xFF8000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0080),
    Color(0xFFFF0000)
)

/**
 * Barra slider arcoíris simple (espectro Hue 0°..360°) con pulgar tipo píldora.
 * No incluye controles adicionales ni cajas complejas.
 */
@Composable
fun RainbowHueSlider(
    selectedColorHex: String?,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedColor = remember(selectedColorHex) { parseHexColorSafe(selectedColorHex) }
    val initialHsv = remember(parsedColor) { colorToHsvArray(parsedColor) }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    LaunchedEffect(selectedColorHex) {
        val updated = colorToHsvArray(parseHexColorSafe(selectedColorHex))
        // Solo sincroniza el ángulo si el color tiene saturación/tono perceptible o si no estaba ya cerca
        if (updated[1] > 0.05f && updated[2] > 0.05f) {
            hue = updated[0]
        }
    }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thumbWidthDp = 12.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }

    val updateHueFromX: (Float) -> Unit = { rawX ->
        if (trackWidthPx > 0f) {
            val fraction = (rawX / trackWidthPx).coerceIn(0f, 1f)
            val newHue = fraction * 360f
            hue = newHue
            onColorSelected(colorToHex(hsvToColor(newHue, 1f, 1f)))
        }
    }

    val thumbColor = remember(hue) { hsvToColor(hue, 1f, 1f) }
    val fraction = (hue / 360f).coerceIn(0f, 1f)
    val thumbOffsetX = ((trackWidthPx - thumbWidthPx).coerceAtLeast(0f) * fraction).roundToInt()

    Box(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateHueFromX(offset.x)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    updateHueFromX(change.position.x)
                }
            }
            .testTag("rainbow_hue_slider"),
        contentAlignment = Alignment.CenterStart
    ) {
        // Pista arcoíris continua con bordes redondeados
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(RAINBOW_SPECTRUM_COLORS))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
        )

        // Pulgar vertical tipo píldora
        Box(
            modifier = Modifier
                .offset { IntOffset(x = thumbOffsetX, y = 0) }
                .size(width = thumbWidthDp, height = 26.dp)
                .shadow(4.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(thumbColor)
                .border(2.dp, Color.White, RoundedCornerShape(6.dp))
        )
    }
}

private enum class ColorInputTab(val label: String) {
    RGB("RGB"),
    HSV("HSV"),
    HEX("HEX")
}

/**
 * Mini ventana centrada de color estilo rueda cromática HSV + barra de brillo + pestañas RGB / HSV / HEX.
 */
@Composable
fun HexColorPickerDialog(
    initialColorHex: String,
    title: String = "Selector de Color",
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val startColor = remember(initialColorHex) { parseHexColorSafe(initialColorHex) }
    val startHsv = remember(startColor) { colorToHsvArray(startColor) }

    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var sat by remember { mutableFloatStateOf(startHsv[1]) }
    var value by remember { mutableFloatStateOf(startHsv[2]) }
    var activeTab by remember { mutableStateOf(ColorInputTab.HSV) }

    val activeColor = remember(hue, sat, value) { hsvToColor(hue, sat, value) }
    val activeHex = remember(activeColor) { colorToHex(activeColor) }

    val activeArgb = activeColor.toArgb()
    val rVal = android.graphics.Color.red(activeArgb)
    val gVal = android.graphics.Color.green(activeArgb)
    val bVal = android.graphics.Color.blue(activeArgb)

    var hexDraft by remember(activeHex) { mutableStateOf(activeHex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1F232E),
            border = BorderStroke(1.dp, Color(0xFF323846)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 316.dp)
                .fillMaxWidth()
                .testTag("color_wheel_mini_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Título discreto con muestra actual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = activeHex,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(activeColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                        )
                    }
                }

                // Rueda cromática circular (Hue + Saturation) y barra vertical de Brillo (Value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HsvColorWheel(
                        hue = hue,
                        saturation = sat,
                        value = value,
                        onHueSatChanged = { newHue, newSat ->
                            hue = newHue
                            sat = newSat
                        },
                        modifier = Modifier.size(188.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    VerticalBrightnessSlider(
                        hue = hue,
                        saturation = sat,
                        value = value,
                        onValueChanged = { newV -> value = newV },
                        modifier = Modifier
                            .width(36.dp)
                            .height(188.dp)
                    )
                }

                // Selector de pestañas RGB | HSV | HEX
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF151821),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ColorInputTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) Color(0xFF2D3343) else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .testTag("color_tab_${tab.label}")
                                    .clickable { activeTab = tab }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tab.label,
                                        color = if (selected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Filas de valores editables según pestaña activa
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF262B38),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (activeTab) {
                            ColorInputTab.HSV -> {
                                CompactNumericChannelRow(
                                    label = "H:",
                                    value = hue.roundToInt(),
                                    max = 360,
                                    testTag = "hsv_input_h",
                                    onValueValid = { newH -> hue = newH.toFloat() }
                                )
                                CompactNumericChannelRow(
                                    label = "S:",
                                    value = (sat * 100f).roundToInt(),
                                    max = 100,
                                    testTag = "hsv_input_s",
                                    onValueValid = { newS -> sat = (newS / 100f).coerceIn(0f, 1f) }
                                )
                                CompactNumericChannelRow(
                                    label = "V:",
                                    value = (value * 100f).roundToInt(),
                                    max = 100,
                                    testTag = "hsv_input_v",
                                    onValueValid = { newV -> value = (newV / 100f).coerceIn(0f, 1f) }
                                )
                            }
                            ColorInputTab.RGB -> {
                                CompactNumericChannelRow(
                                    label = "R:",
                                    value = rVal,
                                    max = 255,
                                    testTag = "rgb_input_r",
                                    onValueValid = { newR ->
                                        val hsv = colorToHsvArray(Color(newR, gVal, bVal))
                                        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                    }
                                )
                                CompactNumericChannelRow(
                                    label = "G:",
                                    value = gVal,
                                    max = 255,
                                    testTag = "rgb_input_g",
                                    onValueValid = { newG ->
                                        val hsv = colorToHsvArray(Color(rVal, newG, bVal))
                                        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                    }
                                )
                                CompactNumericChannelRow(
                                    label = "B:",
                                    value = bVal,
                                    max = 255,
                                    testTag = "rgb_input_b",
                                    onValueValid = { newB ->
                                        val hsv = colorToHsvArray(Color(rVal, gVal, newB))
                                        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                    }
                                )
                            }
                            ColorInputTab.HEX -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "HEX:",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1A1E28))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        BasicTextField(
                                            value = hexDraft,
                                            onValueChange = { raw ->
                                                val sanitized = raw.uppercase()
                                                    .filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
                                                    .take(7)
                                                val formatted = if (sanitized.isNotEmpty() && !sanitized.startsWith("#")) {
                                                    "#$sanitized"
                                                } else {
                                                    sanitized
                                                }
                                                hexDraft = formatted
                                                if (formatted.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))) {
                                                    val parsed = parseHexColorSafe(formatted)
                                                    val hsv = colorToHsvArray(parsed)
                                                    hue = hsv[0]
                                                    sat = hsv[1]
                                                    value = hsv[2]
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.End
                                            ),
                                            cursorBrush = SolidColor(Color(0xFF64FFDA)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("wheel_hex_input")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Botones de acción compactos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("color_wheel_cancel_button")
                    ) {
                        Text("Cancelar", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            onColorSelected(activeHex)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F80ED)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("color_wheel_apply_button")
                    ) {
                        Text(
                            text = "Aplicar",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNumericChannelRow(
    label: String,
    value: Int,
    max: Int,
    testTag: String,
    onValueValid: (Int) -> Unit
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1E28))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    draft = digits
                    digits.toIntOrNull()?.let { parsed ->
                        onValueValid(parsed.coerceIn(0, max))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(Color(0xFF64FFDA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}

@Composable
private fun HsvColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var wheelSizePx by remember { mutableIntStateOf(0) }

    val updateFromOffset: (Offset) -> Unit = { pos ->
        val radius = wheelSizePx / 2f
        if (radius > 0f) {
            val dx = pos.x - radius
            val dy = pos.y - radius
            var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angleDeg < 0f) angleDeg += 360f
            val distFraction = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
            onHueSatChanged(angleDeg, distFraction)
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { wheelSizePx = minOf(it.width, it.height) }
            .pointerInput(Unit) {
                detectTapGestures { pos -> updateFromOffset(pos) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromOffset(change.position)
                }
            }
            .testTag("hsv_color_wheel")
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Gradiente angular arcoíris (0° = rojo a las 3 en punto, sentido horario)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFF0000),
                    Color(0xFFFFFF00),
                    Color(0xFF00FF00),
                    Color(0xFF00FFFF),
                    Color(0xFF0000FF),
                    Color(0xFFFF00FF),
                    Color(0xFFFF0000)
                ),
                center = center
            ),
            radius = radius,
            center = center
        )

        // Saturación radial (centro blanco -> borde saturado)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Oscurecimiento según el brillo V
        val darkness = (1f - value).coerceIn(0f, 1f)
        if (darkness > 0f) {
            drawCircle(
                color = Color.Black.copy(alpha = darkness),
                radius = radius,
                center = center
            )
        }

        // Borde sutil de la rueda
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Indicador circular de posición (H, S)
        val angleRad = Math.toRadians(hue.toDouble())
        val dist = saturation.coerceIn(0f, 1f) * radius
        val selectorCenter = Offset(
            x = center.x + (cos(angleRad) * dist).toFloat(),
            y = center.y + (sin(angleRad) * dist).toFloat()
        )
        val indicatorColor = hsvToColor(hue, saturation, value)
        drawCircle(
            color = indicatorColor,
            radius = 8.dp.toPx(),
            center = selectorCenter
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = 9.dp.toPx(),
            center = selectorCenter,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = selectorCenter,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun VerticalBrightnessSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thumbSizeDp = 18.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    val fullBrightColor = remember(hue, saturation) { hsvToColor(hue, saturation, 1f) }
    val thumbColor = remember(hue, saturation, value) { hsvToColor(hue, saturation, value) }

    val updateFromY: (Float) -> Unit = { rawY ->
        if (heightPx > 0f) {
            val newV = (1f - (rawY / heightPx)).coerceIn(0f, 1f)
            onValueChanged(newV)
        }
    }

    val thumbOffsetY = ((heightPx - thumbSizePx).coerceAtLeast(0f) * (1f - value.coerceIn(0f, 1f))).roundToInt()

    Box(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { pos -> updateFromY(pos.y) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    updateFromY(change.position.y)
                }
            }
            .testTag("hsv_value_bar"),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.verticalGradient(listOf(fullBrightColor, Color.Black)))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = thumbOffsetY) }
                .size(thumbSizeDp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

/**
 * Selector de color usado en portadas de libros y ajustes de elementos gráficos:
 * Muestra directamente la barra slider arcoíris simple y al final un botón circular "+"
 * que abre la mini ventana centrada con rueda HSV + RGB/HSV/HEX.
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
    var showWheelDialog by remember { mutableStateOf(false) }
    val displayColor = remember(selectedColorHex) { parseHexColorSafe(selectedColorHex) }
    val formattedHex = remember(selectedColorHex) {
        if (!selectedColorHex.isNullOrBlank() && selectedColorHex.startsWith("#")) selectedColorHex.uppercase()
        else colorToHex(displayColor)
    }

    if (compact) {
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .size(boxSize.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(displayColor)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable { showWheelDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Elegir color",
                tint = if (isLightSwatch(displayColor)) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size((boxSize * 0.55).dp)
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            if (label != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(displayColor)
                                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        )
                        Text(
                            text = formattedHex,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RainbowHueSlider(
                    selectedColorHex = formattedHex,
                    onColorSelected = onColorSelected,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Botón circular pequeño con símbolo "+" al final de la barra slider
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("open_color_wheel_plus_button")
                        .clickable { showWheelDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E2433),
                        border = BorderStroke(1.dp, Color(0xFF3A445C)),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Abrir selector de color avanzado",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWheelDialog) {
        HexColorPickerDialog(
            initialColorHex = formattedHex,
            title = label ?: "Selector de Color",
            onColorSelected = { newHex ->
                onColorSelected(newHex)
            },
            onDismiss = { showWheelDialog = false }
        )
    }
}
