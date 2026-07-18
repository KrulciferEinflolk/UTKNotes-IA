package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.data.model.BookEntity
import com.example.ui.theme.*
import kotlin.math.absoluteValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clipToBounds

enum class LibraryViewMode {
    Grid,
    Carousel
}

@Composable
fun ColorWheel(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val x = offset.x - center.x
                    val y = offset.y - center.y
                    val radius = size.width / 2f
                    val distance = kotlin.math.sqrt(x * x + y * y)
                    if (radius > 0 && distance <= radius) {
                        val saturation = (distance / radius).coerceIn(0f, 1f)
                        var angle = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        
                        val hsv = floatArrayOf(angle, saturation, 1f)
                        onColorSelected(Color(android.graphics.Color.HSVToColor(hsv)))
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val offset = change.position
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val x = offset.x - center.x
                    val y = offset.y - center.y
                    val radius = size.width / 2f
                    val distance = kotlin.math.sqrt(x * x + y * y)
                    if (radius > 0 && distance <= radius) {
                        val saturation = (distance / radius).coerceIn(0f, 1f)
                        var angle = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        
                        val hsv = floatArrayOf(angle, saturation, 1f)
                        onColorSelected(Color(android.graphics.Color.HSVToColor(hsv)))
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().onGloballyPositioned { size = it.size.toSize() }) {
            val radius = size.width / 2f
            val centerPoint = Offset(size.width / 2f, size.height / 2f)
            if (radius > 0) {
                // Draw a sweep gradient of hues
                val sweepColors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                drawCircle(
                    brush = Brush.sweepGradient(sweepColors, center = centerPoint),
                    radius = radius
                )
                
                // Overlay radial gradient for saturation (white in center, transparent at edge)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        center = centerPoint,
                        radius = radius
                    )
                )
                
                // Draw selector indicator at the selected color's coordinates
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                
                val angleRad = Math.toRadians(hue.toDouble())
                val indicatorRadius = radius * saturation
                val indicatorX = centerPoint.x + (indicatorRadius * kotlin.math.cos(angleRad)).toFloat()
                val indicatorY = centerPoint.y + (indicatorRadius * kotlin.math.sin(angleRad)).toFloat()
                
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(indicatorX, indicatorY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = selectedColor,
                    radius = 6.dp.toPx(),
                    center = Offset(indicatorX, indicatorY)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCustomizationDialog(
    editingBook: BookEntity? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (
        title: String,
        colorHex: String,
        coverUri: String?,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf(editingBook?.title ?: "") }
    var selectedColor by remember {
        mutableStateOf(
            try {
                if (editingBook?.colorHex != null) {
                    Color(android.graphics.Color.parseColor(editingBook.colorHex))
                } else {
                    Color(0xFF907CFF)
                }
            } catch (e: Exception) {
                Color(0xFF907CFF)
            }
        )
    }
    var coverUri by remember { mutableStateOf<String?>(editingBook?.coverUri) }
    var coverScale by remember { mutableStateOf(editingBook?.coverScale ?: 1.0f) }
    var coverOffsetX by remember { mutableStateOf(editingBook?.coverOffsetX ?: 0f) }
    var coverOffsetY by remember { mutableStateOf(editingBook?.coverOffsetY ?: 0f) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore
            }
            coverUri = uri.toString()
            coverScale = 1.0f
            coverOffsetX = 0f
            coverOffsetY = 0f
        }
    }

    val selectedColorHex = remember(selectedColor) {
        String.format("#%06X", 0xFFFFFF and selectedColor.toArgb())
    }

    val tempBook = remember(title, selectedColorHex, coverUri, coverScale, coverOffsetX, coverOffsetY) {
        BookEntity(
            id = "preview_id",
            title = if (title.isBlank()) "Título del Libro" else title,
            colorHex = selectedColorHex,
            coverUri = coverUri,
            coverScale = coverScale,
            coverOffsetX = coverOffsetX,
            coverOffsetY = coverOffsetY
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        if (title.isBlank()) "Nuevo Libro" else title,
                        selectedColorHex,
                        coverUri,
                        coverScale,
                        coverOffsetX,
                        coverOffsetY
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
            ) {
                Text(if (editingBook != null) "Guardar" else "Crear Libro", color = GeminiOnPrimary)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editingBook != null && onDelete != null) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar Libro", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar", color = Color.White)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        },
        title = {
            Text(
                if (editingBook != null) "Personalizar Libro" else "Personalizar Nuevo Libro",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth > 520.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left: Live Preview
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Vista Previa",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeminiCyanAccent,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (coverUri != null) {
                                                coverOffsetX += dragAmount.x
                                                coverOffsetY += dragAmount.y
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Book25D(book = tempBook)
                            }

                            if (coverUri != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "💡 Arrastra la portada en el preview para encuadrarla",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Right: Controls
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Nombre del Libro") },
                                    placeholder = { Text("Ej: Matemáticas, Recetario...") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GeminiBlue,
                                        unfocusedBorderColor = CosmicBorder,
                                        focusedContainerColor = CosmicSurface,
                                        unfocusedContainerColor = CosmicSurface,
                                        focusedLabelColor = GeminiBlue,
                                        unfocusedLabelColor = TextSecondary,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                Text(
                                    "Color de Cubierta",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ColorWheel(
                                        selectedColor = selectedColor,
                                        onColorSelected = { selectedColor = it },
                                        modifier = Modifier.size(120.dp)
                                    )
                                }
                            }

                            item {
                                val presets = listOf(
                                    Color(0xFFE57373),
                                    Color(0xFFF06292),
                                    Color(0xFFBA68C8),
                                    Color(0xFF64B5F6),
                                    Color(0xFF4DB6AC),
                                    Color(0xFF81C784),
                                    Color(0xFFFFB74D)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    presets.forEach { presetColor ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(presetColor)
                                                .border(
                                                    width = if (selectedColor == presetColor) 2.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedColor = presetColor }
                                        )
                                    }
                                }
                            }

                            item {
                                Divider(color = CosmicBorder)
                            }

                            item {
                                Text(
                                    "Imagen de Portada (Opcional)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurface),
                                        border = BorderStroke(1.dp, GeminiBlue.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GeminiBlue)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (coverUri == null) "Elegir Imagen" else "Cambiar Imagen",
                                            color = GeminiBlue,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (coverUri != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                coverUri = null
                                                coverScale = 1.0f
                                                coverOffsetX = 0f
                                                coverOffsetY = 0f
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                                        }
                                    }
                                }
                            }

                            if (coverUri != null) {
                                item {
                                    Text(
                                        "Ajustar Tamaño (Zoom): ${((coverScale * 10).toInt() / 10f)}x",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Slider(
                                        value = coverScale,
                                        onValueChange = { coverScale = it },
                                        valueRange = 1.0f..4.0f,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = GeminiBlue,
                                            thumbColor = GeminiBlue
                                        )
                                    )
                                }
                                
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Encuadre", fontSize = 12.sp, color = TextSecondary)
                                        TextButton(
                                            onClick = {
                                                coverScale = 1.0f
                                                coverOffsetX = 0f
                                                coverOffsetY = 0f
                                            }
                                        ) {
                                            Text("Restablecer", fontSize = 11.sp, color = GeminiBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Narrow Column layout
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .width(130.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (coverUri != null) {
                                                coverOffsetX += dragAmount.x
                                                coverOffsetY += dragAmount.y
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Book25D(book = tempBook)
                            }
                        }

                        if (coverUri != null) {
                            item {
                                Text(
                                    "💡 Arrastra la portada arriba para encuadrarla",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Nombre del Libro") },
                                placeholder = { Text("Ej: Matemáticas, Recetario...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GeminiBlue,
                                    unfocusedBorderColor = CosmicBorder,
                                    focusedContainerColor = CosmicSurface,
                                    unfocusedContainerColor = CosmicSurface,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                "Color de Cubierta",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ColorWheel(
                                    selectedColor = selectedColor,
                                    onColorSelected = { selectedColor = it },
                                    modifier = Modifier.size(110.dp)
                                )
                            }
                        }

                        item {
                            val presets = listOf(
                                Color(0xFFE57373),
                                Color(0xFFF06292),
                                Color(0xFFBA68C8),
                                Color(0xFF64B5F6),
                                Color(0xFF4DB6AC),
                                Color(0xFF81C784),
                                Color(0xFFFFB74D)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                presets.forEach { presetColor ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(presetColor)
                                                .border(
                                                    width = if (selectedColor == presetColor) 2.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedColor = presetColor }
                                        )
                                }
                            }
                        }

                        item {
                            Divider(color = CosmicBorder)
                        }

                        item {
                            Text(
                                "Imagen de Portada (Opcional)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurface),
                                    border = BorderStroke(1.dp, GeminiBlue.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GeminiBlue)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (coverUri == null) "Elegir Imagen" else "Cambiar Imagen",
                                        color = GeminiBlue,
                                        fontSize = 12.sp
                                    )
                                }
                                if (coverUri != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            coverUri = null
                                            coverScale = 1.0f
                                            coverOffsetX = 0f
                                            coverOffsetY = 0f
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                                    }
                                }
                            }
                        }

                        if (coverUri != null) {
                            item {
                                Text(
                                    "Ajustar Tamaño (Zoom): ${((coverScale * 10).toInt() / 10f)}x",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Slider(
                                    value = coverScale,
                                    onValueChange = { coverScale = it },
                                    valueRange = 1.0f..4.0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = GeminiBlue,
                                        thumbColor = GeminiBlue
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

enum class NavigationTab {
    Search,
    Home,
    Community
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMainScreen(viewModel: AetherViewModel, modifier: Modifier = Modifier) {
    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.Home) }
    var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.Carousel) }
    
    val books by viewModel.books.collectAsState()
    
    var showAddBookDialog by rememberSaveable { mutableStateOf(false) }
    var editingBookState by remember { mutableStateOf<BookEntity?>(null) }
    
    if (showAddBookDialog) {
        BookCustomizationDialog(
            onDismiss = { showAddBookDialog = false },
            onConfirm = { title, colorHex, coverUri, scale, offsetX, offsetY ->
                viewModel.addBook(
                    title = title,
                    colorHex = colorHex,
                    coverUri = coverUri,
                    coverScale = scale,
                    coverOffsetX = offsetX,
                    coverOffsetY = offsetY
                )
                showAddBookDialog = false
            }
        )
    }

    if (editingBookState != null) {
        BookCustomizationDialog(
            editingBook = editingBookState,
            onDismiss = { editingBookState = null },
            onDelete = {
                viewModel.deleteBook(editingBookState!!)
                editingBookState = null
            },
            onConfirm = { title, colorHex, coverUri, scale, offsetX, offsetY ->
                viewModel.updateBook(
                    editingBookState!!.copy(
                        title = title,
                        colorHex = colorHex,
                        coverUri = coverUri,
                        coverScale = scale,
                        coverOffsetX = offsetX,
                        coverOffsetY = offsetY
                    )
                )
                editingBookState = null
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CosmicBackground,
        bottomBar = {
            LibraryBottomNavigationBar(currentTab) { currentTab = it }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                NavigationTab.Search -> {
                    LibrarySearchScreen(viewModel)
                }
                NavigationTab.Home -> {
                    LibraryHomeScreen(
                        viewModel = viewModel,
                        books = books,
                        viewMode = viewMode,
                        onToggleViewMode = { 
                            viewMode = if (viewMode == LibraryViewMode.Grid) LibraryViewMode.Carousel else LibraryViewMode.Grid
                        },
                        onAddBookClick = { showAddBookDialog = true },
                        onBookLongClick = { editingBookState = it }
                    )
                }
                NavigationTab.Community -> {
                    LibraryCommunityScreen()
                }
            }
        }
    }
}

@Composable
fun LibraryBottomNavigationBar(currentTab: NavigationTab, onTabSelected: (NavigationTab) -> Unit) {
    NavigationBar(
        containerColor = CosmicSurface,
        contentColor = GeminiBlue
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            label = { Text("Buscar") },
            selected = currentTab == NavigationTab.Search,
            onClick = { onTabSelected(NavigationTab.Search) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GeminiBlue,
                selectedTextColor = GeminiBlue,
                indicatorColor = GeminiBlue.copy(alpha = 0.2f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
            label = { Text("Inicio") },
            selected = currentTab == NavigationTab.Home,
            onClick = { onTabSelected(NavigationTab.Home) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GeminiBlue,
                selectedTextColor = GeminiBlue,
                indicatorColor = GeminiBlue.copy(alpha = 0.2f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.People, contentDescription = "Comunidad") },
            label = { Text("Comunidad") },
            selected = currentTab == NavigationTab.Community,
            onClick = { onTabSelected(NavigationTab.Community) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GeminiBlue,
                selectedTextColor = GeminiBlue,
                indicatorColor = GeminiBlue.copy(alpha = 0.2f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
    }
}

@Composable
fun LibraryHomeScreen(
    viewModel: AetherViewModel,
    books: List<BookEntity>,
    viewMode: LibraryViewMode,
    onToggleViewMode: () -> Unit,
    onAddBookClick: () -> Unit,
    onBookLongClick: (BookEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mi Biblioteca",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = if (viewMode == LibraryViewMode.Grid) Icons.Default.ViewCarousel else Icons.Default.GridView,
                    contentDescription = "Cambiar vista",
                    tint = GeminiBlue
                )
            }
        }
        
        if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = GeminiBlue,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "La biblioteca está vacía",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Crea tu primer libro personalizado para empezar a organizar tus notas.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onAddBookClick,
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = GeminiOnPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear un Libro", color = GeminiOnPrimary)
                    }
                }
            }
        } else {
            if (viewMode == LibraryViewMode.Grid) {
                LibraryGridView(viewModel, books, onAddBookClick, onBookLongClick)
            } else {
                LibraryCarouselView(viewModel, books, onAddBookClick, onBookLongClick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridView(
    viewModel: AetherViewModel,
    books: List<BookEntity>,
    onAddBookClick: () -> Unit,
    onBookLongClick: (BookEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.id }) { book ->
            Book25D(
                book = book,
                modifier = Modifier.combinedClickable(
                    onClick = { viewModel.selectBook(book) },
                    onLongClick = { onBookLongClick(book) }
                )
            )
        }
        item {
            AddBookCard(onClick = onAddBookClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCarouselView(
    viewModel: AetherViewModel,
    books: List<BookEntity>,
    onAddBookClick: () -> Unit,
    onBookLongClick: (BookEntity) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { books.size + 1 })
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // We want the center item to be 60% of the screen.
    val horizontalPadding = screenWidth * 0.20f
    
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        modifier = Modifier.fillMaxSize(),
        pageSpacing = 0.dp
    ) { page ->
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        
        // As distance from center increases, scale decreases
        val scale = lerp(
            start = 0.65f,
            stop = 1f,
            fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
        )
        
        val alphaVal = lerp(
            start = 0.5f,
            stop = 1f,
            fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f) // Keep it centered vertically and reasonably sized
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = alphaVal
                },
            contentAlignment = Alignment.Center
        ) {
            if (page < books.size) {
                Book25D(
                    book = books[page],
                    modifier = Modifier.combinedClickable(
                        onClick = { viewModel.selectBook(books[page]) },
                        onLongClick = { onBookLongClick(books[page]) }
                    )
                )
            } else {
                AddBookCard(onClick = onAddBookClick)
            }
        }
    }
}

@Composable
fun Book25D(
    book: BookEntity,
    modifier: Modifier = Modifier
) {
    val defaultColors = listOf(
        Color(0xFF2C3E50) to Color(0xFF34495E),
        Color(0xFF8E44AD) to Color(0xFF9B59B6),
        Color(0xFF2980B9) to Color(0xFF3498DB),
        Color(0xFF27AE60) to Color(0xFF2ECC71),
        Color(0xFFC0392B) to Color(0xFFE74C3C)
    )
    
    val baseColor = remember(book.colorHex, book.id) {
        try {
            Color(android.graphics.Color.parseColor(book.colorHex))
        } catch (e: Exception) {
            val hash = book.id.hashCode().absoluteValue
            defaultColors[hash % defaultColors.size].first
        }
    }
    
    val secondaryColor = remember(baseColor) {
        Color(
            red = (baseColor.red * 0.7f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.7f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.7f).coerceIn(0f, 1f),
            alpha = baseColor.alpha
        )
    }
    
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.65f)
                .shadow(
                    elevation = 12.dp, 
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                    clip = false
                )
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.9f),
                            baseColor,
                            secondaryColor
                        ),
                        startX = 0f,
                        endX = 150f
                    )
                )
        ) {
            // CUSTOM COVER IMAGE BACKGROUND (if available)
            if (!book.coverUri.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    coil.compose.AsyncImage(
                        model = book.coverUri,
                        contentDescription = "Portada",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = book.coverScale
                                scaleY = book.coverScale
                                translationX = book.coverOffsetX
                                translationY = book.coverOffsetY
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            // Spine crease
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .padding(start = 4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Cover content (if cover image is set, we can add a subtle backing scrim to keep title text readable)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                contentAlignment = Alignment.TopStart
            ) {
                // Readability shadow under text if there's a cover image
                val textShadow = if (!book.coverUri.isNullOrEmpty()) {
                    androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                } else null

                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(shadow = textShadow)
                )
            }
            
            // Inner shadow / page effect on the right edge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f))
                        )
                    )
            )
            
            // Inner light gradient for the 2.5D curved effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = book.title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchScreen(viewModel: AetherViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { newValue -> viewModel.updateSearchQuery(newValue) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            placeholder = { Text("Buscar libros, capítulos, contenido...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GeminiBlue,
                unfocusedBorderColor = CosmicBorder,
                focusedContainerColor = CosmicSurface, unfocusedContainerColor = CosmicSurface
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Resultados de búsqueda", color = TextSecondary, fontSize = 14.sp)
        
        val books by viewModel.books.collectAsState()
        val pages by viewModel.pages.collectAsState()
        val notes by viewModel.notes.collectAsState()
        
        val filteredBooks = if (query.isBlank()) emptyList() else books.filter { it.title.contains(query, ignoreCase = true) }
        val filteredPages = if (query.isBlank()) emptyList() else pages.filter { it.title.contains(query, ignoreCase = true) }
        val filteredNotes = if (query.isBlank()) emptyList() else notes.filter { it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) }
        
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredBooks.isNotEmpty()) {
                item { Text("Libros", fontWeight = FontWeight.Bold, color = GeminiBlue) }
                items(filteredBooks.size) { i ->
                    val book = filteredBooks[i]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectBook(book) },
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant)
                    ) {
                        Text(book.title, modifier = Modifier.padding(16.dp), color = TextPrimary)
                    }
                }
            }
            if (filteredPages.isNotEmpty()) {
                item { Text("Páginas", fontWeight = FontWeight.Bold, color = GeminiBlue) }
                items(filteredPages.size) { i ->
                    val page = filteredPages[i]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            val book = books.find { it.id == page.bookId }
                            if (book != null) {
                                viewModel.selectBook(book)
                                viewModel.selectPage(page)
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant)
                    ) {
                        Text(page.title, modifier = Modifier.padding(16.dp), color = TextPrimary)
                    }
                }
            }
            if (filteredNotes.isNotEmpty()) {
                item { Text("Notas", fontWeight = FontWeight.Bold, color = GeminiBlue) }
                items(filteredNotes.size) { i ->
                    val note = filteredNotes[i]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            val page = pages.find { it.id == note.pageId }
                            if (page != null) {
                                val book = books.find { it.id == page.bookId }
                                if (book != null) {
                                    viewModel.selectBook(book)
                                    viewModel.selectPage(page)
                                    viewModel.selectNote(note)
                                }
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant)
                    ) {
                        Text(note.title, modifier = Modifier.padding(16.dp), color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryCommunityScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(64.dp), tint = GeminiBlue)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Comunidad",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Gestión de notas compartidas y colaboración",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun AddBookCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.65f)
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                .border(2.dp, GeminiBlue.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                .background(Color.Transparent)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Añadir Libro", tint = GeminiBlue, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Nuevo Libro", color = GeminiBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
