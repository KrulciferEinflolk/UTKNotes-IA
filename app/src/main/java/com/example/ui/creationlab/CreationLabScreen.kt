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

// Paleta de Estilo Vyianora Translate
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
    Vyianji("Glifos Vyianji")
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
    var showVyianjiCanvasDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vyianora_creation_lab"),
        containerColor = VyianoraDarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VyianoraSurface)
                    .statusBarsPadding()
            ) {
                // Header estilo Vyianora Translate
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
                                    text = "Vyianora",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = VyianoraCyan
                                )
                                Text(
                                    text = " • Laboratorio",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
                                text = "Léxico, morfología, frases y caracteres Vyianji",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botón Lienzo Vyianji
                        FilledTonalButton(
                            onClick = { showVyianjiCanvasDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VyianoraPurple.copy(alpha = 0.2f),
                                contentColor = VyianoraPurple
                            ),
                            border = BorderStroke(1.dp, VyianoraPurple.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vyianji", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        onOpenVyianjiCanvas = { showVyianjiCanvasDialog = true }
                    )
                }
            }

            // Diálogo Modal Lienzo Vyianji (Vyianji Canvas)
            if (showVyianjiCanvasDialog) {
                VyianjiCanvasDialog(
                    viewModel = viewModel,
                    repository = repository,
                    userEmail = safeUserEmail,
                    onDismiss = { showVyianjiCanvasDialog = false }
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
    var isCanonicalMode by rememberSaveable { mutableStateOf(true) }
    var selectedSubMenu by rememberSaveable { mutableStateOf(LexiconSubMenu.Nuclei) }

    Column(modifier = modifier.fillMaxSize()) {
        // Alternador de Modo: Glosario Canónico vs Creación Libre
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VyianoraSurface,
                border = BorderStroke(1.dp, VyianoraBorder)
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    FilledTonalButton(
                        onClick = { isCanonicalMode = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isCanonicalMode) VyianoraPurple else Color.Transparent,
                            contentColor = if (isCanonicalMode) Color.White else TextSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("★ Canónico Vyio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { isCanonicalMode = false },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (!isCanonicalMode) VyianoraCyan else Color.Transparent,
                            contentColor = if (!isCanonicalMode) Color(0xFF090C15) else TextSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✎ Creación Libre", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = if (isCanonicalMode) "Bases Oficiales" else "Términos Creados",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        HorizontalDivider(color = VyianoraBorder.copy(alpha = 0.6f))

        if (isCanonicalMode) {
            // Visor del Glosario Canónico Oficial de Vyianóra
            CanonicalGlossaryViewer()
        } else {
            // Modo Creación Libre con sus 3 submenús
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
}

/**
 * Visor del Glosario Canónico Oficial de Vyianóra (9 Raíces base, 11 Modificadores y 5 Categorías)
 */
@Composable
fun CanonicalGlossaryViewer(modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    var canonicalTab by rememberSaveable { mutableStateOf(0) } // 0: Raíces, 1: Modificadores, 2: Reglas

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Regla absoluta banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VyianoraPurple.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, VyianoraPurple.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VyianoraCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\"Una palabra no describe… domina el concepto.\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VyianoraCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("9 Raíces Base", "11 Modificadores", "Reglas Fonéticas").forEachIndexed { index, title ->
                val isSelected = canonicalTab == index
                FilterChip(
                    selected = isSelected,
                    onClick = { canonicalTab = index },
                    label = { Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (canonicalTab) {
                0 -> {
                    items(VyianoraMasterData.canonicalRoots) { r ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
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
                                        Text(r.root, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VyianoraCyan)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = VyianoraBorder.copy(alpha = 0.6f)
                                        ) {
                                            Text(r.category, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(r.meaningSpanish, fontSize = 13.sp, color = TextPrimary)
                                    Text(r.conceptualDomain, fontSize = 11.sp, color = TextSecondary)
                                }

                                IconButton(onClick = { clipboardManager.setText(AnnotatedString("${r.root}: ${r.meaningSpanish}")) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                1 -> {
                    items(VyianoraMasterData.canonicalModifiers) { m ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
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
                                        Text(m.affix, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VyianoraPurple)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = VyianoraBorder.copy(alpha = 0.6f)
                                        ) {
                                            Text(m.type, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(m.meaningSpanish, fontSize = 13.sp, color = TextPrimary)
                                    Text(m.semanticImpact, fontSize = 11.sp, color = TextSecondary)
                                }

                                IconButton(onClick = { clipboardManager.setText(AnnotatedString("${m.affix}: ${m.meaningSpanish}")) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = VyianoraSurface,
                            border = BorderStroke(1.dp, VyianoraBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Leyes Fonéticas de Vyianóra", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VyianoraGold)
                                Text("1. Terminación obligatoria: Siempre debe terminar en vocal o 'n'.", fontSize = 12.sp, color = TextPrimary)
                                Text("2. Longitud máxima: Máximo 3 sílabas idealmente para mantener la fuerza.", fontSize = 12.sp, color = TextPrimary)
                                Text("3. Evitar choques duros: No emplear combinaciones ásperas como 'kt' o 'tsr'.", fontSize = 12.sp, color = TextPrimary)
                                Text("4. Clases de palabras: Sustantivos (raíz + en), Verbos (raíz pura), Adjetivos (raíz + yo).", fontSize = 12.sp, color = TextPrimary)
                                Text("5. Sintaxis de frases: [Sujeto] + [Concepto] + [Acción] (ej. Mi shenryo nara).", fontSize = 12.sp, color = VyianoraCyan)
                            }
                        }
                    }
                }
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
    var selectedCategory by rememberSaveable { mutableStateOf(categories.firstOrNull()?.categoryOrType ?: "Cosmos & Éter") }
    var isGeneratingByAi by remember { mutableStateOf(false) }
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
                        text = "Forjar Nuevo Núcleo / Raíz de Vyianora",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text("Categoría / Dominio Arcano:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    if (categories.isEmpty()) {
                        Text("No hay categorías creadas. Agrega una en la pestaña Categorías.", fontSize = 12.sp, color = VyianoraCyan)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat.categoryOrType
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat.categoryOrType },
                                    label = { Text(cat.categoryOrType, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = termLanguage,
                        onValueChange = { termLanguage = it },
                        label = { Text("Cómo se dirá en Vyianora (Raíz)") },
                        placeholder = { Text("Ej. Vyia, Astra, Lumen, Chron, Kael") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = termSpanish,
                        onValueChange = { termSpanish = it },
                        label = { Text("Cómo se dirá en español") },
                        placeholder = { Text("Ej. Vida consciente, Luz celestial, Tiempo") },
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
                                        feedbackMessage = "✅ Núcleo guardado en el léxico de Vyianora."
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
                                    val prompt = """
                                        Eres Aura Kioko, compañera e IA lingüística de Vyianora Translate.
                                        Genera una raíz o núcleo armónico para la lengua Vyianora perteneciente a la categoría "$selectedCategory".
                                        Los términos de Vyianora poseen armonía mística, estética etérea y sonoridad cósmica (ejemplos: Vyia, Nora, Kael, Zeth, Lumen, Astrix, Chron, Vesper).
                                        Responde ÚNICAMENTE en JSON estricto sin markdown:
                                        {"idioma": "palabra_vyianora", "espanol": "traduccion_espanol"}
                                    """.trimIndent()
                                    try {
                                        val response = viewModel.sendMessage(prompt)
                                        if (!response.isNullOrBlank()) {
                                            val clean = response.substringAfter("{").substringBeforeLast("}")
                                            val json = JSONObject("{$clean}")
                                            termLanguage = json.optString("idioma", "Zethra")
                                            termSpanish = json.optString("espanol", "Esencia celestial")
                                            feedbackMessage = "✨ Aura forjó el núcleo de Vyianora para $selectedCategory."
                                        }
                                    } catch (e: Exception) {
                                        termLanguage = "Aethra"
                                        termSpanish = "Destello del éter"
                                        feedbackMessage = "✨ Aura forjó sugerencia de raíz."
                                    } finally {
                                        isGeneratingByAi = false
                                    }
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
                text = "Núcleos de Vyianora (${nuclei.size})",
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
 * Submenú Morfología: Prefijo o Sufijo, significados en Vyianora y español, Guardar y Autollenar con Aura
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
    var meaningLanguage by rememberSaveable { mutableStateOf("") }
    var meaningSpanish by rememberSaveable { mutableStateOf("") }
    var isGeneratingByAi by remember { mutableStateOf(false) }
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
                        text = "Crear Modificador Morfológico (Vyianora)",
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
                        placeholder = { Text(if (selectedType == "Prefijo") "Ej. Vyi-, Aeth-, Syn-" else "Ej. -anora, -kji, -alia, -ix") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = meaningLanguage,
                        onValueChange = { meaningLanguage = it },
                        label = { Text("Significado en Vyianora") },
                        placeholder = { Text("Ej. Trascendencia sagrada, aliento cósmico") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = meaningSpanish,
                        onValueChange = { meaningSpanish = it },
                        label = { Text("Significado en español") },
                        placeholder = { Text("Ej. Sagrado, celeste, manifestación") },
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
                                                meaningLanguage = meaningLanguage.trim(),
                                                meaningSpanish = meaningSpanish.trim()
                                            )
                                        )
                                        morphemeText = ""
                                        meaningLanguage = ""
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
                                    val prompt = """
                                        Eres Aura Kioko de Vyianora Translate. Diseña un $selectedType morfológico para la lengua Vyianora.
                                        Debe evocar el misterio y la armonía de Vyianora (ejemplos: Vyi-, Aeth-, Kji-, -anora, -kji, -alia, -ix, -um).
                                        Responde ÚNICAMENTE en JSON estricto sin markdown:
                                        {"morfema": "ejemplo-", "significado_vyianora": "definicion_vyianora", "significado_espanol": "traduccion_espanol"}
                                    """.trimIndent()
                                    try {
                                        val response = viewModel.sendMessage(prompt)
                                        if (!response.isNullOrBlank()) {
                                            val clean = response.substringAfter("{").substringBeforeLast("}")
                                            val json = JSONObject("{$clean}")
                                            morphemeText = json.optString("morfema", if (selectedType == "Prefijo") "Vyi-" else "-anora")
                                            meaningLanguage = json.optString("significado_vyianora", "Cántico sagrado")
                                            meaningSpanish = json.optString("significado_espanol", "Lengua o plegaria de")
                                            feedbackMessage = "✨ Aura generó el modificador automáticamente."
                                        }
                                    } catch (e: Exception) {
                                        morphemeText = if (selectedType == "Prefijo") "Kaela-" else "-kji"
                                        meaningLanguage = "Símbolo ancestral grabado"
                                        meaningSpanish = "Runa o glifo eterno"
                                        feedbackMessage = "✨ Aura sugirió morfema."
                                    } finally {
                                        isGeneratingByAi = false
                                    }
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
                                Text("Canalizar con Aura", color = Color(0xFF090C15), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        if (item.meaningLanguage.isNotBlank()) Text("Vyianora: ${item.meaningLanguage}", fontSize = 11.sp, color = TextSecondary)
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
                        text = "Dominios y Categorías de Vyianora",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Nombre del dominio") },
                        placeholder = { Text("Ej. Cosmos & Éter, Esencia Espiritual, Runas Arcanas") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = categoryDescription,
                        onValueChange = { categoryDescription = it },
                        label = { Text("Descripción o ámbito temático") },
                        placeholder = { Text("Ej. Conceptos vinculados a las entidades celestes de Vyianora") },
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
                                        feedbackMessage = "✅ Dominio registrado."
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
                                    val prompt = """
                                        Eres Aura Kioko de Vyianora Translate. Sugiere un dominio o categoría arquetípica fascinante para la cosmología y lengua de Vyianora.
                                        Responde ÚNICAMENTE en JSON estricto sin markdown:
                                        {"categoria": "nombre_dominio", "descripcion": "descripcion_dominio"}
                                    """.trimIndent()
                                    try {
                                        val response = viewModel.sendMessage(prompt)
                                        if (!response.isNullOrBlank()) {
                                            val clean = response.substringAfter("{").substringBeforeLast("}")
                                            val json = JSONObject("{$clean}")
                                            categoryName = json.optString("categoria", "Fuerza del Éter Primordial")
                                            categoryDescription = json.optString("descripcion", "Morfemas que gobiernan el vacío y la resonancia estelar")
                                            feedbackMessage = "✨ Aura reveló una nueva categoría de Vyianora."
                                        }
                                    } catch (e: Exception) {
                                        categoryName = "Runas de Orión"
                                        categoryDescription = "Glifos dedicados a las constelaciones protectoras"
                                        feedbackMessage = "✨ Aura sugirió dominio."
                                    } finally {
                                        isGeneratingByAi = false
                                    }
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
                                Text("Revelar con Aura", color = Color(0xFF090C15), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
 * 2. MENÚ GENERADOR (Constructor de Morfemas al estilo Vyianora con significado automático por Aura)
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
                val prompt = """
                    Eres Aura Kioko, compañera e intérprete del sistema lingüístico Vyianora Translate.
                    El usuario ha forjado una nueva palabra en Vyianora combinando:
                    - Prefijo: "${p?.termLanguage ?: "ninguno"}" (Significado: ${p?.meaningSpanish ?: "N/A"})
                    - Núcleo: "${n?.termLanguage ?: "ninguno"}" (Significado: ${n?.termSpanish ?: "N/A"})
                    - Sufijo: "${s?.termLanguage ?: "ninguno"}" (Significado: ${s?.meaningSpanish ?: "N/A"})
                    Palabra forjada en Vyianora: "$assembled"
                    
                    Determina su traducción y significado conciso en español respetando la esencia mística de Vyianora. Responde solo con el significado (máximo 12 palabras, en español).
                """.trimIndent()
                try {
                    val resp = viewModel.sendMessage(prompt)
                    aiSpanishMeaning = resp?.trim()?.removePrefix("\"")?.removeSuffix("\"") ?: "Significado sintetizado en Vyianora"
                } catch (e: Exception) {
                    val fallbackMeaning = listOfNotNull(p?.meaningSpanish, n?.termSpanish, s?.meaningSpanish).joinToString(" de ")
                    aiSpanishMeaning = fallbackMeaning.ifBlank { "Esencia primordial de Vyianora" }
                } finally {
                    isGeneratingMeaning = false
                }
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
                text = "Generador de Morfemas • Vyianora",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Combina prefijos, raíces y sufijos para forjar nuevos términos en Vyianora. Aura deducirá automáticamente su significado en español.",
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
                        text = "PALABRA FORJADA EN VYIANORA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyianoraCyan,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (generatedWordText.isNotBlank()) generatedWordText else "(Elige modificadores abajo)",
                        fontSize = 30.sp,
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
                                        saveSuccessMessage = "✅ Guardada en la Biblioteca de Vyianora."
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

        item {
            MorphemeChoiceSection(
                title = "1. Prefijo de Vyianora (Opcional)",
                items = prefixes,
                selected = selectedPrefix,
                onSelected = {
                    selectedPrefix = if (selectedPrefix == it) null else it
                    triggerWordAssembly(selectedPrefix, selectedNucleus, selectedSuffix)
                }
            )
        }

        item {
            MorphemeChoiceSection(
                title = "2. Núcleo / Raíz Base (Vyianora)",
                items = nuclei,
                selected = selectedNucleus,
                onSelected = {
                    selectedNucleus = if (selectedNucleus == it) null else it
                    triggerWordAssembly(selectedPrefix, selectedNucleus, selectedSuffix)
                }
            )
        }

        item {
            MorphemeChoiceSection(
                title = "3. Sufijo de Vyianora (Opcional)",
                items = suffixes,
                selected = selectedSuffix,
                onSelected = {
                    selectedSuffix = if (selectedSuffix == it) null else it
                    triggerWordAssembly(selectedPrefix, selectedNucleus, selectedSuffix)
                }
            )
        }
    }
}

@Composable
fun MorphemeChoiceSection(
    title: String,
    items: List<LabItemEntity>,
    selected: LabItemEntity?,
    onSelected: (LabItemEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (items.isEmpty()) {
            Text("No hay elementos registrados.", fontSize = 12.sp, color = TextSecondary)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { item ->
                    val isSelected = selected == item
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) VyianoraPurple.copy(alpha = 0.25f) else VyianoraSurface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) VyianoraCyan else VyianoraBorder
                        ),
                        modifier = Modifier.clickable { onSelected(item) }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                            Text(
                                text = item.termLanguage,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) VyianoraCyan else TextPrimary
                            )
                            val desc = if (item.termSpanish.isNotBlank()) item.termSpanish else item.meaningSpanish
                            if (desc.isNotBlank()) {
                                Text(
                                    text = desc,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. MENÚ FRASES O PALABRAS (Vyianora Translate)
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
                text = "Generador de Frases • Vyianora",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Escribe o compone frases en la lengua Vyianora. Aura analizará su morfología, el significado literal y su contexto místico, junto con el Grado de Poder (1-10).",
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
                        text = "Frase en Vyianora:",
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
                                    val knownGlossary = nuclei.joinToString("; ") { "${it.termLanguage}=${it.termSpanish}" }
                                    val prompt = """
                                        Eres Aura Kioko, compañera e intérprete del universo Vyianora Translate.
                                        Analiza la siguiente frase construida en la lengua sagrada Vyianora:
                                        Frase: "$phraseInput"
                                        Glosario conocido: $knownGlossary
                                        
                                        Calcula la morfología, significado literal y significado contextual. Determina el "grado_poder" (entero del 1 al 10) y su correspondiente "insignia_poder".
                                        Escala de poder de Vyianora:
                                        1-2: Insignia Terrenal
                                        3-4: Insignia Resonante
                                        5-6: Insignia Arcana
                                        7-8: Insignia Ancestral Éter
                                        9-10: Insignia Cósmica Suprema
                                        
                                        Responde ÚNICAMENTE en JSON estricto sin markdown:
                                        {
                                          "nucleos_usados": "desglose de raices y morfemas usados con significado",
                                          "significado_literal": "traduccion palabra por palabra",
                                          "significado_contextual": "interpretacion conceptual profunda y poetica de Vyianora",
                                          "grado_poder": 8,
                                          "insignia_poder": "Insignia Ancestral Éter",
                                          "estructura": "Sujeto + Cántico + Verbo Trascendental"
                                        }
                                    """.trimIndent()
                                    try {
                                        val response = viewModel.sendMessage(prompt)
                                        if (!response.isNullOrBlank()) {
                                            val clean = response.substringAfter("{").substringBeforeLast("}")
                                            val json = JSONObject("{$clean}")
                                            analyzedNucleiUsed = json.optString("nucleos_usados", "Raíces de Vyianora detectadas")
                                            analyzedLiteralMeaning = json.optString("significado_literal", phraseInput)
                                            analyzedContextualMeaning = json.optString("significado_contextual", "Plegaria mística de resonancia cósmica")
                                            analyzedPowerLevel = json.optInt("grado_poder", 8).coerceIn(1, 10)
                                            analyzedPowerBadge = json.optString("insignia_poder", "Insignia Arcana")
                                            analyzedStructure = json.optString("estructura", "Sintaxis Vyianora Clásica")
                                        }
                                    } catch (e: Exception) {
                                        analyzedNucleiUsed = "Raíces identificadas: ${phraseInput.take(25)}"
                                        analyzedLiteralMeaning = "Traducción directa de los términos"
                                        analyzedContextualMeaning = "Sentencia armónica que sintoniza con el cosmos de Vyianora"
                                        analyzedPowerLevel = 7
                                        analyzedPowerBadge = "Insignia Ancestral Éter"
                                        analyzedStructure = "Orden SOV Místico"
                                    } finally {
                                        isAnalyzingWithAura = false
                                    }
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
                            Text("Aura analizando en Vyianora...", color = Color(0xFF090C15), fontWeight = FontWeight.Bold)
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
                                text = "ANÁLISIS MORFOLÓGICO VYIANORA",
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
                                    phraseSaveMessage = "✅ Frase guardada en la Biblioteca de Vyianora."
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
                    text = "Explorar Léxico de Vyianora",
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
 * 4. MENÚ BIBLIOTECA (Organizado en pestañas dedicadas estilo Vyianora Translate)
 */
@Composable
fun LibraryLabModule(
    repository: CreationLabRepository,
    nuclei: List<LabItemEntity>,
    morphology: List<LabItemEntity>,
    words: List<LabItemEntity>,
    phrases: List<LabItemEntity>,
    onOpenVyianjiCanvas: () -> Unit,
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
                    LibrarySubTab.Vyianji -> 0
                }
                Tab(
                    selected = isSelected,
                    onClick = {
                        if (tab == LibrarySubTab.Vyianji) {
                            onOpenVyianjiCanvas()
                        } else {
                            selectedTab = tab
                        }
                    },
                    text = {
                        Text(
                            text = if (tab == LibrarySubTab.Vyianji) "Lienzo Vyianji" else "${tab.label} ($count)",
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
                                    if (item.meaningLanguage.isNotBlank()) Text("Vyianora: ${item.meaningLanguage}", fontSize = 11.sp, color = TextSecondary)
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
                        item { EmptyLibraryPlaceholder("No hay frases de Vyianora guardadas.") }
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
                LibrarySubTab.Vyianji -> {
                    // Controlado por el click listener
                }
            }
        }
    }
}

/**
 * 5. LIENZO VYIANJI (Vyianji Canvas - Inspirado en VyianjiCanvas.jsx de Vyianora Translate)
 */
@Composable
fun VyianjiCanvasDialog(
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
                        Text("Lienzo Vyianji (Carácter Sagrado)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VyianoraCyan)
                        Text("Dibuja trazos ideográficos de Vyianora con tu dedo", fontSize = 11.sp, color = TextSecondary)
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
                            // Cuadrícula caligráfica tenue estilo Vyianji
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
                                text = "Traza aquí el ideograma Vyianji...",
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
                    label = { Text("Nombre del Carácter Vyianji") },
                    placeholder = { Text("Ej. Vyianji de la Sabiduría, Runa de Nora") },
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
                                        Eres Aura Kioko de Vyianora Translate.
                                        El usuario ha dibujado el carácter ideográfico Vyianji "${glyphName.ifBlank { "Glifo Cósmico" }}".
                                        Interpreta su resonancia en una frase corta y mística (máximo 14 palabras).
                                    """.trimIndent()
                                    try {
                                        auraInterpretation = viewModel.sendMessage(prompt)
                                    } catch (e: Exception) {
                                        auraInterpretation = "Carácter sagrado que canaliza la luz de Vyianora."
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
                                            termLanguage = glyphName.ifBlank { "Vyianji #${System.currentTimeMillis() % 1000}" },
                                            termSpanish = auraInterpretation ?: "Glifo grabado en el lienzo Vyianji",
                                            categoryOrType = "Glifo Vyianji"
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

