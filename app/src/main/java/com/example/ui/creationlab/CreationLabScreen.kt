package com.example.ui.creationlab

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LabItemEntity
import com.example.data.model.LabItemType
import com.example.data.repository.CreationLabRepository
import com.example.ui.AetherViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

// Paleta de Estilo Laboratorio Lingüístico
val VyianoraDarkBackground = Color(0xFF090C15)
val VyianoraSurface = Color(0xFF131826)
val VyianoraBorder = Color(0xFF26304A)
val VyianoraPurple = Color(0xFF8C7CFF)
val VyianoraCyan = Color(0xFF00CEC9)
val VyianoraGold = Color(0xFFFFD166)
val VyianoraRose = Color(0xFFFF7675)

data class DrawnPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

enum class LabMainMenu(val label: String, val icon: ImageVector) {
    Lexicon("Léxico", Icons.Default.MenuBook),
    Generator("Generador", Icons.Default.Extension),
    Phrases("Frases o Palabras", Icons.Default.AutoAwesome),
    Library("Biblioteca", Icons.Default.FolderSpecial)
}

enum class LexiconSubMenu(val label: String) {
    Nuclei("Núcleos (Raíces)"),
    Morphology("Morfología"),
    Categories("Categorías")
}

enum class LibrarySubTab(val label: String) {
    Nuclei("Núcleos"),
    Morphology("Morfología"),
    Words("Palabras"),
    Phrases("Frases"),
    WritingCanvas("Lienzo de escritura")
}

enum class FilterOrder {
    Alphabetical,
    Recent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationLabScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit,
    onReopenTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { CreationLabRepository.getInstance(context) }
    val userEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
    val safeUserEmail = userEmail ?: ""

    LaunchedEffect(safeUserEmail) {
        repository.ensureDefaultData(safeUserEmail)
    }

    val allItems by repository.getAllLabItemsFlow(safeUserEmail).collectAsStateWithLifecycle(initialValue = emptyList())

    val nuclei = remember(allItems) { allItems.filter { it.itemType == LabItemType.NUCLEUS } }
    val morphology = remember(allItems) { allItems.filter { it.itemType == LabItemType.MORPHOLOGY } }
    val categories = remember(allItems) { allItems.filter { it.itemType == LabItemType.CATEGORY } }
    val generatedWords = remember(allItems) { allItems.filter { it.itemType == LabItemType.WORD } }
    val phrases = remember(allItems) { allItems.filter { it.itemType == LabItemType.PHRASE } }

    var selectedMainMenu by rememberSaveable { mutableStateOf(LabMainMenu.Lexicon) }
    var showWritingCanvasDialog by rememberSaveable { mutableStateOf(false) }
    var labTitle by rememberSaveable { mutableStateOf(CreationLabPrefs.getLabTitle(context)) }
    var showEditLabNameDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("creation_lab_screen"),
        containerColor = VyianoraDarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VyianoraSurface)
                    .statusBarsPadding()
            ) {
                // Header del Laboratorio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("lab_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Regresar",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = labTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = VyianoraCyan,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = { showEditLabNameDialog = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("edit_lab_name_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar nombre del laboratorio o idioma",
                                        tint = VyianoraPurple,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = VyianoraPurple.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, VyianoraPurple.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = VyianoraPurple,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Aura IA",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VyianoraPurple
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Léxico, morfología, frases y biblioteca",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botón Lienzo de Escritura
                        FilledTonalButton(
                            onClick = { showWritingCanvasDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VyianoraPurple.copy(alpha = 0.2f),
                                contentColor = VyianoraPurple
                            ),
                            border = BorderStroke(1.dp, VyianoraPurple.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = "Lienzo de escritura", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lienzo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = onReopenTutorial) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Ver tutorial",
                                tint = VyianoraCyan
                            )
                        }
                    }
                }

                // Selector de los 4 Menús Principales
                ScrollableTabRow(
                    selectedTabIndex = selectedMainMenu.ordinal,
                    containerColor = VyianoraSurface,
                    contentColor = VyianoraCyan,
                    edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = VyianoraBorder) }
                ) {
                    LabMainMenu.values().forEach { menu ->
                        val isSelected = selectedMainMenu == menu
                        Tab(
                            selected = isSelected,
                            onClick = { selectedMainMenu = menu },
                            text = {
                                Text(
                                    text = menu.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) VyianoraCyan else TextSecondary
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = menu.icon,
                                    contentDescription = menu.label,
                                    tint = if (isSelected) VyianoraCyan else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedMainMenu) {
                LabMainMenu.Lexicon -> {
                    LexiconMainModule(
                        viewModel = viewModel,
                        repository = repository,
                        userEmail = safeUserEmail,
                        categories = categories,
                        nuclei = nuclei,
                        morphology = morphology
                    )
                }
                LabMainMenu.Generator -> {
                    GeneratorMorphemesModule(
                        viewModel = viewModel,
                        repository = repository,
                        userEmail = safeUserEmail,
                        nuclei = nuclei,
                        morphology = morphology
                    )
                }
                LabMainMenu.Phrases -> {
                    PhrasesModule(
                        viewModel = viewModel,
                        repository = repository,
                        userEmail = safeUserEmail,
                        nuclei = nuclei,
                        morphology = morphology
                    )
                }
                LabMainMenu.Library -> {
                    LibraryLabModule(
                        repository = repository,
                        nuclei = nuclei,
                        morphology = morphology,
                        words = generatedWords,
                        phrases = phrases,
                        onOpenWritingCanvas = { showWritingCanvasDialog = true }
                    )
                }
            }

            // Diálogo Modal Lienzo de Escritura (Canvas)
            if (showWritingCanvasDialog) {
                WritingCanvasDialog(
                    viewModel = viewModel,
                    repository = repository,
                    userEmail = safeUserEmail,
                    onDismiss = { showWritingCanvasDialog = false }
                )
            }

            // Diálogo Modal para Editar el Nombre del Laboratorio / Idioma
            if (showEditLabNameDialog) {
                var tempTitle by rememberSaveable { mutableStateOf(labTitle) }
                AlertDialog(
                    onDismissRequest = { showEditLabNameDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = VyianoraCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nombre del Laboratorio / Idioma", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Personaliza el nombre de tu laboratorio o de la lengua construida que estás creando.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            OutlinedTextField(
                                value = tempTitle,
                                onValueChange = { tempTitle = it },
                                label = { Text("Nombre") },
                                placeholder = { Text("Ej. Laboratorio Lingüístico, Mi Lengua, etc.") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmed = tempTitle.trim().ifEmpty { CreationLabPrefs.DEFAULT_LAB_TITLE }
                                labTitle = trimmed
                                CreationLabPrefs.setLabTitle(context, trimmed)
                                showEditLabNameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                        ) {
                            Text("Guardar", color = Color(0xFF090C15), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditLabNameDialog = false }) {
                            Text("Cancelar", color = TextSecondary)
                        }
                    },
                    containerColor = VyianoraSurface,
                    titleContentColor = TextPrimary,
                    textContentColor = TextSecondary,
                    shape = RoundedCornerShape(18.dp)
                )
            }
        }
    }
}

@Composable
fun LexiconMainModule(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    categories: List<LabItemEntity>,
    nuclei: List<LabItemEntity>,
    morphology: List<LabItemEntity>,
    modifier: Modifier = Modifier
) {
    var selectedSubMenu by rememberSaveable { mutableStateOf(LexiconSubMenu.Nuclei) }

    Column(modifier = modifier.fillMaxSize()) {
        // Selector de 3 Submenús: Núcleos, Morfología, Categorías
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LexiconSubMenu.values().forEach { subMenu ->
                val isSelected = selectedSubMenu == subMenu
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedSubMenu = subMenu },
                    label = { Text(subMenu.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VyianoraPurple.copy(alpha = 0.25f),
                        selectedLabelColor = VyianoraCyan,
                        containerColor = VyianoraSurface,
                        labelColor = TextSecondary
                    ),
                    border = BorderStroke(1.dp, if (isSelected) VyianoraCyan else VyianoraBorder)
                )
            }
        }

        HorizontalDivider(color = VyianoraBorder.copy(alpha = 0.6f))

        when (selectedSubMenu) {
            LexiconSubMenu.Nuclei -> {
                NucleiSubMenu(
                    viewModel = viewModel,
                    repository = repository,
                    userEmail = userEmail,
                    categories = categories,
                    nuclei = nuclei
                )
            }
            LexiconSubMenu.Morphology -> {
                MorphologySubMenu(
                    viewModel = viewModel,
                    repository = repository,
                    userEmail = userEmail,
                    morphology = morphology
                )
            }
            LexiconSubMenu.Categories -> {
                CategoriesSubMenu(
                    viewModel = viewModel,
                    repository = repository,
                    userEmail = userEmail,
                    categories = categories
                )
            }
        }
    }
}

/**
 * Submenú Núcleos (Raíces): Idioma Vyianora, Español, Categoría, Guardar y Autollenar con Aura
 */
@Composable
fun NucleiSubMenu(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    categories: List<LabItemEntity>,
    nuclei: List<LabItemEntity>
) {
    val coroutineScope = rememberCoroutineScope()
    var termLanguage by rememberSaveable { mutableStateOf("") }
    var termSpanish by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(categories.firstOrNull()?.categoryOrType ?: "General") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var isGeneratingByAi by remember { mutableStateOf(false) }
    var auraNucleusSuggestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categories) {
        if (selectedCategory.isBlank() && categories.isNotEmpty()) {
            selectedCategory = categories.first().categoryOrType
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Forjar Nuevo Núcleo / Raíz",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Lista desplegable para seleccionar las categorías
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Categoría de la Raíz:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VyianoraDarkBackground,
                                border = BorderStroke(1.dp, if (categoryDropdownExpanded) VyianoraCyan else VyianoraBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { categoryDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedCategory.ifBlank { "Selecciona una categoría" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selectedCategory.isNotBlank()) TextPrimary else TextSecondary
                                        )
                                        val catDesc = categories.firstOrNull { it.categoryOrType == selectedCategory }?.termSpanish
                                        if (!catDesc.isNullOrBlank()) {
                                            Text(
                                                text = catDesc,
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (categoryDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "Desplegar categorías",
                                        tint = VyianoraCyan
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                modifier = Modifier
                                    .background(VyianoraSurface)
                                    .border(BorderStroke(1.dp, VyianoraBorder), RoundedCornerShape(12.dp))
                            ) {
                                if (categories.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Sin categorías creadas", color = VyianoraGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text("Crea una en la pestaña 'Categorías' o usa General", color = TextSecondary, fontSize = 11.sp)
                                            }
                                        },
                                        onClick = {
                                            selectedCategory = "General"
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                } else {
                                    categories.forEach { cat ->
                                        val isCurrent = selectedCategory == cat.categoryOrType
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = cat.categoryOrType,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp,
                                                        color = if (isCurrent) VyianoraCyan else TextPrimary
                                                    )
                                                    if (cat.termSpanish.isNotBlank()) {
                                                        Text(
                                                            text = cat.termSpanish,
                                                            fontSize = 11.sp,
                                                            color = TextSecondary
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedCategory = cat.categoryOrType
                                                categoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = termLanguage,
                        onValueChange = { termLanguage = it },
                        label = { Text("Cómo se dirá en el idioma (Raíz)") },
                        placeholder = { Text("Ej. Astra, Lumen, Chron, Kael") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = termSpanish,
                        onValueChange = { termSpanish = it },
                        label = { Text("Cómo se dirá en español") },
                        placeholder = { Text("Ej. Luz celestial, Tiempo, Sabiduría") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Botones: Guardar léxico y botón de IA al lado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (termLanguage.isNotBlank() && termSpanish.isNotBlank()) {
                                    coroutineScope.launch {
                                        repository.insertLabItem(
                                            LabItemEntity(
                                                userEmail = userEmail,
                                                itemType = LabItemType.NUCLEUS,
                                                termLanguage = termLanguage.trim(),
                                                termSpanish = termSpanish.trim(),
                                                categoryOrType = selectedCategory.ifBlank { "General" }
                                            )
                                        )
                                        termLanguage = ""
                                        termSpanish = ""
                                        feedbackMessage = "✅ Núcleo guardado en el léxico."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = termLanguage.isNotBlank() && termSpanish.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraPurple)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar Núcleo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                isGeneratingByAi = true
                                feedbackMessage = null
                                coroutineScope.launch {
                                    val catEntity = categories.firstOrNull { it.categoryOrType == selectedCategory }
                                    val catName = selectedCategory.ifBlank { "General" }
                                    val catDesc = catEntity?.termSpanish.orEmpty()

                                    auraNucleusSuggestion = AuraLinguisticEngine.suggestNucleus(
                                        viewModel = viewModel,
                                        categoryName = catName,
                                        categoryDescription = catDesc,
                                        currentLanguage = termLanguage,
                                        currentSpanish = termSpanish
                                    )
                                    isGeneratingByAi = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isGeneratingByAi,
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                        ) {
                            if (isGeneratingByAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF090C15), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF090C15), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Forjar con Aura", color = Color(0xFF090C15), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Tarjeta Interactiva de Sugerencia Previa de Aura (Aplicar / Descartar)
                    if (auraNucleusSuggestion != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraPurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VyianoraCyan.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VyianoraCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Sugerencia de Aura (${selectedCategory.ifBlank { "General" }})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = VyianoraCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Término en el idioma:", fontSize = 11.sp, color = TextSecondary)
                                        Text(auraNucleusSuggestion!!.first, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Traducción al español:", fontSize = 11.sp, color = TextSecondary)
                                        Text(auraNucleusSuggestion!!.second, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VyianoraCyan)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { auraNucleusSuggestion = null },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, VyianoraBorder),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Descartar", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            termLanguage = auraNucleusSuggestion!!.first
                                            termSpanish = auraNucleusSuggestion!!.second
                                            auraNucleusSuggestion = null
                                            feedbackMessage = "✅ Sugerencia aplicada a los campos."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Text("Aplicar", color = Color(0xFF090C15), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (feedbackMessage != null) {
                        Text(
                            text = feedbackMessage!!,
                            color = VyianoraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Núcleos Registrados (${nuclei.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(nuclei) { item ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.termLanguage,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = VyianoraCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VyianoraBorder.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = item.categoryOrType,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = item.termSpanish,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Submenú Morfología: Prefijo o Sufijo, significado en español, Guardar y Autollenar con Aura
 */
@Composable
fun MorphologySubMenu(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    morphology: List<LabItemEntity>
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedType by rememberSaveable { mutableStateOf("Prefijo") }
    var morphemeText by rememberSaveable { mutableStateOf("") }
    var meaningSpanish by rememberSaveable { mutableStateOf("") }
    var isGeneratingByAi by remember { mutableStateOf(false) }
    var auraMorphologySuggestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Crear Modificador Morfológico",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Prefijo", "Sufijo").forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = morphemeText,
                        onValueChange = { morphemeText = it },
                        label = { Text("Morfema") },
                        placeholder = { Text(if (selectedType == "Prefijo") "Ej. Tor-, Kra-, Syn-" else "Ej. -ash, -on, -il, -el") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = meaningSpanish,
                        onValueChange = { meaningSpanish = it },
                        label = { Text("Significado o función en español") },
                        placeholder = { Text("Ej. Fuerza, intensidad, estado o pluralidad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (morphemeText.isNotBlank()) {
                                    coroutineScope.launch {
                                        repository.insertLabItem(
                                            LabItemEntity(
                                                userEmail = userEmail,
                                                itemType = LabItemType.MORPHOLOGY,
                                                termLanguage = morphemeText.trim(),
                                                categoryOrType = selectedType,
                                                meaningLanguage = "",
                                                meaningSpanish = meaningSpanish.trim()
                                            )
                                        )
                                        morphemeText = ""
                                        meaningSpanish = ""
                                        feedbackMessage = "✅ Modificador morfológico guardado."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = morphemeText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraPurple)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                isGeneratingByAi = true
                                feedbackMessage = null
                                coroutineScope.launch {
                                    auraMorphologySuggestion = AuraLinguisticEngine.suggestMorphology(
                                        viewModel = viewModel,
                                        type = selectedType,
                                        currentMorpheme = morphemeText,
                                        currentMeaning = meaningSpanish
                                    )
                                    isGeneratingByAi = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isGeneratingByAi,
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                        ) {
                            if (isGeneratingByAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF090C15), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF090C15), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Forjar con Aura", color = Color(0xFF090C15), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Tarjeta Interactiva de Sugerencia Previa de Aura (Aplicar / Descartar)
                    if (auraMorphologySuggestion != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraPurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VyianoraCyan.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VyianoraCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Sugerencia de Aura ($selectedType)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = VyianoraCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Morfema:", fontSize = 11.sp, color = TextSecondary)
                                        Text(auraMorphologySuggestion!!.first, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Función en español:", fontSize = 11.sp, color = TextSecondary)
                                        Text(auraMorphologySuggestion!!.second, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VyianoraCyan)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { auraMorphologySuggestion = null },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, VyianoraBorder),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Descartar", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            morphemeText = auraMorphologySuggestion!!.first
                                            meaningSpanish = auraMorphologySuggestion!!.second
                                            auraMorphologySuggestion = null
                                            feedbackMessage = "✅ Sugerencia aplicada a los campos."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Text("Aplicar", color = Color(0xFF090C15), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (feedbackMessage != null) {
                        Text(
                            text = feedbackMessage!!,
                            color = VyianoraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Modificadores Registrados (${morphology.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(morphology) { item ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.termLanguage,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = VyianoraCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VyianoraBorder.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = item.categoryOrType,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (item.meaningSpanish.isNotBlank()) Text("Español: ${item.meaningSpanish}", fontSize = 12.sp, color = TextPrimary)
                    }

                    IconButton(
                        onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Submenú Categorías
 */
@Composable
fun CategoriesSubMenu(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    categories: List<LabItemEntity>
) {
    val coroutineScope = rememberCoroutineScope()
    var categoryName by rememberSaveable { mutableStateOf("") }
    var categoryDescription by rememberSaveable { mutableStateOf("") }
    var isGeneratingByAi by remember { mutableStateOf(false) }
    var auraCategorySuggestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Categorías para Núcleos y Raíces de Palabras",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Crea o genera con Aura categorías temáticas donde clasificarás y organizarás los núcleos y raíces del idioma.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Nombre de la categoría") },
                        placeholder = { Text("Ej. Guerra y Estrategia, Naturaleza, Cosmos, Mística") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = categoryDescription,
                        onValueChange = { categoryDescription = it },
                        label = { Text("Contexto temático para núcleos y raíces") },
                        placeholder = { Text("Ej. Raíces para armas, combate, asedio, defensa y honor marcial") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (categoryName.isNotBlank()) {
                                    coroutineScope.launch {
                                        repository.insertLabItem(
                                            LabItemEntity(
                                                userEmail = userEmail,
                                                itemType = LabItemType.CATEGORY,
                                                categoryOrType = categoryName.trim(),
                                                termSpanish = categoryDescription.trim()
                                            )
                                        )
                                        categoryName = ""
                                        categoryDescription = ""
                                        feedbackMessage = "✅ Categoría de núcleos registrada."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = categoryName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraPurple)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                isGeneratingByAi = true
                                feedbackMessage = null
                                coroutineScope.launch {
                                    auraCategorySuggestion = AuraLinguisticEngine.suggestCategory(
                                        viewModel = viewModel,
                                        currentName = categoryName,
                                        currentDesc = categoryDescription
                                    )
                                    isGeneratingByAi = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isGeneratingByAi,
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                        ) {
                            if (isGeneratingByAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF090C15), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF090C15), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sugerir con Aura", color = Color(0xFF090C15), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Tarjeta Interactiva de Sugerencia Previa de Aura (Aplicar / Descartar)
                    if (auraCategorySuggestion != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraPurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VyianoraCyan.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VyianoraCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Sugerencia de Aura (Categoría)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = VyianoraCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Categoría:", fontSize = 11.sp, color = TextSecondary)
                                        Text(auraCategorySuggestion!!.first, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text("Contexto:", fontSize = 11.sp, color = TextSecondary)
                                        Text(auraCategorySuggestion!!.second, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = VyianoraCyan)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { auraCategorySuggestion = null },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, VyianoraBorder),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Descartar", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            categoryName = auraCategorySuggestion!!.first
                                            categoryDescription = auraCategorySuggestion!!.second
                                            auraCategorySuggestion = null
                                            feedbackMessage = "✅ Sugerencia aplicada a los campos."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Text("Aplicar", color = Color(0xFF090C15), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (feedbackMessage != null) {
                        Text(
                            text = feedbackMessage!!,
                            color = VyianoraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Categorías Guardadas (${categories.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(categories) { item ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = item.categoryOrType,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = VyianoraCyan
                        )
                        if (item.termSpanish.isNotBlank()) {
                            Text(
                                text = item.termSpanish,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * 2. MENÚ GENERADOR (Constructor de Morfemas con síntesis rápida por Aura)
 */
@Composable
fun GeneratorMorphemesModule(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    nuclei: List<LabItemEntity>,
    morphology: List<LabItemEntity>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val prefixes = remember(morphology) { morphology.filter { it.categoryOrType.equals("Prefijo", ignoreCase = true) } }
    val suffixes = remember(morphology) { morphology.filter { it.categoryOrType.equals("Sufijo", ignoreCase = true) } }

    var selectedPrefix by remember { mutableStateOf<LabItemEntity?>(null) }
    var selectedNucleus by remember { mutableStateOf<LabItemEntity?>(null) }
    var selectedSuffix by remember { mutableStateOf<LabItemEntity?>(null) }

    var generatedWordText by rememberSaveable { mutableStateOf("") }
    var aiSpanishMeaning by rememberSaveable { mutableStateOf("") }
    var isGeneratingMeaning by remember { mutableStateOf(false) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    fun triggerWordAssembly(p: LabItemEntity?, n: LabItemEntity?, s: LabItemEntity?) {
        val pClean = p?.termLanguage?.replace("-", "") ?: ""
        val nClean = n?.termLanguage?.replace("-", "") ?: ""
        val sClean = s?.termLanguage?.replace("-", "") ?: ""

        val assembled = (pClean + nClean + sClean).trim()
        generatedWordText = assembled

        if (assembled.isNotBlank()) {
            isGeneratingMeaning = true
            coroutineScope.launch {
                aiSpanishMeaning = AuraLinguisticEngine.interpretAssembledWord(
                    viewModel = viewModel,
                    prefixTerm = p?.termLanguage,
                    prefixMeaning = p?.meaningSpanish?.ifBlank { p.termSpanish },
                    nucleusTerm = n?.termLanguage,
                    nucleusMeaning = n?.termSpanish?.ifBlank { n.meaningSpanish },
                    suffixTerm = s?.termLanguage,
                    suffixMeaning = s?.meaningSpanish?.ifBlank { s.termSpanish },
                    assembledWord = assembled
                )
                isGeneratingMeaning = false
            }
        } else {
            aiSpanishMeaning = ""
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Generador de Morfemas",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Selecciona entre prefijos, núcleos y sufijos desde las listas desplegables. Aura deducirá automáticamente su significado coherente en español.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Visor de la Palabra Forjada
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(VyianoraCyan, VyianoraPurple))),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PALABRA FORJADA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyianoraCyan,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (generatedWordText.isNotBlank()) generatedWordText else "(Selecciona en los desplegables abajo)",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (generatedWordText.isNotBlank()) Color.White else TextSecondary.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isGeneratingMeaning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = VyianoraCyan, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aura interpretando en español...", fontSize = 12.sp, color = VyianoraCyan)
                        }
                    } else if (aiSpanishMeaning.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VyianoraPurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VyianoraPurple.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Español: $aiSpanishMeaning",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (generatedWordText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val componentsList = listOfNotNull(
                                            selectedPrefix?.termLanguage,
                                            selectedNucleus?.termLanguage,
                                            selectedSuffix?.termLanguage
                                        ).joinToString(" + ")

                                        repository.insertLabItem(
                                            LabItemEntity(
                                                userEmail = userEmail,
                                                itemType = LabItemType.WORD,
                                                termLanguage = generatedWordText,
                                                termSpanish = aiSpanishMeaning,
                                                usedComponents = componentsList
                                            )
                                        )
                                        saveSuccessMessage = "✅ Guardada en la Biblioteca."
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color(0xFF090C15), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar en Biblioteca", color = Color(0xFF090C15), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedPrefix = null
                                    selectedNucleus = null
                                    selectedSuffix = null
                                    generatedWordText = ""
                                    aiSpanishMeaning = ""
                                    saveSuccessMessage = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, VyianoraBorder)
                            ) {
                                Text("Reiniciar", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    if (saveSuccessMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = saveSuccessMessage!!, color = VyianoraCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Apartado de Prefijos con Lista Desplegable
        item {
            MorphemeDropdownSection(
                title = "1. Apartado de Prefijos",
                items = prefixes,
                selected = selectedPrefix,
                isOptional = true,
                emptyPrompt = "— Selecciona un Prefijo (Opcional) —",
                onSelected = {
                    selectedPrefix = it
                    triggerWordAssembly(selectedPrefix, selectedNucleus, selectedSuffix)
                }
            )
        }

        // 2. Apartado de Núcleos con Lista Desplegable
        item {
            MorphemeDropdownSection(
                title = "2. Apartado de Núcleos (Raíces)",
                items = nuclei,
                selected = selectedNucleus,
                isOptional = false,
                emptyPrompt = "— Selecciona un Núcleo / Raíz —",
                onSelected = {
                    selectedNucleus = it
                    triggerWordAssembly(selectedPrefix, selectedNucleus, selectedSuffix)
                }
            )
        }

        // 3. Apartado de Sufijos con Lista Desplegable
        item {
            MorphemeDropdownSection(
                title = "3. Apartado de Sufijos",
                items = suffixes,
                selected = selectedSuffix,
                isOptional = true,
                emptyPrompt = "— Selecciona un Sufijo (Opcional) —",
                onSelected = {
                    selectedSuffix = it
                    triggerWordAssembly(selectedPrefix, selectedNucleus, selectedSuffix)
                }
            )
        }
    }
}

/**
 * Componente de Apartado Desplegable para Prefijos, Núcleos y Sufijos con Significado en Español
 */
@Composable
fun MorphemeDropdownSection(
    title: String,
    items: List<LabItemEntity>,
    selected: LabItemEntity?,
    isOptional: Boolean = true,
    emptyPrompt: String,
    onSelected: (LabItemEntity?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VyianoraSurface,
        border = BorderStroke(1.dp, if (selected != null) VyianoraCyan.copy(alpha = 0.7f) else VyianoraBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (selected != null && isOptional) {
                    TextButton(
                        onClick = { onSelected(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Quitar", fontSize = 11.sp, color = VyianoraRose)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VyianoraDarkBackground,
                    border = BorderStroke(1.dp, if (expanded) VyianoraCyan else VyianoraBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (selected != null) {
                                Text(
                                    text = selected.termLanguage,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = VyianoraCyan
                                )
                                val desc = selected.meaningSpanish.ifBlank { selected.termSpanish }
                                if (desc.isNotBlank()) {
                                    Text(
                                        text = "Significado: $desc",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Text(
                                    text = emptyPrompt,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Desplegar opciones",
                            tint = VyianoraCyan
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(VyianoraSurface)
                        .border(BorderStroke(1.dp, VyianoraBorder), RoundedCornerShape(12.dp))
                ) {
                    if (isOptional) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("— Ninguno (Omitir) —", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                                    Text("No incluir este modificador en la palabra", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
                                }
                            },
                            onClick = {
                                onSelected(null)
                                expanded = false
                            }
                        )
                    }

                    if (items.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text("No hay elementos registrados en el léxico", fontSize = 12.sp, color = VyianoraGold)
                            },
                            onClick = { expanded = false }
                        )
                    } else {
                        items.forEach { item ->
                            val isCurrent = selected?.id == item.id
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.termLanguage,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isCurrent) VyianoraCyan else TextPrimary
                                            )
                                            if (item.categoryOrType.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = VyianoraBorder.copy(alpha = 0.6f)
                                                ) {
                                                    Text(
                                                        text = item.categoryOrType,
                                                        fontSize = 9.sp,
                                                        color = TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        val desc = item.meaningSpanish.ifBlank { item.termSpanish }
                                        if (desc.isNotBlank()) {
                                            Text(
                                                text = "Significado: $desc",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelected(item)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. MENÚ FRASES O PALABRAS (Traductor y Constructor de Frases)
 * Filtrador por tipo, alfabético y fecha. Constructor de morfología con Aura.
 * Muestra núcleos usados, significado literal, contextual de la IA y Grado de Poder (1-10 con Insignia).
 */
@Composable
fun PhrasesModule(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    nuclei: List<LabItemEntity>,
    morphology: List<LabItemEntity>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var phraseInput by rememberSaveable { mutableStateOf("") }
    var filterType by rememberSaveable { mutableStateOf("Todos") }
    var filterOrder by rememberSaveable { mutableStateOf(FilterOrder.Recent) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var isAnalyzingWithAura by remember { mutableStateOf(false) }
    var analyzedNucleiUsed by rememberSaveable { mutableStateOf("") }
    var analyzedLiteralMeaning by rememberSaveable { mutableStateOf("") }
    var analyzedContextualMeaning by rememberSaveable { mutableStateOf("") }
    var analyzedPowerLevel by rememberSaveable { mutableStateOf(1) }
    var analyzedPowerBadge by rememberSaveable { mutableStateOf("") }
    var analyzedStructure by rememberSaveable { mutableStateOf("") }
    var phraseSaveMessage by remember { mutableStateOf<String?>(null) }

    val availableComponents = remember(nuclei, morphology, filterType, filterOrder, searchQuery) {
        val baseList = when (filterType) {
            "Núcleos" -> nuclei
            "Morfemas" -> morphology
            else -> nuclei + morphology
        }
        val searched = if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.termLanguage.contains(searchQuery, ignoreCase = true) ||
                it.termSpanish.contains(searchQuery, ignoreCase = true) ||
                it.meaningSpanish.contains(searchQuery, ignoreCase = true)
            }
        }
        when (filterOrder) {
            FilterOrder.Alphabetical -> searched.sortedBy { it.termLanguage.lowercase() }
            FilterOrder.Recent -> searched.sortedByDescending { it.createdAt }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Generador de Frases",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Escribe o compone frases en la lengua que estás creando. Aura analizará su morfología, el significado literal y su contexto, junto con el Grado de Poder (1-10).",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Espacio para la Frase Creada
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Frase en el idioma:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        placeholder = { Text("Toca elementos del léxico abajo o escribe aquí...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Botón para construir morfología con Aura
                    Button(
                        onClick = {
                            if (phraseInput.isNotBlank()) {
                                isAnalyzingWithAura = true
                                phraseSaveMessage = null
                                coroutineScope.launch {
                                    val analysis = AuraLinguisticEngine.analyzePhrase(
                                        viewModel = viewModel,
                                        phrase = phraseInput,
                                        glossary = nuclei.map { Pair(it.termLanguage, it.termSpanish) }
                                    )
                                    analyzedNucleiUsed = analysis.nucleiUsed
                                    analyzedLiteralMeaning = analysis.literalMeaning
                                    analyzedContextualMeaning = analysis.contextualMeaning
                                    analyzedPowerLevel = analysis.powerLevel
                                    analyzedPowerBadge = analysis.powerBadge
                                    analyzedStructure = analysis.structure
                                    isAnalyzingWithAura = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = phraseInput.isNotBlank() && !isAnalyzingWithAura,
                        colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                    ) {
                        if (isAnalyzingWithAura) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF090C15), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aura analizando la frase...", color = Color(0xFF090C15), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF090C15))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Construir Morfología con Aura", color = Color(0xFF090C15), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tarjeta de Análisis de Aura con Grado de Poder
        if (analyzedLiteralMeaning.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = VyianoraSurface,
                    border = BorderStroke(1.5.dp, VyianoraPurple.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ANÁLISIS MORFOLÓGICO Y PODER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VyianoraCyan,
                                letterSpacing = 1.sp
                            )

                            // Insignia y Grado de Poder
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    analyzedPowerLevel >= 9 -> Color(0xFF381E72)
                                    analyzedPowerLevel >= 7 -> Color(0xFF2C244A)
                                    analyzedPowerLevel >= 5 -> Color(0xFF1E283F)
                                    else -> Color(0xFF1A2234)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (analyzedPowerLevel >= 7) VyianoraGold else VyianoraCyan
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = if (analyzedPowerLevel >= 7) VyianoraGold else VyianoraCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Grado: $analyzedPowerLevel/10 • $analyzedPowerBadge",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (analyzedPowerLevel >= 7) VyianoraGold else VyianoraCyan
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = VyianoraBorder)

                        Text("Estructura: $analyzedStructure", fontSize = 12.sp, color = TextSecondary)
                        Text("Núcleos usados: $analyzedNucleiUsed", fontSize = 13.sp, color = TextPrimary)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VyianoraDarkBackground.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Significado Literal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Text(analyzedLiteralMeaning, fontSize = 13.sp, color = TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Significado Contextual (Aura):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyianoraCyan)
                                Text(analyzedContextualMeaning, fontSize = 13.sp, color = TextPrimary)
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    repository.insertLabItem(
                                        LabItemEntity(
                                            userEmail = userEmail,
                                            itemType = LabItemType.PHRASE,
                                            termLanguage = phraseInput.trim(),
                                            literalMeaning = analyzedLiteralMeaning,
                                            contextualMeaning = analyzedContextualMeaning,
                                            powerLevel = analyzedPowerLevel,
                                            powerBadge = analyzedPowerBadge,
                                            usedComponents = analyzedNucleiUsed,
                                            grammaticalStructure = analyzedStructure
                                        )
                                    )
                                    phraseSaveMessage = "✅ Frase guardada en la Biblioteca."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VyianoraPurple)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar Frase en Biblioteca", fontWeight = FontWeight.Bold)
                        }

                        if (phraseSaveMessage != null) {
                            Text(text = phraseSaveMessage!!, color = VyianoraCyan, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Explorador y Filtrador
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Explorar Léxico Creado",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar núcleos o modificadores...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Todos", "Núcleos", "Morfemas").forEach { type ->
                            FilterChip(
                                selected = filterType == type,
                                onClick = { filterType = type },
                                label = { Text(type, fontSize = 11.sp) }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            filterOrder = if (filterOrder == FilterOrder.Alphabetical) FilterOrder.Recent else FilterOrder.Alphabetical
                        }
                    ) {
                        Icon(
                            imageVector = if (filterOrder == FilterOrder.Alphabetical) Icons.Default.SortByAlpha else Icons.Default.AccessTime,
                            contentDescription = "Cambiar orden",
                            tint = VyianoraCyan
                        )
                    }
                }
            }
        }

        items(availableComponents) { comp ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val wordToAdd = comp.termLanguage.replace("-", "")
                        phraseInput = if (phraseInput.isBlank()) wordToAdd else "$phraseInput $wordToAdd"
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comp.termLanguage, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VyianoraCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VyianoraBorder.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = if (comp.itemType == LabItemType.NUCLEUS) "Núcleo" else comp.categoryOrType,
                                    fontSize = 9.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val esp = if (comp.termSpanish.isNotBlank()) comp.termSpanish else comp.meaningSpanish
                        Text(esp, fontSize = 11.sp, color = TextPrimary)
                    }

                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Añadir a frase", tint = VyianoraCyan)
                }
            }
        }
    }
}

/**
 * 4. MENÚ BIBLIOTECA (Organizado en pestañas dedicadas)
 */
@Composable
fun LibraryLabModule(
    repository: CreationLabRepository,
    nuclei: List<LabItemEntity>,
    morphology: List<LabItemEntity>,
    words: List<LabItemEntity>,
    phrases: List<LabItemEntity>,
    onOpenWritingCanvas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableStateOf(LibrarySubTab.Nuclei) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = VyianoraSurface,
            contentColor = VyianoraCyan,
            edgePadding = 12.dp,
            divider = { HorizontalDivider(color = VyianoraBorder) }
        ) {
            LibrarySubTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val count = when (tab) {
                    LibrarySubTab.Nuclei -> nuclei.size
                    LibrarySubTab.Morphology -> morphology.size
                    LibrarySubTab.Words -> words.size
                    LibrarySubTab.Phrases -> phrases.size
                    LibrarySubTab.WritingCanvas -> 0
                }
                Tab(
                    selected = isSelected,
                    onClick = {
                        if (tab == LibrarySubTab.WritingCanvas) {
                            onOpenWritingCanvas()
                        } else {
                            selectedTab = tab
                        }
                    },
                    text = {
                        Text(
                            text = if (tab == LibrarySubTab.WritingCanvas) "Lienzo de escritura" else "${tab.label} ($count)",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (isSelected) VyianoraCyan else TextSecondary
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                LibrarySubTab.Nuclei -> {
                    if (nuclei.isEmpty()) {
                        item { EmptyLibraryPlaceholder("No hay núcleos guardados en la biblioteca.") }
                    }
                    items(nuclei) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.termLanguage, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VyianoraCyan)
                                    Text("Español: ${item.termSpanish}", fontSize = 13.sp, color = TextPrimary)
                                    Text("Dominio: ${item.categoryOrType}", fontSize = 11.sp, color = TextSecondary)
                                }
                                Row {
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString("${item.termLanguage}: ${item.termSpanish}")) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                LibrarySubTab.Morphology -> {
                    if (morphology.isEmpty()) {
                        item { EmptyLibraryPlaceholder("No hay morfología guardada en la biblioteca.") }
                    }
                    items(morphology) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.termLanguage, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VyianoraCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(6.dp), color = VyianoraBorder) {
                                            Text(item.categoryOrType, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    if (item.meaningSpanish.isNotBlank()) Text("Español: ${item.meaningSpanish}", fontSize = 12.sp, color = TextPrimary)
                                }
                                Row {
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString("${item.termLanguage}: ${item.meaningSpanish}")) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                LibrarySubTab.Words -> {
                    if (words.isEmpty()) {
                        item { EmptyLibraryPlaceholder("No hay palabras forjadas guardadas aún.") }
                    }
                    items(words) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.termLanguage, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VyianoraCyan)
                                    Text("Significado: ${item.termSpanish}", fontSize = 13.sp, color = TextPrimary)
                                    if (item.usedComponents.isNotBlank()) {
                                        Text("Composición: ${item.usedComponents}", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                                Row {
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString("${item.termLanguage}: ${item.termSpanish}")) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                LibrarySubTab.Phrases -> {
                    if (phrases.isEmpty()) {
                        item { EmptyLibraryPlaceholder("No hay frases guardadas en la biblioteca.") }
                    }
                    items(phrases) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.termLanguage,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = VyianoraCyan
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            item.powerLevel >= 9 -> Color(0xFF381E72)
                                            item.powerLevel >= 7 -> Color(0xFF2C244A)
                                            item.powerLevel >= 5 -> Color(0xFF1E283F)
                                            else -> Color(0xFF1A2234)
                                        },
                                        border = BorderStroke(1.dp, if (item.powerLevel >= 7) VyianoraGold else VyianoraCyan)
                                    ) {
                                        Text(
                                            text = "Poder: ${item.powerLevel}/10 • ${item.powerBadge}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.powerLevel >= 7) VyianoraGold else VyianoraCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text("Literal: ${item.literalMeaning}", fontSize = 12.sp, color = TextPrimary)
                                Text("Aura Contexto: ${item.contextualMeaning}", fontSize = 12.sp, color = TextSecondary)
                                if (item.usedComponents.isNotBlank()) {
                                    Text("Núcleos usados: ${item.usedComponents}", fontSize = 11.sp, color = VyianoraPurple)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = {
                                        clipboardManager.setText(
                                            AnnotatedString("${item.termLanguage}\nSignificado: ${item.contextualMeaning}\nPoder: ${item.powerLevel}/10 (${item.powerBadge})")
                                        )
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar frase", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { coroutineScope.launch { repository.deleteLabItem(item) } }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                LibrarySubTab.WritingCanvas -> {
                    // Controlado por el click listener
                }
            }
        }
    }
}

/**
 * 5. LIENZO DE ESCRITURA (Canvas interactivo para trazar glifos, símbolos o caracteres)
 */
@Composable
fun WritingCanvasDialog(
    viewModel: AetherViewModel,
    repository: CreationLabRepository,
    userEmail: String,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val paths = remember { mutableStateListOf<DrawnPath>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(VyianoraCyan) }
    var strokeWidth by remember { mutableStateOf(7f) }
    var glyphName by remember { mutableStateOf("") }
    var auraInterpretation by remember { mutableStateOf<String?>(null) }
    var isInterpreting by remember { mutableStateOf(false) }

    val palette = listOf(
        VyianoraCyan,
        VyianoraPurple,
        VyianoraGold,
        VyianoraRose,
        Color.White
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = VyianoraSurface,
            border = BorderStroke(1.5.dp, VyianoraCyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header del Diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Lienzo de Escritura", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VyianoraCyan)
                        Text("Dibuja trazos, glifos o ideogramas con tu dedo", fontSize = 11.sp, color = TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                // Canvas interactivo con cuadrícula de caligrafía
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, VyianoraBorder, RoundedCornerShape(16.dp)),
                    color = Color(0xFF07090F)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(selectedColor, strokeWidth) {
                                    detectDragGestures(
                                        onDragStart = { offset -> currentPoints = listOf(offset) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPoints = currentPoints + change.position
                                        },
                                        onDragEnd = {
                                            if (currentPoints.isNotEmpty()) {
                                                paths.add(DrawnPath(currentPoints, selectedColor, strokeWidth))
                                                currentPoints = emptyList()
                                            }
                                        },
                                        onDragCancel = { currentPoints = emptyList() }
                                    )
                                }
                        ) {
                            // Cuadrícula caligráfica tenue
                            val step = 36.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(color = Color.White.copy(alpha = 0.03f), start = Offset(x, 0f), end = Offset(x, size.height))
                                x += step
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(color = Color.White.copy(alpha = 0.03f), start = Offset(0f, y), end = Offset(size.width, y))
                                y += step
                            }

                            // Trazos grabados
                            paths.forEach { drawnPath ->
                                if (drawnPath.points.size > 1) {
                                    val p = Path().apply {
                                        moveTo(drawnPath.points.first().x, drawnPath.points.first().y)
                                        for (i in 1 until drawnPath.points.size) {
                                            lineTo(drawnPath.points[i].x, drawnPath.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = p,
                                        color = drawnPath.color,
                                        style = Stroke(width = drawnPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }

                            // Trazo activo
                            if (currentPoints.size > 1) {
                                val curPath = Path().apply {
                                    moveTo(currentPoints.first().x, currentPoints.first().y)
                                    for (i in 1 until currentPoints.size) {
                                        lineTo(currentPoints[i].x, currentPoints[i].y)
                                    }
                                }
                                drawPath(
                                    path = curPath,
                                    color = selectedColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        if (paths.isEmpty() && currentPoints.isEmpty()) {
                            Text(
                                text = "Traza aquí tu glifo o símbolo...",
                                color = TextSecondary.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // Paleta de colores, deshacer y limpiar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEach { color ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) }, enabled = paths.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer", tint = if (paths.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.3f))
                        }
                        IconButton(onClick = { paths.clear() }, enabled = paths.isNotEmpty()) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Limpiar", tint = if (paths.isNotEmpty()) VyianoraRose else TextSecondary.copy(alpha = 0.3f))
                        }
                    }
                }

                // Formulario de Nombre e Interpretación de Aura
                OutlinedTextField(
                    value = glyphName,
                    onValueChange = { glyphName = it },
                    label = { Text("Nombre del Glifo o Símbolo") },
                    placeholder = { Text("Ej. Glifo del Amanecer, Runa de Fuerza") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                isInterpreting = true
                                coroutineScope.launch {
                                    val prompt = """
                                        Eres Aura, asistente rápida del laboratorio lingüístico.
                                        El usuario dibujó el símbolo o carácter "${glyphName.ifBlank { "Glifo Simbólico" }}".
                                        Interpreta su posible significado directo y conciso en español (máximo 8 palabras).
                                    """.trimIndent()
                                    try {
                                        auraInterpretation = viewModel.sendMessage(prompt)?.trim()?.removePrefix("\"")?.removeSuffix("\"")
                                    } catch (e: Exception) {
                                        auraInterpretation = "Carácter simbólico y representativo."
                                    } finally {
                                        isInterpreting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = paths.isNotEmpty() && !isInterpreting,
                        colors = ButtonDefaults.buttonColors(containerColor = VyianoraPurple)
                    ) {
                        if (isInterpreting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Interpretar con Aura", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                coroutineScope.launch {
                                    repository.insertLabItem(
                                        LabItemEntity(
                                            userEmail = userEmail,
                                            itemType = LabItemType.WORD,
                                            termLanguage = glyphName.ifBlank { "Glifo #${System.currentTimeMillis() % 1000}" },
                                            termSpanish = auraInterpretation ?: "Glifo grabado en el lienzo de escritura",
                                            categoryOrType = "Glifo de Escritura"
                                        )
                                    )
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = paths.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = VyianoraCyan)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF090C15), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Glifo", color = Color(0xFF090C15), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (auraInterpretation != null) {
                    Text(
                        text = "Aura: $auraInterpretation",
                        color = VyianoraCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inbox, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

