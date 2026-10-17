package com.example.ui.creationlab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class OnboardingStep(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val secondaryColor: Color,
    val tagText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationLabOnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val steps = listOf(
        OnboardingStep(
            stepNumber = 1,
            title = "Exploración de Raíces y Léxico",
            subtitle = "Arquitectura de morfemas y raíces",
            description = "Descubre y forja raíces base, prefijos y sufijos esenciales para expandir tu propia lengua construida. Mezcla morfemas para descubrir nuevos significados y resonancias al instante.",
            icon = Icons.Default.Extension,
            accentColor = Color(0xFF00CEC9),
            secondaryColor = Color(0xFF8C7CFF),
            tagText = "Paso 1 de 3 • Morfología"
        ),
        OnboardingStep(
            stepNumber = 2,
            title = "Generador Inteligente y Modificadores",
            subtitle = "Gramática y derivación con Aura",
            description = "Aplica reglas gramaticales automatizadas asistidas por la IA Aura: flexiones verbales, declinaciones, síntesis fonética y cálculo automático de significados en español.",
            icon = Icons.Default.AutoAwesome,
            accentColor = Color(0xFF8C7CFF),
            secondaryColor = Color(0xFFBA86FC),
            tagText = "Paso 2 de 3 • Motor IA"
        ),
        OnboardingStep(
            stepNumber = 3,
            title = "Lienzo de Escritura",
            subtitle = "Trazos y caligrafía de símbolos",
            description = "Plasma y diseña caracteres, glifos y símbolos con un lienzo táctil de dibujo en tiempo real. Ajusta grosores, experimenta con colores neón cósmicos y cataloga tus creaciones.",
            icon = Icons.Default.Brush,
            accentColor = Color(0xFFFFD166),
            secondaryColor = Color(0xFFFF7675),
            tagText = "Paso 3 de 3 • Glifos y Símbolos"
        )
    )

    val pagerState = rememberPagerState(pageCount = { steps.size })

    val completeAndProceed: () -> Unit = {
        CreationLabPrefs.setOnboardingSeen(context, true)
        onFinish()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("creation_lab_onboarding_scaffold"),
        containerColor = CosmicBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge indicador superior
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CosmicSurface,
                    border = BorderStroke(1.dp, CosmicBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = GeminiCyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Laboratorio de Creación",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Botón Saltar (disponible para salir rápido del tutorial)
                if (pagerState.currentPage < steps.size - 1) {
                    TextButton(
                        onClick = completeAndProceed,
                        modifier = Modifier.testTag("onboarding_skip_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) {
                        Text(
                            text = "Saltar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicador visual de páginas (PagerDotsIndicator)
                PagerDotsIndicator(
                    pagerState = pagerState,
                    activeColor = steps[pagerState.currentPage].accentColor,
                    inactiveColor = CosmicBorder
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Fila de botones de navegación dinámicos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Anterior
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("onboarding_prev_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CosmicBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CosmicSurface,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Anterior",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Anterior",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Botón Siguiente / Comenzar
                    val isLastPage = pagerState.currentPage == steps.size - 1
                    Button(
                        onClick = {
                            if (isLastPage) {
                                completeAndProceed()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(if (pagerState.currentPage > 0) 1.2f else 1f)
                            .height(50.dp)
                            .testTag(if (isLastPage) "onboarding_start_button" else "onboarding_next_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLastPage) GeminiCyanAccent else GeminiBlue
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = if (isLastPage) "Comenzar" else "Siguiente",
                            color = GeminiOnPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isLastPage) "Comenzar" else "Siguiente",
                            tint = GeminiOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("onboarding_horizontal_pager")
        ) { pageIndex ->
            val step = steps[pageIndex]

            // Animación fluida de escala y opacidad con lerp basada en la fracción de desplazamiento
            val pageOffset = (
                (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
            ).absoluteValue

            val cardScale = lerp(
                start = 0.88f,
                stop = 1f,
                fraction = 1f - pageOffset.coerceIn(0f, 1f)
            )

            val cardAlpha = lerp(
                start = 0.45f,
                stop = 1f,
                fraction = 1f - pageOffset.coerceIn(0f, 1f)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                OnboardingCard(step = step)
            }
        }
    }
}

@Composable
fun OnboardingCard(
    step: OnboardingStep,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = step.accentColor.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(28.dp),
        color = CosmicSurface,
        border = BorderStroke(1.5.dp, step.accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            step.accentColor.copy(alpha = 0.08f),
                            CosmicSurface,
                            CosmicBackground.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tag identificador de paso
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = step.accentColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, step.accentColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = step.tagText,
                    color = step.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Ilustración o gráfico visual representativo
            StepVisualIllustration(step = step)

            Spacer(modifier = Modifier.height(28.dp))

            // Título
            Text(
                text = step.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = step.subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = step.accentColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Descripción breve y clara
            Text(
                text = step.description,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
fun StepVisualIllustration(
    step: OnboardingStep,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Anillos decorativos de fondo
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        step.accentColor.copy(alpha = 0.25f),
                        step.secondaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 1.8f
            )

            drawCircle(
                color = step.accentColor.copy(alpha = 0.3f),
                radius = size.minDimension / 2.3f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Mini elementos vectoriales interactivos según el paso
        when (step.stepNumber) {
            1 -> {
                // Ilustración de morfemas entrelazados
                Canvas(modifier = Modifier.size(90.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.5f)
                        lineTo(size.width * 0.8f, size.height * 0.5f)
                    }
                    drawPath(path, color = step.accentColor, style = Stroke(width = 3.dp.toPx()))
                }
            }
            2 -> {
                // Ilustración de destellos estelares (Aura)
                Canvas(modifier = Modifier.size(100.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawLine(
                        color = step.secondaryColor,
                        start = Offset(center.x, 15f),
                        end = Offset(center.x, size.height - 15f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = step.secondaryColor,
                        start = Offset(15f, center.y),
                        end = Offset(size.width - 15f, center.y),
                        strokeWidth = 2.5f
                    )
                }
            }
            3 -> {
                // Ilustración de trazo caligráfico
                Canvas(modifier = Modifier.size(100.dp)) {
                    val wave = Path().apply {
                        moveTo(size.width * 0.15f, size.height * 0.7f)
                        cubicTo(
                            size.width * 0.35f, size.height * 0.2f,
                            size.width * 0.65f, size.height * 0.85f,
                            size.width * 0.85f, size.height * 0.35f
                        )
                    }
                    drawPath(wave, color = step.accentColor, style = Stroke(width = 4.dp.toPx()))
                }
            }
        }

        // Icono central con contenedor esférico
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = CosmicBackground,
            border = BorderStroke(2.dp, step.accentColor),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = step.title,
                    tint = step.accentColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
fun PagerDotsIndicator(
    pagerState: PagerState,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .testTag("onboarding_pager_indicator"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pagerState.pageCount) { iteration ->
            val isSelected = pagerState.currentPage == iteration
            val width = if (isSelected) 28.dp else 10.dp
            val color = if (isSelected) activeColor else inactiveColor.copy(alpha = 0.5f)

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}
