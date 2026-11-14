package com.example.ui.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.NoteEntity
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiOnPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TypographyDropdownMenu
import com.example.ui.theme.appText
import com.example.ui.theme.getAppFontDisplayName
import com.example.ui.theme.getAppFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    note: NoteEntity,
    initialConfig: PdfExportConfig,
    onConfigChange: (PdfExportConfig) -> Unit,
    onExportPdf: (PdfExportConfig) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    var config by remember { mutableStateOf(initialConfig) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isControlsExpanded by remember { mutableStateOf(true) }
    var isRendering by remember { mutableStateOf(true) }
    var renderResult by remember { mutableStateOf(PdfPreviewRenderResult(pageCount = 1, pageBitmaps = emptyList())) }

    LaunchedEffect(config, note.id, note.content, note.title, note.tags) {
        onConfigChange(config)
        isRendering = true
        delay(140)
        renderResult = PdfExportManager.renderPreviewPages(context, note, config)
        isRendering = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pdf_preview_screen"),
            containerColor = CosmicBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = appText("Vista de PDF", "PDF Preview"),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = appText(
                                    "${renderResult.pageCount} pág. · A4",
                                    "${renderResult.pageCount} page(s) · A4"
                                ),
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_preview_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = appText("Volver", "Back"),
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isControlsExpanded = !isControlsExpanded },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_preview_toggle_controls_btn")
                        ) {
                            Icon(
                                imageVector = if (isControlsExpanded) Icons.Default.ExpandMore else Icons.Default.Tune,
                                contentDescription = appText("Mostrar u ocultar ajustes", "Toggle controls"),
                                tint = GeminiBlue
                            )
                        }
                        Button(
                            onClick = { onExportPdf(config) },
                            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_preview_export_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = GeminiOnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = appText("Exportar", "Export"),
                                color = GeminiOnPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSurface)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Live A4 Paper Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(CosmicBackground)
                        .testTag("pdf_preview_canvas_area"),
                    contentAlignment = Alignment.Center
                ) {
                    if (renderResult.pageBitmaps.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 640.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(renderResult.pageBitmaps) { idx, bmp ->
                                PdfRenderedPageSheet(
                                    bitmap = bmp,
                                    pageIndex = idx + 1,
                                    totalPages = renderResult.pageCount,
                                    config = config
                                )
                            }
                        }
                    } else {
                        // Clean simulated A4 sheet fallback when native PdfRenderer is unavailable (e.g., JVM test runner)
                        PdfSimulatedFallbackSheet(
                            note = note,
                            config = config,
                            totalPages = renderResult.pageCount,
                            modifier = Modifier
                                .padding(16.dp)
                                .widthIn(max = 420.dp)
                        )
                    }

                    if (isRendering) {
                        Surface(
                            color = CosmicSurface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CosmicBorder),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = GeminiBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = appText("Actualizando...", "Updating..."),
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Bottom Configurator Sheet
                Surface(
                    color = CosmicSurface,
                    border = BorderStroke(1.dp, CosmicBorder),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isControlsExpanded = !isControlsExpanded }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = GeminiBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appText("Configuración de página PDF", "PDF Page Setup"),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                            Icon(
                                imageVector = if (isControlsExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }

                        AnimatedVisibility(visible = isControlsExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                TabRow(
                                    selectedTabIndex = selectedTab,
                                    containerColor = CosmicSurface,
                                    contentColor = GeminiBlue,
                                    indicator = { tabPositions ->
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = GeminiBlue
                                        )
                                    }
                                ) {
                                    Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        text = {
                                            Text(
                                                appText("Numerador", "Page Num"),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.testTag("pdf_tab_numbering")
                                    )
                                    Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        text = {
                                            Text(
                                                appText("Encab. y pie", "Header/Footer"),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.testTag("pdf_tab_header_footer")
                                    )
                                    Tab(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        text = {
                                            Text(
                                                appText("Márgenes", "Margins"),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.testTag("pdf_tab_margins")
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 190.dp, max = 300.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    when (selectedTab) {
                                        0 -> PageNumberConfigSection(
                                            config = config,
                                            onUpdate = { config = it }
                                        )
                                        1 -> HeaderFooterConfigSection(
                                            config = config,
                                            onUpdate = { config = it }
                                        )
                                        2 -> MarginsConfigSection(
                                            config = config,
                                            onUpdate = { config = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRenderedPageSheet(
    bitmap: Bitmap,
    pageIndex: Int,
    totalPages: Int,
    config: PdfExportConfig
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, CosmicBorder),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PdfExportManager.PAGE_WIDTH_PT.toFloat() / PdfExportManager.PAGE_HEIGHT_PT.toFloat())
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appText("Página $pageIndex de $totalPages", "Page $pageIndex of $totalPages"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        val numPreview = if (config.showPageNumbers) {
            " · #${PdfExportManager.formatPageNumber(pageIndex, config.pageNumberStyle)}"
        } else {
            ""
        }
        Text(
            text = appText("Página $pageIndex de $totalPages$numPreview", "Page $pageIndex of $totalPages$numPreview"),
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PdfSimulatedFallbackSheet(
    note: NoteEntity,
    config: PdfExportConfig,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    val margins = config.resolveMargins()
    val scale = 0.45f
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CosmicBorder),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PdfExportManager.PAGE_WIDTH_PT.toFloat() / PdfExportManager.PAGE_HEIGHT_PT.toFloat())
            .testTag("pdf_preview_sheet_fallback")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val topPad = (margins.top * scale).dp
            val bottomPad = (margins.bottom * scale).dp
            val leftPad = (margins.left * scale).dp
            val rightPad = (margins.right * scale).dp

            if (config.headerEnabled) {
                val hText = PdfExportManager.resolveBannerText(note, config.headerContentMode, config.headerCustomText)
                Text(
                    text = hText,
                    color = Color.DarkGray,
                    fontSize = (config.headerFontSize * 0.8f).sp,
                    fontFamily = getAppFontFamily(config.headerFontFamily),
                    textAlign = if (config.headerPosition == PdfHeaderPosition.TOP_RIGHT) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = (topPad * 0.45f), start = leftPad, end = rightPad)
                        .align(Alignment.TopCenter)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topPad + if (config.headerEnabled) (config.headerHeightPt * scale).dp else 0.dp,
                        bottom = bottomPad + if (config.footerEnabled) (config.footerHeightPt * scale).dp else 0.dp,
                        start = leftPad,
                        end = rightPad
                    )
            ) {
                Text(
                    text = note.title.ifBlank { "Sin Título" },
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = appText(
                        "Vista previa A4 ($totalPages pág.) · Márgenes T:${margins.top.toInt()} B:${margins.bottom.toInt()} L:${margins.left.toInt()} R:${margins.right.toInt()}",
                        "A4 Preview ($totalPages p.) · Margins T:${margins.top.toInt()} B:${margins.bottom.toInt()} L:${margins.left.toInt()} R:${margins.right.toInt()}"
                    ),
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            if (config.footerEnabled) {
                val fText = PdfExportManager.resolveBannerText(note, config.footerContentMode, config.footerCustomText)
                Text(
                    text = fText,
                    color = Color.DarkGray,
                    fontSize = (config.footerFontSize * 0.8f).sp,
                    fontFamily = getAppFontFamily(config.footerFontFamily),
                    textAlign = if (config.footerPosition == PdfFooterPosition.BOTTOM_RIGHT) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (bottomPad * 0.45f), start = leftPad, end = rightPad)
                        .align(Alignment.BottomCenter)
                )
            }

            if (config.showPageNumbers) {
                val pageStr = PdfExportManager.formatPageNumber(1, config.pageNumberStyle)
                val align = when (config.pageNumberPosition) {
                    PdfPageNumberPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    PdfPageNumberPosition.BOTTOM_CENTER -> Alignment.BottomCenter
                    PdfPageNumberPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                    PdfPageNumberPosition.CENTER_LEFT -> Alignment.CenterStart
                    PdfPageNumberPosition.CENTER_RIGHT -> Alignment.CenterEnd
                }
                Text(
                    text = pageStr,
                    color = Color.Gray,
                    fontSize = (config.pageNumberFontSize * 0.85f).sp,
                    fontFamily = getAppFontFamily(config.pageNumberFontFamily),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(align)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PageNumberConfigSection(
    config: PdfExportConfig,
    onUpdate: (PdfExportConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appText("Mostrar numerador de página", "Show page numbers"),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            )
            Switch(
                checked = config.showPageNumbers,
                onCheckedChange = { onUpdate(config.copy(showPageNumbers = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = GeminiOnPrimary, checkedTrackColor = GeminiBlue),
                modifier = Modifier.testTag("pdf_page_number_switch")
            )
        }

        if (config.showPageNumbers) {
            Text(
                text = appText("Estilo del numerador", "Numbering style"),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.pageNumberStyle == PdfPageNumberStyle.NORMAL,
                    onClick = { onUpdate(config.copy(pageNumberStyle = PdfPageNumberStyle.NORMAL)) },
                    label = { Text(appText("Normal (1, 2, 3)", "Normal (1, 2, 3)"), fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                        selectedLabelColor = GeminiBlue
                    ),
                    modifier = Modifier.testTag("pdf_number_style_normal")
                )
                FilterChip(
                    selected = config.pageNumberStyle == PdfPageNumberStyle.ROMAN,
                    onClick = { onUpdate(config.copy(pageNumberStyle = PdfPageNumberStyle.ROMAN)) },
                    label = { Text(appText("Romano (I, II, III)", "Roman (I, II, III)"), fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                        selectedLabelColor = GeminiBlue
                    ),
                    modifier = Modifier.testTag("pdf_number_style_roman")
                )
            }

            Text(
                text = appText("Posición en página", "Position"),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    PdfPageNumberPosition.BOTTOM_LEFT to appText("Inferior izq.", "Bottom Left"),
                    PdfPageNumberPosition.BOTTOM_CENTER to appText("Inferior centro", "Bottom Center"),
                    PdfPageNumberPosition.BOTTOM_RIGHT to appText("Inferior der.", "Bottom Right"),
                    PdfPageNumberPosition.CENTER_LEFT to appText("Centro izq.", "Middle Left"),
                    PdfPageNumberPosition.CENTER_RIGHT to appText("Centro der.", "Middle Right")
                ).forEach { (pos, label) ->
                    FilterChip(
                        selected = config.pageNumberPosition == pos,
                        onClick = { onUpdate(config.copy(pageNumberPosition = pos)) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                            selectedLabelColor = GeminiBlue
                        ),
                        modifier = Modifier.testTag("pdf_num_pos_${pos.id}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(appText("Tamaño de letra", "Font size"), color = TextSecondary, fontSize = 12.sp)
                Text("${config.pageNumberFontSize.roundToInt()} pt", color = GeminiBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = config.pageNumberFontSize,
                onValueChange = { onUpdate(config.copy(pageNumberFontSize = it.roundToInt().toFloat())) },
                valueRange = 8f..18f,
                colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue)
            )

            PdfFontPickerRow(
                label = appText("Tipografía del numerador", "Numbering font"),
                selectedFontId = config.pageNumberFontFamily,
                onFontSelected = { onUpdate(config.copy(pageNumberFontFamily = it)) }
            )
        }
    }
}

@Composable
private fun HeaderFooterConfigSection(
    config: PdfExportConfig,
    onUpdate: (PdfExportConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ENCABEZADO
        Surface(
            color = CosmicSurfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CosmicBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(appText("Encabezado", "Header"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Switch(
                        checked = config.headerEnabled,
                        onCheckedChange = { onUpdate(config.copy(headerEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = GeminiOnPrimary, checkedTrackColor = GeminiBlue),
                        modifier = Modifier.testTag("pdf_header_switch")
                    )
                }

                if (config.headerEnabled) {
                    Text(appText("Posición", "Position"), color = TextSecondary, fontSize = 11.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.headerPosition == PdfHeaderPosition.TOP_LEFT,
                            onClick = { onUpdate(config.copy(headerPosition = PdfHeaderPosition.TOP_LEFT)) },
                            label = { Text(appText("Arriba izq.", "Top Left"), fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                                selectedLabelColor = GeminiBlue
                            )
                        )
                        FilterChip(
                            selected = config.headerPosition == PdfHeaderPosition.TOP_RIGHT,
                            onClick = { onUpdate(config.copy(headerPosition = PdfHeaderPosition.TOP_RIGHT)) },
                            label = { Text(appText("Arriba der.", "Top Right"), fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                                selectedLabelColor = GeminiBlue
                            )
                        )
                    }

                    BannerContentModeRow(
                        mode = config.headerContentMode,
                        customText = config.headerCustomText,
                        onModeChange = { onUpdate(config.copy(headerContentMode = it)) },
                        onCustomTextChange = { onUpdate(config.copy(headerCustomText = it)) }
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(appText("Altura de encabezado", "Header height"), color = TextSecondary, fontSize = 11.5.sp)
                        Text("${config.headerHeightPt.roundToInt()} pt", color = GeminiBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.headerHeightPt,
                        onValueChange = { onUpdate(config.copy(headerHeightPt = it.roundToInt().toFloat())) },
                        valueRange = 18f..64f,
                        colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue)
                    )

                    PdfFontPickerRow(
                        label = appText("Tipografía", "Font"),
                        selectedFontId = config.headerFontFamily,
                        onFontSelected = { onUpdate(config.copy(headerFontFamily = it)) }
                    )
                }
            }
        }

        // PIE DE PÁGINA
        Surface(
            color = CosmicSurfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CosmicBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(appText("Pie de página", "Footer"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Switch(
                        checked = config.footerEnabled,
                        onCheckedChange = { onUpdate(config.copy(footerEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = GeminiOnPrimary, checkedTrackColor = GeminiBlue),
                        modifier = Modifier.testTag("pdf_footer_switch")
                    )
                }

                if (config.footerEnabled) {
                    Text(appText("Posición", "Position"), color = TextSecondary, fontSize = 11.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.footerPosition == PdfFooterPosition.BOTTOM_LEFT,
                            onClick = { onUpdate(config.copy(footerPosition = PdfFooterPosition.BOTTOM_LEFT)) },
                            label = { Text(appText("Inferior izq.", "Bottom Left"), fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                                selectedLabelColor = GeminiBlue
                            )
                        )
                        FilterChip(
                            selected = config.footerPosition == PdfFooterPosition.BOTTOM_RIGHT,
                            onClick = { onUpdate(config.copy(footerPosition = PdfFooterPosition.BOTTOM_RIGHT)) },
                            label = { Text(appText("Inferior der.", "Bottom Right"), fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                                selectedLabelColor = GeminiBlue
                            )
                        )
                    }

                    BannerContentModeRow(
                        mode = config.footerContentMode,
                        customText = config.footerCustomText,
                        onModeChange = { onUpdate(config.copy(footerContentMode = it)) },
                        onCustomTextChange = { onUpdate(config.copy(footerCustomText = it)) }
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(appText("Altura de pie", "Footer height"), color = TextSecondary, fontSize = 11.5.sp)
                        Text("${config.footerHeightPt.roundToInt()} pt", color = GeminiBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.footerHeightPt,
                        onValueChange = { onUpdate(config.copy(footerHeightPt = it.roundToInt().toFloat())) },
                        valueRange = 18f..64f,
                        colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue)
                    )

                    PdfFontPickerRow(
                        label = appText("Tipografía", "Font"),
                        selectedFontId = config.footerFontFamily,
                        onFontSelected = { onUpdate(config.copy(footerFontFamily = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerContentModeRow(
    mode: PdfBannerContentMode,
    customText: String,
    onModeChange: (PdfBannerContentMode) -> Unit,
    onCustomTextChange: (String) -> Unit
) {
    Text(appText("Contenido", "Content"), color = TextSecondary, fontSize = 11.5.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            PdfBannerContentMode.NOTE_TITLE to appText("Título", "Title"),
            PdfBannerContentMode.DATE_TIME to appText("Fecha", "Date"),
            PdfBannerContentMode.TITLE_AND_DATE to appText("Título + Fecha", "Title + Date"),
            PdfBannerContentMode.CUSTOM_TEXT to appText("Texto libre", "Custom text")
        ).forEach { (itemMode, label) ->
            FilterChip(
                selected = mode == itemMode,
                onClick = { onModeChange(itemMode) },
                label = { Text(label, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                    selectedLabelColor = GeminiBlue
                )
            )
        }
    }
    if (mode == PdfBannerContentMode.CUSTOM_TEXT) {
        OutlinedTextField(
            value = customText,
            onValueChange = onCustomTextChange,
            singleLine = true,
            placeholder = {
                Text(
                    appText("Escribe tu texto...", "Write custom text..."),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GeminiBlue,
                unfocusedBorderColor = CosmicBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_banner_custom_text_input")
        )
    }
}

@Composable
private fun MarginsConfigSection(
    config: PdfExportConfig,
    onUpdate: (PdfExportConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = appText("Preajustes y márgenes personalizados", "Presets & custom margins"),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                PdfMarginPreset.NARROW to appText("Estrecho (26)", "Narrow (26)"),
                PdfMarginPreset.NORMAL to appText("Normal (50)", "Normal (50)"),
                PdfMarginPreset.WIDE to appText("Amplio (72)", "Wide (72)"),
                PdfMarginPreset.CUSTOM to appText("Personalizado", "Custom")
            ).forEach { (preset, label) ->
                FilterChip(
                    selected = config.marginPreset == preset,
                    onClick = {
                        val updated = if (preset == PdfMarginPreset.CUSTOM && config.marginPreset != PdfMarginPreset.CUSTOM) {
                            val current = config.resolveMargins()
                            config.copy(
                                marginPreset = PdfMarginPreset.CUSTOM,
                                customMarginTop = current.top,
                                customMarginBottom = current.bottom,
                                customMarginLeft = current.left,
                                customMarginRight = current.right
                            )
                        } else {
                            config.copy(marginPreset = preset)
                        }
                        onUpdate(updated)
                    },
                    label = { Text(label, fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeminiBlue.copy(alpha = 0.22f),
                        selectedLabelColor = GeminiBlue
                    ),
                    modifier = Modifier.testTag("pdf_margin_preset_${preset.id}")
                )
            }
        }

        val resolved = config.resolveMargins()
        if (config.marginPreset == PdfMarginPreset.CUSTOM) {
            HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
            MarginSliderRow(
                label = appText("Margen superior", "Top margin"),
                value = config.customMarginTop,
                sliderTestTag = "pdf_margin_slider_top",
                onChange = { onUpdate(config.copy(customMarginTop = it)) }
            )
            MarginSliderRow(
                label = appText("Margen inferior", "Bottom margin"),
                value = config.customMarginBottom,
                sliderTestTag = "pdf_margin_slider_bottom",
                onChange = { onUpdate(config.copy(customMarginBottom = it)) }
            )
            MarginSliderRow(
                label = appText("Margen izquierdo", "Left margin"),
                value = config.customMarginLeft,
                sliderTestTag = "pdf_margin_slider_left",
                onChange = { onUpdate(config.copy(customMarginLeft = it)) }
            )
            MarginSliderRow(
                label = appText("Margen derecho", "Right margin"),
                value = config.customMarginRight,
                sliderTestTag = "pdf_margin_slider_right",
                onChange = { onUpdate(config.copy(customMarginRight = it)) }
            )
        } else {
            Surface(
                color = CosmicSurfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = appText(
                        "Márgenes actuales: Sup. ${resolved.top.toInt()} pt · Inf. ${resolved.bottom.toInt()} pt · Izq. ${resolved.left.toInt()} pt · Der. ${resolved.right.toInt()} pt",
                        "Current margins: Top ${resolved.top.toInt()} pt · Bottom ${resolved.bottom.toInt()} pt · Left ${resolved.left.toInt()} pt · Right ${resolved.right.toInt()} pt"
                    ),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MarginSliderRow(
    label: String,
    value: Float,
    sliderTestTag: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 12.sp)
            Text("${value.roundToInt()} pt", color = GeminiBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = 16f..110f,
            colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue),
            modifier = Modifier.testTag(sliderTestTag)
        )
    }
}

@Composable
private fun PdfFontPickerRow(
    label: String,
    selectedFontId: String,
    onFontSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Box {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CosmicSurfaceVariant,
                border = BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = getAppFontDisplayName(selectedFontId),
                        fontFamily = getAppFontFamily(selectedFontId),
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = GeminiBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            TypographyDropdownMenu(
                expanded = expanded,
                selectedFontId = selectedFontId,
                onFontSelected = {
                    onFontSelected(it)
                    expanded = false
                },
                onDismiss = { expanded = false }
            )
        }
    }
}
