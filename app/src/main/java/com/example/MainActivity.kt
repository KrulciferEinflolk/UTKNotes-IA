package com.example

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.remote.SyncState
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.local.llm.LlmModelState
import com.example.ui.AetherViewModel
import com.example.ui.LibraryMainScreen
import com.example.ui.UTKNotesWelcomeScreen
import com.example.ui.HexColorBox
import com.example.ui.HexColorPickerDialog
import com.example.ui.parseHexColorSafe
import com.example.ui.colorToHex
import com.example.ui.graphicElement2SecondHold
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: AetherViewModel by viewModels()

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val showChatbotGlobal by viewModel.showChatbot.collectAsStateWithLifecycle()

                val recoveryIntent by viewModel.syncManager.recoveryIntent.collectAsStateWithLifecycle()
                val recoveryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    viewModel.syncManager.clearRecoveryIntent()
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        viewModel.triggerDriveSync()
                    }
                }

                LaunchedEffect(recoveryIntent) {
                    recoveryIntent?.let { intent ->
                        try {
                            recoveryLauncher.launch(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to launch recovery intent", e)
                            viewModel.syncManager.clearRecoveryIntent()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = CosmicBackground
                    ) { innerPadding ->
                        val selectedBook by viewModel.selectedBook.collectAsStateWithLifecycle()
                        val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()
                        val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
                        val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()

                        if (!isConnected) {
                            UTKNotesWelcomeScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else if (selectedBook == null) {
                            LibraryMainScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else if (selectedNote != null) {
                            androidx.activity.compose.BackHandler {
                                viewModel.selectNote(null)
                            }
                            NoteEditorWorkspace(
                                note = selectedNote!!,
                                aiLoading = aiLoading,
                                onDismiss = { viewModel.selectNote(null) },
                                onSave = { viewModel.saveNote(it) },
                                onAiModify = { instruction ->
                                    viewModel.applyAiModificationToNote(selectedNote!!, instruction)
                                },
                                onOpenChatbot = { citation ->
                                    viewModel.openChatbot(selectedNote, citation)
                                },
                                syncManager = viewModel.syncManager
                            )
                        } else {
                            androidx.activity.compose.BackHandler {
                                viewModel.clearSelectedBook()
                            }
                            AetherAppScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }

                    // Bottom Sheet Slider for Chatbot that slides up from the bottom (without touching the very top)
                    // and can be swiped down to close while background AI processing continues.
                    if (showChatbotGlobal) {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = { viewModel.closeChatbot() },
                            sheetState = sheetState,
                            containerColor = CosmicBackground,
                            dragHandle = {
                                BottomSheetDefaults.DragHandle(
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.88f)
                                    .background(CosmicBackground)
                            ) {
                                ChatbotUI(
                                    viewModel = viewModel,
                                    onDismiss = { viewModel.closeChatbot() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherAppScreen(
    viewModel: AetherViewModel,
    modifier: Modifier = Modifier
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val selectedBook by viewModel.selectedBook.collectAsStateWithLifecycle()
    val selectedPage by viewModel.selectedPage.collectAsStateWithLifecycle()
    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val syncState by viewModel.syncManager.syncState.collectAsStateWithLifecycle()
    val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()
    val userEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()

    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiSuggestions by viewModel.aiSuggestions.collectAsStateWithLifecycle()
    val aiMessage by viewModel.aiMessage.collectAsStateWithLifecycle()

    // Drawer state
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Drawer removed per user request

    // Dialog state
    var showAddBookDialog by remember { mutableStateOf(false) }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAiCustomDialog by remember { mutableStateOf(false) }
    var aiCustomPrompt by remember { mutableStateOf("") }

    var isImportingPdf by remember { mutableStateOf(false) }
    val pdfPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            isImportingPdf = true
            scope.launch {
                try {
                    val originalName = getFileName(context, uri) ?: "Documento"
                    val sizeString = getFileSize(context, uri) ?: "Desconocido"
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    
                    val cleanName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val destFile = java.io.File(
                        java.io.File(context.filesDir, "imported_pdfs").apply { mkdirs() },
                        "${System.currentTimeMillis()}_$cleanName"
                    )
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val isPdf = originalName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"
                    val isTextFile = isSupportedTextExtension(originalName, mimeType)

                    val isImageFile = mimeType.startsWith("image/", ignoreCase = true) ||
                                      originalName.endsWith(".png", ignoreCase = true) ||
                                      originalName.endsWith(".jpg", ignoreCase = true) ||
                                      originalName.endsWith(".jpeg", ignoreCase = true) ||
                                      originalName.endsWith(".webp", ignoreCase = true) ||
                                      originalName.endsWith(".gif", ignoreCase = true)

                    val blocks = mutableListOf<EditorBlock>()

                    if (isPdf) {
                        var totalElementsExtracted = 0
                        val imageDir = java.io.File(context.filesDir, "extracted_pdf_images").apply { mkdirs() }
                        try {
                            val reader = com.itextpdf.text.pdf.PdfReader(destFile.absolutePath)
                            val numPages = reader.numberOfPages
                            val parser = com.itextpdf.text.pdf.parser.PdfReaderContentParser(reader)
                            for (page in 1..numPages) {
                                val extractor = PageElementExtractor(context, imageDir, totalElementsExtracted)
                                parser.processContent(page, extractor)
                                val pageBlocks = processPageElements(extractor.elements)
                                if (pageBlocks.isNotEmpty()) {
                                    totalElementsExtracted += extractor.elements.size
                                    blocks.addAll(pageBlocks)
                                }
                            }
                            reader.close()
                        } catch (e: Exception) {
                            android.util.Log.e("PDFImport", "Error extracting PDF", e)
                        }
                        if (totalElementsExtracted == 0) {
                            blocks.add(
                                EditorBlock.Text(
                                    content = "No se pudo extraer texto legible ni imágenes de las páginas de este PDF.",
                                    isItalic = true,
                                    fontSize = 13,
                                    fontColor = "Red"
                                )
                            )
                        }
                    } else if (isTextFile) {
                        val textContent = try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                input.bufferedReader().use { it.readText() }
                            } ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        
                        blocks.add(
                            EditorBlock.Text(
                                content = "Archivo Importado: $originalName",
                                isBold = true,
                                fontSize = 20,
                                isHeader = true
                            )
                        )
                        blocks.add(
                            EditorBlock.File(
                                name = originalName,
                                sourceUrl = destFile.absolutePath,
                                size = sizeString
                            )
                        )
                        
                        if (textContent.isNotEmpty()) {
                            blocks.addAll(parseTextContentToBlocks(textContent))
                        } else {
                            blocks.add(
                                EditorBlock.Text(
                                    content = "(Archivo de texto vacío)",
                                    isItalic = true,
                                    fontSize = 13,
                                    fontColor = "Normal"
                                )
                            )
                        }
                    } else if (isImageFile) {
                        blocks.add(
                            EditorBlock.Text(
                                content = "Imagen Importada: $originalName",
                                isBold = true,
                                fontSize = 18,
                                isHeader = true
                            )
                        )
                        blocks.add(
                            EditorBlock.Image(
                                urlOrPath = destFile.absolutePath,
                                caption = originalName,
                                width = "Match",
                                height = "Wrap"
                            )
                        )
                        blocks.add(
                            EditorBlock.File(
                                name = originalName,
                                sourceUrl = destFile.absolutePath,
                                size = sizeString
                            )
                        )
                    } else {
                        // General file
                        blocks.add(
                            EditorBlock.Text(
                                content = "Archivo Importado: $originalName",
                                isBold = true,
                                fontSize = 18,
                                isHeader = true
                            )
                        )
                        blocks.add(
                            EditorBlock.File(
                                name = originalName,
                                sourceUrl = destFile.absolutePath,
                                size = sizeString
                            )
                        )
                        blocks.add(
                            EditorBlock.Text(
                                content = "Puedes pulsar prolongadamente el archivo de arriba para cambiar su configuración, o tocarlo para abrirlo.",
                                isItalic = true,
                                fontSize = 13,
                                fontColor = "Normal"
                            )
                        )
                    }
                    
                    val serialized = serializeBlocks(blocks)
                    
                    val titleWithoutExtension = if (originalName.contains(".")) {
                        originalName.substringBeforeLast(".")
                    } else {
                        originalName
                    }
                    
                    viewModel.addNote(
                        title = titleWithoutExtension,
                        content = serialized,
                        tags = if (isPdf) "PDF, Importado" else if (isTextFile) "Texto, Importado" else "Archivo, Importado"
                    )
                    
                    Toast.makeText(context, "Archivo importado correctamente: $originalName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.util.Log.e("PDFImport", "Error importing file", e)
                    Toast.makeText(context, "Error al importar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isImportingPdf = false
                }
            }
        }
    }

    if (isImportingPdf) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSurface,
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = GeminiBlue)
                    Text("Importando archivo...", color = TextPrimary, fontSize = 16.sp)
                }
            }
        }
    }

    if (showAiCustomDialog) {
        var selectedNoteToContinue by remember { mutableStateOf<NoteEntity?>(notes.firstOrNull()) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAiCustomDialog = false },
            containerColor = CosmicSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GeminiBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Sugerencia de Gemini",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Display the suggestion
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                        border = BorderStroke(1.dp, GeminiBlue.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = GeminiCyanAccent,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = aiCustomPrompt,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Text(
                        "¿Cómo te gustaría aplicar esta idea?",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Selection for note to continue
                    if (notes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Continuar o modificar nota existente:",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dropdownExpanded = true }
                                    .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                    .border(1.dp, CosmicBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedNoteToContinue?.title ?: "Seleccionar una nota",
                                        color = if (selectedNoteToContinue != null) TextPrimary else TextTertiary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextPrimary
                                    )
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .background(CosmicSurface)
                                        .border(1.dp, CosmicBorder)
                                        .width(280.dp)
                                ) {
                                    notes.forEach { note ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    note.title,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            onClick = {
                                                selectedNoteToContinue = note
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.openChatbot(preAttachedText = aiCustomPrompt)
                            showAiCustomDialog = false
                        }
                    ) {
                        Text("Chat", color = GeminiCyanAccent, fontSize = 13.sp)
                    }

                    if (notes.isNotEmpty() && selectedNoteToContinue != null) {
                        Button(
                            onClick = {
                                val targetNote = selectedNoteToContinue!!
                                viewModel.applyAiModificationToNote(
                                    note = targetNote,
                                    instruction = "Modifica o continúa esta nota aplicando la siguiente idea o sugerencia de manera natural y detallada: $aiCustomPrompt"
                                )
                                showAiCustomDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicBorder)
                        ) {
                            Text("Continuar nota", color = TextPrimary, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.createNoteWithAI(aiCustomPrompt)
                            showAiCustomDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                    ) {
                        Text("Crear nota", color = GeminiOnPrimary, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAiCustomDialog = false }
                ) {
                    Text("Cancelar", color = TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }

    // Toast-like message for AI
    LaunchedEffect(aiMessage) {
        aiMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearAiMessage()
        }
    }


        // Main Screen Scaffold Content
        Scaffold(
            topBar = {
                AetherTopBar(
                    selectedBook = selectedBook,
                    selectedPage = selectedPage,
                    searchQuery = searchQuery,
                    syncState = syncState,
                    userEmail = userEmail,
                    isConnected = isConnected,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onBack = { viewModel.clearSelectedBook() },
                    onSyncClick = { viewModel.triggerDriveSync() },
                    onChatClick = { viewModel.openChatbot() },
                    selectedNote = selectedNote,
                    onImportPdfClick = {
                        if (!isImportingPdf) {
                            try {
                                pdfPickerLauncher.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se pudo abrir el selector de archivos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selectedPage != null && searchQuery.isEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.addNote(title = "Nota Nueva", content = "[]", tags = "")
                        },
                        containerColor = GeminiBlue,
                        contentColor = GeminiOnPrimary,
                        modifier = Modifier.testTag("add_note_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Crear Nota")
                    }
                }
            },
            containerColor = CosmicBackground
        ) { scaffoldPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // AI Suggestion Banner row
                    AISuggestionsHeader(
                        aiLoading = aiLoading,
                        aiSuggestions = aiSuggestions,
                        onGenerateClick = { viewModel.generateAISuggestions() },
                        onSuggestionClick = { suggestion ->
                            aiCustomPrompt = suggestion
                            showAiCustomDialog = true
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        // Search workspace
                        SearchWorkspace(
                            searchResults = searchResults,
                            query = searchQuery,
                            onNoteClick = { viewModel.selectNote(it) }
                        )
                    } else if (selectedPage == null) {
                        // Transient loading state while book content loads
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GeminiBlue)
                        }
                    } else {
                        // Standard Notes Workspace Grid
                        NotesWorkspace(
                            notes = notes,
                            selectedPage = selectedPage!!,
                            onNoteClick = { viewModel.selectNote(it) },
                            onPinClick = { viewModel.togglePinNote(it) },
                            onDeleteClick = {
                                viewModel.deleteNote(it)
                                Toast.makeText(context, "Nota enviada a la papelera", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // AI Agent Overlay loading indicator
                if (aiLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .width(240.dp)
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = GeminiBlue)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Consultando a Gemini...",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "IA rediseñando y puliendo tu nota",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }
                    }
                }
            }
        }
    }

// --- COMPOSE WIDGET IMPLEMENTATIONS ---

@Composable
fun AetherTopBar(
    selectedBook: BookEntity?,
    selectedPage: PageEntity?,
    searchQuery: String,
    syncState: SyncState,
    userEmail: String?,
    isConnected: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSyncClick: () -> Unit,
    onChatClick: () -> Unit,
    selectedNote: NoteEntity? = null,
    onImportPdfClick: (() -> Unit)? = null
) {
    Surface(
        color = CosmicSurface,
        border = BorderStroke(1.dp, CosmicBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("back_btn")) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Atrás", tint = GeminiBlue)
                        }
                    } else if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick, modifier = Modifier.testTag("menu_drawer_btn")) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú", tint = GeminiBlue)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (selectedBook != null) selectedBook.title else "Aether Notes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }
                }

                // Cloud backup indicator / button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onImportPdfClick != null && selectedPage != null) {
                        IconButton(
                            onClick = onImportPdfClick,
                            modifier = Modifier.testTag("import_pdf_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Importar archivo",
                                tint = GeminiBlue
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    val rotationAnimation = rememberInfiniteTransition()
                    val rotation by rotationAnimation.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    IconButton(onClick = onSyncClick, modifier = Modifier.testTag("sync_drive_btn")) {
                        when (syncState) {
                            is SyncState.Syncing -> {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "Sincronizando",
                                    tint = GeminiCyanAccent,
                                    modifier = Modifier.rotate(rotation)
                                )
                            }
                            is SyncState.Success -> {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = "Respaldado exitosamente en Google Drive",
                                    tint = Color(0xFF81C784)
                                )
                            }
                            is SyncState.Error -> {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = "Error al sincronizar con Drive",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = "Respaldar en la Nube",
                                    tint = TextTertiary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Embedded search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("search_notes_input"),
                placeholder = { Text("Buscar notas, etiquetas, contenidos...", color = TextTertiary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = TextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = CosmicSurfaceVariant,
                    unfocusedContainerColor = CosmicSurfaceVariant,
                    focusedBorderColor = GeminiBlue.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun AISuggestionsHeader(
    aiLoading: Boolean,
    aiSuggestions: List<String>,
    onGenerateClick: () -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    Surface(
        color = Color(0xFF1D192B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "IA Gemini",
                        tint = GeminiBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Asistente Gemini Copilot",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeminiBlue
                    )
                }
                TextButton(
                    onClick = onGenerateClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (aiSuggestions.isEmpty()) "Analizar y Sugerir" else "Actualizar Ideas",
                        fontSize = 11.sp,
                        color = GeminiCyanAccent
                    )
                }
            }

            if (aiSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(aiSuggestions) { suggestion ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                            border = BorderStroke(1.dp, GeminiBlue.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable { onSuggestionClick(suggestion) }
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lightbulb, null, tint = GeminiCyanAccent, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    suggestion,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotesWorkspace(
    notes: List<NoteEntity>,
    selectedPage: PageEntity,
    onNoteClick: (NoteEntity) -> Unit,
    onPinClick: (NoteEntity) -> Unit,
    onDeleteClick: (NoteEntity) -> Unit
) {
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.NoteAlt,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Esta página está vacía",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pulsa el botón '+' abajo a la derecha para crear tu primera nota.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentPadding = PaddingValues(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note) },
                    onPinClick = { onPinClick(note) },
                    onDeleteClick = { onDeleteClick(note) }
                )
            }
        }
    }
}

fun getNoteTextPreview(content: String): String {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) return content
    return try {
        val array = org.json.JSONArray(trimmed)
        val sb = java.lang.StringBuilder()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("type") == "text") {
                val text = obj.optString("content", "")
                if (text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(text)
                }
            }
        }
        val result = sb.toString()
        if (result.isBlank()) "Documento enriquecido (toca para editar)" else result
    } catch (e: Exception) {
        content
    }
}

fun getNoteMediaBadges(content: String): List<String> {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) return emptyList()
    return try {
        val array = org.json.JSONArray(trimmed)
        val badges = mutableListOf<String>()
        var hasTable = false
        var hasImage = false
        var hasAudio = false
        var hasVideo = false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            when (obj.optString("type")) {
                "table" -> hasTable = true
                "image" -> hasImage = true
                "audio" -> hasAudio = true
                "video" -> hasVideo = true
            }
        }
        if (hasTable) badges.add("📊 Tabla")
        if (hasImage) badges.add("📷 Imagen")
        if (hasAudio) badges.add("🎵 Audio")
        if (hasVideo) badges.add("🎬 Video")
        badges
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDeleteClick
            )
            .border(
                width = 1.dp,
                color = if (note.isPinned) GeminiBlue.copy(alpha = 0.5f) else CosmicBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title.ifEmpty { "Sin Título" },
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                            contentDescription = "Fijar",
                            tint = if (note.isPinned) GeminiBlue else TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    var showCardMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showCardMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false },
                            modifier = Modifier.background(CosmicSurface)
                        ) {
                            val cardContext = LocalContext.current
                            DropdownMenuItem(
                                text = { Text("Exportar como PDF", color = TextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    exportNoteToPdf(cardContext, note)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = Color.Red, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = getNoteTextPreview(note.content),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            // Visual badges of blocks if they exist
            val mediaBadges = remember(note.content) { getNoteMediaBadges(note.content) }
            if (mediaBadges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    mediaBadges.forEach { badge ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF211F26))
                                .border(0.5.dp, GeminiBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tags chips inside note
            if (note.tags.isNotBlank()) {
                val tagList = note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tagList) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GeminiBlue.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 9.sp,
                                color = GeminiBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Metadata footer (Reminder and Sync indicator)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Reminder alarm icon
                if (note.reminderTime != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = "Recordatorio programado",
                            tint = GeminiCyanAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = try { formatter.format(Date(note.reminderTime)) } catch (e: Exception) { "" },
                            fontSize = 9.sp,
                            color = GeminiCyanAccent
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Cloud Synced check icon
                if (note.isSynced) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Sincronizado en la nube",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.SyncProblem,
                        contentDescription = "Pendiente de sincronizar",
                        tint = TextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchWorkspace(
    searchResults: List<NoteEntity>,
    query: String,
    onNoteClick: (NoteEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Resultados para '$query'",
            color = GeminiBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No se encontraron notas.", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { note ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(note) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    getNoteTextPreview(note.content),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ArrowForward, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyBookState(
    hasBooks: Boolean,
    onAddBookClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = GeminiBlue,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Aether Notes",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Organiza tu mente en un sistema infinito de Libros, Páginas y Notas.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!hasBooks) {
                Button(
                    onClick = onAddBookClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                ) {
                    Text("Crear mi primer Libro", color = GeminiOnPrimary)
                }
            } else {
                Text(
                    "Desliza desde el borde izquierdo o pulsa el menú arriba para elegir tus libros.",
                    color = GeminiCyanAccent,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AetherInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GeminiBlue)

                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label, color = TextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CosmicSurfaceVariant,
                        unfocusedContainerColor = CosmicSurfaceVariant,
                        focusedIndicatorColor = GeminiBlue
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_input_field")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                    ) {
                        Text("Aceptar", color = GeminiOnPrimary)
                    }
                }
            }
        }
    }
}

// --- RICH EDITOR BLOCK STATE DEFINITIONS ---
sealed class EditorBlock {
    abstract val id: String

    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        var content: String,
        var isBold: Boolean = false,
        var isItalic: Boolean = false,
        var isUnderline: Boolean = false,
        var fontSize: Int = 16, // sp
        var fontColor: String = "Normal", // "Normal", "Purple", "Blue", "Green", "Red", "Amber"
        var fontFamily: String = "Sans", // "Sans", "Serif", "Monospace", "Cursive"
        var alignment: String = "Left", // "Left", "Center", "Right"
        var isBullet: Boolean = false,
        var isNumbered: Boolean = false,
        var isCollapsedHeader: Boolean = false,
        var isCollapsed: Boolean = false,
        var isHeader: Boolean = false
    ) : EditorBlock()

    data class Table(
        override val id: String = UUID.randomUUID().toString(),
        var rows: Int = 3,
        var cols: Int = 3,
        var data: List<List<String>> = List(3) { List(3) { "" } },
        var headerColor: String = "Purple", // "Purple", "Blue", "Slate", "Dark Graphite"
        var borderColor: String = "Border", // "Border", "None"
        var cellColor: String = "Surface",
        var margin: Int = 0,
        var tableWidth: String = "Match",
        var mergedCells: String = ""
    ) : EditorBlock()

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        var urlOrPath: String = "Mantener pulsado para editar",
        var caption: String = "", var width: String = "Match", var height: String = "Wrap"
        
        
    ) : EditorBlock()

    data class Audio(
        override val id: String = UUID.randomUUID().toString(),
        var name: String = "Mantener pulsado para editar",
        var duration: String = "02:30",
        var isPlaying: Boolean = false,
        var sourceUrl: String = ""
    ) : EditorBlock()

    data class Video(
        override val id: String = UUID.randomUUID().toString(),
        var title: String = "Mantener pulsado para editar",
        var length: String = "05:15",
        var sourceUrl: String = "", var width: String = "Match", var height: String = "Wrap"
    ) : EditorBlock()
    data class File(
        override val id: String = java.util.UUID.randomUUID().toString(),
        var name: String = "Mantener pulsado para editar",
        var sourceUrl: String = "",
        var size: String = "Desconocido"
    ) : EditorBlock()

    data class Todo(
        override val id: String = UUID.randomUUID().toString(),
        var content: String = "",
        var isChecked: Boolean = false
    ) : EditorBlock()

    data class Callout(
        override val id: String = UUID.randomUUID().toString(),
        var content: String = "",
        var emoji: String = "💡",
        var colorVariant: String = "Purple",
        var bgColorHex: String? = null,
        var textColorHex: String? = null
    ) : EditorBlock()

    data class Quote(
        override val id: String = UUID.randomUUID().toString(),
        var content: String = ""
    ) : EditorBlock()

    data class Divider(
        override val id: String = UUID.randomUUID().toString(),
        var style: String = "Solid", // "Solid", "Dotted", "Dashed"
        var thickness: Int = 1,
        var colorHex: String? = null
    ) : EditorBlock()

    data class Code(
        override val id: String = UUID.randomUUID().toString(),
        var code: String = "",
        var language: String = "Kotlin",
        var bgColorHex: String? = null,
        var textColorHex: String? = null
    ) : EditorBlock()
}

fun exportNoteToPdf(context: android.content.Context, note: NoteEntity) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        var pageNumber = 1
        
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = android.graphics.Paint()
        
        val margin = 50f
        var yPosition = 60f
        
        // Helper to draw text with word wrap, and handle page breaks
        fun drawTextWithWrap(text: String, size: Float, isBold: Boolean, isItalic: Boolean, color: Int = android.graphics.Color.BLACK, isBullet: Boolean = false) {
            paint.textSize = size
            val tf = when {
                isBold && isItalic -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
                isBold -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isItalic -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                else -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            }
            paint.typeface = tf
            paint.color = color
            
            val maxTextWidth = pageWidth - (margin * 2) - (if (isBullet) 15f else 0f)
            
            // Clean up text
            val cleanText = text.replace("\r", "")
            val paragraphs = cleanText.split("\n")
            
            for (paragraph in paragraphs) {
                if (paragraph.isBlank()) {
                    yPosition += size * 0.5f
                    continue
                }
                
                val words = paragraph.split(" ")
                var currentLine = StringBuilder()
                
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                    val measuredWidth = paint.measureText(testLine)
                    if (measuredWidth > maxTextWidth) {
                        // Check if page needs to be broken
                        if (yPosition + size + 10f > pageHeight - margin) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            yPosition = margin
                        }
                        
                        val drawX = if (isBullet) margin + 15f else margin
                        if (isBullet && currentLine.toString() == words.firstOrNull()) {
                            // draw bullet point
                            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            canvas.drawText("• ", margin, yPosition, paint)
                            paint.typeface = tf
                        }
                        
                        canvas.drawText(currentLine.toString(), drawX, yPosition, paint)
                        yPosition += size + 6f
                        currentLine = StringBuilder(word)
                    } else {
                        currentLine = StringBuilder(testLine)
                    }
                }
                
                if (currentLine.isNotEmpty()) {
                    if (yPosition + size + 10f > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = margin
                    }
                    val drawX = if (isBullet) margin + 15f else margin
                    if (isBullet) {
                        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        canvas.drawText("• ", margin, yPosition, paint)
                        paint.typeface = tf
                    }
                    canvas.drawText(currentLine.toString(), drawX, yPosition, paint)
                    yPosition += size + 8f
                }
            }
        }
        
        // 1. Draw Title
        drawTextWithWrap(note.title.ifEmpty { "Sin Título" }, 22f, isBold = true, isItalic = false)
        yPosition += 10f
        
        // 2. Draw Metadata
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateStr = formatter.format(Date(note.updatedAt))
        drawTextWithWrap("Modificado: $dateStr", 10f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
        
        if (note.tags.isNotBlank()) {
            drawTextWithWrap("Etiquetas: ${note.tags}", 10f, isBold = false, isItalic = false, color = android.graphics.Color.DKGRAY)
        }
        
        yPosition += 15f
        
        // Draw horizontal line separator
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, paint)
        yPosition += 25f
        paint.style = android.graphics.Paint.Style.FILL
        
        // 3. Draw Content Blocks
        val blocks = try {
            parseBlocks(note.content)
        } catch(e: Exception) {
            listOf(EditorBlock.Text(content = note.content))
        }
        
        for (block in blocks) {
            when (block) {
                is EditorBlock.Text -> {
                    val fontSize = block.fontSize.toFloat()
                    val colorHex = when (block.fontColor) {
                        "Purple" -> 0xFF907CFF.toInt()
                        "Blue" -> 0xFF2F80ED.toInt()
                        "Green" -> 0xFF27AE60.toInt()
                        "Red" -> 0xFFEB5757.toInt()
                        "Amber" -> 0xFFF2994A.toInt()
                        else -> if (block.fontColor.startsWith("#")) {
                            try { android.graphics.Color.parseColor(block.fontColor) } catch (e: Exception) { android.graphics.Color.BLACK }
                        } else android.graphics.Color.BLACK
                    }
                    drawTextWithWrap(
                        text = block.content,
                        size = fontSize,
                        isBold = block.isBold || block.isHeader,
                        isItalic = block.isItalic,
                        color = colorHex,
                        isBullet = block.isBullet
                    )
                }
                is EditorBlock.Table -> {
                    drawTextWithWrap("[Tabla]", 12f, isBold = true, isItalic = false, color = android.graphics.Color.DKGRAY)
                    for (row in block.data) {
                        val rowText = row.joinToString(" | ")
                        drawTextWithWrap(rowText, 11f, isBold = false, isItalic = false, color = android.graphics.Color.BLACK)
                    }
                    yPosition += 10f
                }
                is EditorBlock.Image -> {
                    val caption = if (block.caption.isNotEmpty()) block.caption else "Imagen"
                    drawTextWithWrap("[Imagen: $caption]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                }
                is EditorBlock.Audio -> {
                    drawTextWithWrap("[Audio: ${block.name}]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                }
                is EditorBlock.Video -> {
                    drawTextWithWrap("[Video: ${block.title}]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                }
                is EditorBlock.File -> {
                    drawTextWithWrap("[Archivo: ${block.name} (${block.size})]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                }
                is EditorBlock.Todo -> {
                    val box = if (block.isChecked) "[X] " else "[  ] "
                    drawTextWithWrap(box + block.content, 12f, isBold = false, isItalic = block.isChecked, color = if (block.isChecked) android.graphics.Color.GRAY else android.graphics.Color.BLACK)
                }
                is EditorBlock.Callout -> {
                    drawTextWithWrap("${block.emoji} ${block.content}", 12f, isBold = false, isItalic = false, color = android.graphics.Color.DKGRAY)
                    yPosition += 4f
                }
                is EditorBlock.Quote -> {
                    drawTextWithWrap("| \"${block.content}\"", 12f, isBold = false, isItalic = true, color = 0xFF907CFF.toInt())
                }
                is EditorBlock.Divider -> {
                    drawTextWithWrap("----------------------------------------", 10f, isBold = false, isItalic = false, color = android.graphics.Color.LTGRAY)
                }
                is EditorBlock.Code -> {
                    drawTextWithWrap("[${block.language}]\n${block.code}", 10f, isBold = false, isItalic = false, color = android.graphics.Color.DKGRAY)
                }
            }
        }
        
        pdfDocument.finishPage(page)
        
        // Write PDF file to cache directory
        val rawTitle = note.title.ifEmpty { "Sin_Titulo" }
        val fileName = "${rawTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")}_export.pdf"
        val cacheFile = java.io.File(context.cacheDir, fileName)
        val fileOutputStream = java.io.FileOutputStream(cacheFile)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()
        
        // Share PDF via Intent
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, note.title)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(android.content.Intent.createChooser(intent, "Compartir PDF de Nota"))
        Toast.makeText(context, "PDF generado con éxito", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun parseBlocks(content: String): List<EditorBlock> {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) {
        if (trimmed.isBlank()) return listOf(EditorBlock.Text(content = ""))
        return parseTextContentToBlocks(trimmed)
    }
    return try {
        val array = org.json.JSONArray(trimmed)
        val list = mutableListOf<EditorBlock>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val type = obj.optString("type")
            val id = obj.optString("id", UUID.randomUUID().toString())
            when (type) {
                "text" -> {
                    list.add(
                        EditorBlock.Text(
                            id = id,
                            content = obj.optString("content", ""),
                            isBold = obj.optBoolean("isBold", false),
                            isItalic = obj.optBoolean("isItalic", false),
                            isUnderline = obj.optBoolean("isUnderline", false),
                            fontSize = obj.optInt("fontSize", 16),
                            fontColor = obj.optString("fontColor", "Normal"),
                            fontFamily = obj.optString("fontFamily", "Sans"),
                            alignment = obj.optString("alignment", "Left"),
                            isBullet = obj.optBoolean("isBullet", false),
                            isNumbered = obj.optBoolean("isNumbered", false),
                            isCollapsedHeader = obj.optBoolean("isCollapsedHeader", false),
                            isCollapsed = obj.optBoolean("isCollapsed", false),
                            isHeader = obj.optBoolean("isHeader", false)
                        )
                    )
                }
                "table" -> {
                    val rows = obj.optInt("rows", 3)
                    val cols = obj.optInt("cols", 3)
                    val headerColor = obj.optString("headerColor", "Purple")
                    val borderColor = obj.optString("borderColor", "Border")
                    val cellColor = obj.optString("cellColor", "Surface")
                    val margin = obj.optInt("margin", 0)
                    val tableWidth = obj.optString("tableWidth", "Match")
                    val mergedCells = obj.optString("mergedCells", "")
                    
                    val dataArray = obj.optJSONArray("data")
                    val dataList = mutableListOf<List<String>>()
                    if (dataArray != null) {
                        for (r in 0 until rows) {
                            val rowArray = dataArray.optJSONArray(r)
                            val rowCells = mutableListOf<String>()
                            for (c in 0 until cols) {
                                rowCells.add(rowArray?.optString(c, "") ?: "")
                            }
                            dataList.add(rowCells)
                        }
                    } else {
                        for (r in 0 until rows) {
                            dataList.add(List(cols) { "" })
                        }
                    }
                    
                    list.add(
                        EditorBlock.Table(
                            id = id,
                            rows = rows,
                            cols = cols,
                            data = dataList,
                            headerColor = headerColor,
                            borderColor = borderColor,
                            cellColor = cellColor,
                            margin = margin,
                            tableWidth = tableWidth,
                            mergedCells = mergedCells
                        )
                    )
                }
                "image" -> {
                    list.add(
                        EditorBlock.Image(
                            id = id,
                            urlOrPath = obj.optString("urlOrPath", "Mantener pulsado para editar"),
                            caption = obj.optString("caption", ""),
                            width = obj.optString("width", "Match"),
                            height = obj.optString("height", "Wrap")
                        )
                    )
                }
                "audio" -> {
                    list.add(
                        EditorBlock.Audio(
                            id = id,
                            name = obj.optString("name", "Mantener pulsado para editar"),
                            duration = obj.optString("duration", "02:30"),
                            sourceUrl = obj.optString("sourceUrl", "")
                        )
                    )
                }
                "video" -> {
                    list.add(
                        EditorBlock.Video(
                            id = id,
                            title = obj.optString("title", "Mantener pulsado para editar"),
                            length = obj.optString("length", "05:15"),
                            sourceUrl = obj.optString("sourceUrl", ""),
                            width = obj.optString("width", "Match"),
                            height = obj.optString("height", "Wrap")
                        )
                    )
                }
                "file" -> {
                    list.add(
                        EditorBlock.File(
                            id = id,
                            name = obj.optString("name", "Documento"),
                            sourceUrl = obj.optString("sourceUrl", ""),
                            size = obj.optString("size", "Desconocido")
                        )
                    )
                }
                "todo" -> {
                    list.add(
                        EditorBlock.Todo(
                            id = id,
                            content = obj.optString("content", ""),
                            isChecked = obj.optBoolean("isChecked", false)
                        )
                    )
                }
                "callout" -> {
                    list.add(
                        EditorBlock.Callout(
                            id = id,
                            content = obj.optString("content", ""),
                            emoji = obj.optString("emoji", "💡"),
                            colorVariant = obj.optString("colorVariant", "Purple"),
                            bgColorHex = if (obj.has("bgColorHex")) obj.getString("bgColorHex") else null,
                            textColorHex = if (obj.has("textColorHex")) obj.getString("textColorHex") else null
                        )
                    )
                }
                "quote" -> {
                    list.add(
                        EditorBlock.Quote(
                            id = id,
                            content = obj.optString("content", "")
                        )
                    )
                }
                "divider" -> {
                    list.add(
                        EditorBlock.Divider(
                            id = id,
                            style = obj.optString("style", "Solid"),
                            thickness = obj.optInt("thickness", 1),
                            colorHex = if (obj.has("colorHex")) obj.getString("colorHex") else null
                        )
                    )
                }
                "code" -> {
                    list.add(
                        EditorBlock.Code(
                            id = id,
                            code = obj.optString("code", ""),
                            language = obj.optString("language", "Kotlin"),
                            bgColorHex = if (obj.has("bgColorHex")) obj.getString("bgColorHex") else null,
                            textColorHex = if (obj.has("textColorHex")) obj.getString("textColorHex") else null
                        )
                    )
                }
            }
        }
        if (list.isEmpty()) list.add(EditorBlock.Text(content = ""))
        list
    } catch (e: Exception) {
        parseTextContentToBlocks(trimmed)
    }
}

fun serializeBlocks(blocks: List<EditorBlock>): String {
    return try {
        val array = org.json.JSONArray()
        for (block in blocks) {
            val obj = org.json.JSONObject()
            when (block) {
                is EditorBlock.Text -> {
                    obj.put("type", "text")
                    obj.put("id", block.id)
                    obj.put("content", block.content)
                    obj.put("isBold", block.isBold)
                    obj.put("isItalic", block.isItalic)
                    obj.put("isUnderline", block.isUnderline)
                    obj.put("fontSize", block.fontSize)
                    obj.put("fontColor", block.fontColor)
                    obj.put("fontFamily", block.fontFamily)
                    obj.put("alignment", block.alignment)
                    obj.put("isBullet", block.isBullet)
                    obj.put("isNumbered", block.isNumbered)
                    obj.put("isCollapsedHeader", block.isCollapsedHeader)
                    obj.put("isCollapsed", block.isCollapsed)
                    obj.put("isHeader", block.isHeader)
                }
                is EditorBlock.Table -> {
                    obj.put("type", "table")
                    obj.put("id", block.id)
                    obj.put("rows", block.rows)
                    obj.put("cols", block.cols)
                    obj.put("headerColor", block.headerColor)
                    obj.put("borderColor", block.borderColor)
                    obj.put("cellColor", block.cellColor)
                    obj.put("margin", block.margin)
                    obj.put("tableWidth", block.tableWidth)
                    obj.put("mergedCells", block.mergedCells)
                    
                    val dataArray = org.json.JSONArray()
                    for (row in block.data) {
                        val rowArray = org.json.JSONArray()
                        for (cell in row) {
                            rowArray.put(cell)
                        }
                        dataArray.put(rowArray)
                    }
                    obj.put("data", dataArray)
                }
                is EditorBlock.Image -> {
                    obj.put("type", "image")
                    obj.put("id", block.id)
                    obj.put("urlOrPath", block.urlOrPath)
                    obj.put("caption", block.caption)
                    obj.put("width", block.width)
                    obj.put("height", block.height)
                }
                is EditorBlock.Audio -> {
                    obj.put("type", "audio")
                    obj.put("id", block.id)
                    obj.put("name", block.name)
                    obj.put("duration", block.duration)
                    obj.put("sourceUrl", block.sourceUrl)
                }
                is EditorBlock.Video -> {
                    obj.put("type", "video")
                    obj.put("id", block.id)
                    obj.put("title", block.title)
                    obj.put("length", block.length)
                    obj.put("sourceUrl", block.sourceUrl)
                    obj.put("width", block.width)
                    obj.put("height", block.height)
                }
                is EditorBlock.File -> {
                    obj.put("type", "file")
                    obj.put("id", block.id)
                    obj.put("name", block.name)
                    obj.put("sourceUrl", block.sourceUrl)
                    obj.put("size", block.size)
                }
                is EditorBlock.Todo -> {
                    obj.put("type", "todo")
                    obj.put("id", block.id)
                    obj.put("content", block.content)
                    obj.put("isChecked", block.isChecked)
                }
                is EditorBlock.Callout -> {
                    obj.put("type", "callout")
                    obj.put("id", block.id)
                    obj.put("content", block.content)
                    obj.put("emoji", block.emoji)
                    obj.put("colorVariant", block.colorVariant)
                    block.bgColorHex?.let { obj.put("bgColorHex", it) }
                    block.textColorHex?.let { obj.put("textColorHex", it) }
                }
                is EditorBlock.Quote -> {
                    obj.put("type", "quote")
                    obj.put("id", block.id)
                    obj.put("content", block.content)
                }
                is EditorBlock.Divider -> {
                    obj.put("type", "divider")
                    obj.put("id", block.id)
                    obj.put("style", block.style)
                    obj.put("thickness", block.thickness)
                    block.colorHex?.let { obj.put("colorHex", it) }
                }
                is EditorBlock.Code -> {
                    obj.put("type", "code")
                    obj.put("id", block.id)
                    obj.put("code", block.code)
                    obj.put("language", block.language)
                    block.bgColorHex?.let { obj.put("bgColorHex", it) }
                    block.textColorHex?.let { obj.put("textColorHex", it) }
                }
            }
            array.put(obj)
        }
        array.toString()
    } catch (e: Exception) {
        "[]"
    }
}

fun cloneBlocks(blocks: List<EditorBlock>): List<EditorBlock> {
    return blocks.map { block ->
        when (block) {
            is EditorBlock.Text -> block.copy()
            is EditorBlock.Table -> block.copy(data = block.data.map { it.toList() })
            is EditorBlock.Image -> block.copy()
            is EditorBlock.Audio -> block.copy()
            is EditorBlock.Video -> block.copy()
            is EditorBlock.File -> block.copy()
            is EditorBlock.Todo -> block.copy()
            is EditorBlock.Callout -> block.copy()
            is EditorBlock.Quote -> block.copy()
            is EditorBlock.Divider -> block.copy()
            is EditorBlock.Code -> block.copy()
        }
    }
}

@Composable
fun RichFormatToolbar(
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    activeBlock: EditorBlock.Text?,
    onFormatChange: (isBold: Boolean?, isItalic: Boolean?, isUnderline: Boolean?, fontSize: Int?, fontColor: String?, fontFamily: String?, alignment: String?, isBullet: Boolean?, isNumbered: Boolean?, isCollapsedHeader: Boolean?) -> Unit,
    onInsertTable: () -> Unit,
    onInsertImage: () -> Unit,
    onInsertAudio: () -> Unit,
    onInsertVideo: () -> Unit
) {
    Surface(
        color = CosmicSurfaceVariant,
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // History Controls
            item {
                IconButton(onClick = onUndo, enabled = undoEnabled) {
                    Icon(Icons.Default.Undo, "Deshacer", tint = if (undoEnabled) GeminiBlue else TextTertiary)
                }
            }
            item {
                IconButton(onClick = onRedo, enabled = redoEnabled) {
                    Icon(Icons.Default.Redo, "Rehacer", tint = if (redoEnabled) GeminiBlue else TextTertiary)
                }
            }
            
            item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(24.dp)) }

            if (activeBlock != null) {
                // Formatting Toggles
                item {
                    IconToggleButton(
                        checked = activeBlock.isBold,
                        onCheckedChange = { onFormatChange(it, null, null, null, null, null, null, null, null, null) }
                    ) {
                        Icon(Icons.Default.FormatBold, "Negrita", tint = if (activeBlock.isBold) GeminiCyanAccent else TextPrimary)
                    }
                }
                item {
                    IconToggleButton(
                        checked = activeBlock.isItalic,
                        onCheckedChange = { onFormatChange(null, it, null, null, null, null, null, null, null, null) }
                    ) {
                        Icon(Icons.Default.FormatItalic, "Cursiva", tint = if (activeBlock.isItalic) GeminiCyanAccent else TextPrimary)
                    }
                }
                item {
                    IconToggleButton(
                        checked = activeBlock.isUnderline,
                        onCheckedChange = { onFormatChange(null, null, it, null, null, null, null, null, null, null) }
                    ) {
                        Icon(Icons.Default.FormatUnderlined, "Subrayado", tint = if (activeBlock.isUnderline) GeminiCyanAccent else TextPrimary)
                    }
                }

                item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(24.dp)) }

                // Alignments
                item {
                    IconButton(onClick = {
                        val nextAlign = when (activeBlock.alignment) {
                            "Left" -> "Center"
                            "Center" -> "Right"
                            else -> "Left"
                        }
                        onFormatChange(null, null, null, null, null, null, nextAlign, null, null, null)
                    }) {
                        val icon = when (activeBlock.alignment) {
                            "Center" -> Icons.Default.FormatAlignCenter
                            "Right" -> Icons.Default.FormatAlignRight
                            else -> Icons.Default.FormatAlignLeft
                        }
                        Icon(icon, "Alineación", tint = GeminiBlue)
                    }
                }

                item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(24.dp)) }

                // List & Headings
                item {
                    IconToggleButton(
                        checked = activeBlock.isBullet,
                        onCheckedChange = { onFormatChange(null, null, null, null, null, null, null, it, false, null) }
                    ) {
                        Icon(Icons.Default.FormatListBulleted, "Lista Viñetas", tint = if (activeBlock.isBullet) GeminiCyanAccent else TextPrimary)
                    }
                }
                item {
                    IconToggleButton(
                        checked = activeBlock.isNumbered,
                        onCheckedChange = { onFormatChange(null, null, null, null, null, null, null, false, it, null) }
                    ) {
                        Icon(Icons.Default.FormatListNumbered, "Lista Numerada", tint = if (activeBlock.isNumbered) GeminiCyanAccent else TextPrimary)
                    }
                }
                item {
                    IconToggleButton(
                        checked = activeBlock.isCollapsedHeader,
                        onCheckedChange = { onFormatChange(null, null, null, null, null, null, null, null, null, it) }
                    ) {
                        Icon(Icons.Default.UnfoldLess, "Título Contraíble", tint = if (activeBlock.isCollapsedHeader) GeminiCyanAccent else TextPrimary)
                    }
                }

                item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(24.dp)) }

                // Font Family
                item {
                    AssistChip(
                        onClick = {
                            val nextFamily = when (activeBlock.fontFamily) {
                                "Sans" -> "Serif"
                                "Serif" -> "Monospace"
                                "Monospace" -> "Cursive"
                                else -> "Sans"
                            }
                            onFormatChange(null, null, null, null, null, nextFamily, null, null, null, null)
                        },
                        label = { Text(activeBlock.fontFamily, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = GeminiBlue)
                    )
                }

                // Font Size Adjuster
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (activeBlock.fontSize > 10) onFormatChange(null, null, null, activeBlock.fontSize - 2, null, null, null, null, null, null)
                        }) {
                            Text("-", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text("${activeBlock.fontSize}", color = GeminiCyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            if (activeBlock.fontSize < 36) onFormatChange(null, null, null, activeBlock.fontSize + 2, null, null, null, null, null, null)
                        }) {
                            Text("+", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Font Color Palette
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colors = listOf("Normal", "Purple", "Blue", "Green", "Red", "Amber")
                        colors.forEach { colorName ->
                            val colorHex = when (colorName) {
                                "Purple" -> Color(0xFFD0BCFF)
                                "Blue" -> Color(0xFF8AB4F8)
                                "Green" -> Color(0xFF81C784)
                                "Red" -> Color(0xFFE57373)
                                "Amber" -> Color(0xFFFFB74D)
                                else -> TextPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(colorHex)
                                    .border(
                                        width = if (activeBlock.fontColor == colorName) 1.5.dp else 0.dp,
                                        color = if (activeBlock.fontColor == colorName) GeminiCyanAccent else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onFormatChange(null, null, null, null, colorName, null, null, null, null, null)
                                    }
                            )
                        }
                    }
                }
            } else {
                item {
                    Text("Toca un párrafo para formatear", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }

            item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(24.dp)) }

            // INSERTIONS
            item {
                AssistChip(
                    onClick = onInsertTable,
                    label = { Text("+ Tabla", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.GridOn, null, modifier = Modifier.size(12.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = GeminiCyanAccent)
                )
            }
            item {
                AssistChip(
                    onClick = onInsertImage,
                    label = { Text("+ Imagen", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(12.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = GeminiCyanAccent)
                )
            }
            item {
                AssistChip(
                    onClick = onInsertAudio,
                    label = { Text("+ Audio", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(12.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = GeminiCyanAccent)
                )
            }
            item {
                AssistChip(
                    onClick = onInsertVideo,
                    label = { Text("+ Video", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(12.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = GeminiCyanAccent)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellEditDialog(
    cellValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(cellValue) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Editar Celda", fontWeight = FontWeight.Bold, color = GeminiBlue, fontSize = 15.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CosmicSurfaceVariant,
                        unfocusedContainerColor = CosmicSurfaceVariant,
                        focusedIndicatorColor = GeminiBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(text) },
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                    ) {
                        Text("Aceptar", color = GeminiOnPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableBlockView(
    block: EditorBlock.Table,
    onBlockChange: (EditorBlock.Table) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = block.margin.dp)
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(if (block.tableWidth == "Match") 1f else 0.8f)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tabla (${block.rows}×${block.cols}) • Mantén presionado 2s para editar", color = TextTertiary, fontSize = 10.sp)
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Editar tabla", tint = GeminiCyanAccent, modifier = Modifier.size(14.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (block.tableWidth == "Match") 1f else 0.8f)
                .border(
                    width = if (block.borderColor == "None") 0.dp else 1.dp,
                    color = CosmicBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        ) {
            for (r in 0 until block.rows) {
                val headerBg = when (block.headerColor) {
                    "Purple" -> Color(0xFF3B2E5C)
                    "Blue" -> Color(0xFF233B5E)
                    "Green" -> Color(0xFF22422C)
                    "Red" -> Color(0xFF4A2328)
                    "Slate" -> Color(0xFF37474F)
                    "None" -> Color.Transparent
                    else -> if (block.headerColor.startsWith("#")) {
                        parseHexColorSafe(block.headerColor, Color(0xFF3B2E5C))
                    } else Color.Transparent
                }
                
                val isRowHeader = r == 0
                val isZebraRow = r % 2 == 1
                val rowBg = if (isRowHeader && block.headerColor != "None") {
                    headerBg
                } else if (isZebraRow) {
                    Color(0xFF1E1C24)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                ) {
                    for (c in 0 until block.cols) {
                        val cellText = block.data.getOrNull(r)?.getOrNull(c) ?: ""
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (block.borderColor == "None") 0.dp else 0.5.dp,
                                    color = CosmicBorder
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = cellText,
                                onValueChange = { newVal ->
                                    val newData = block.data.mapIndexed { rIdx, row ->
                                        if (rIdx == r) {
                                            row.mapIndexed { cIdx, cell ->
                                                if (cIdx == c) newVal else cell
                                            }
                                        } else row
                                    }
                                    onBlockChange(block.copy(data = newData))
                                },
                                textStyle = TextStyle(
                                    color = if (isRowHeader && block.headerColor != "None") Color.White else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isRowHeader && block.headerColor != "None") FontWeight.Bold else FontWeight.Normal,
                                    textAlign = when (block.cellColor) {
                                        "Center" -> TextAlign.Center
                                        "Right" -> TextAlign.Right
                                        else -> TextAlign.Left
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = when (block.cellColor) {
                                            "Center" -> Alignment.Center
                                            "Right" -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        }
                                    ) {
                                        if (cellText.isEmpty()) {
                                            Text(
                                                text = "...",
                                                color = TextTertiary.copy(alpha = 0.4f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageBlockView(
    block: EditorBlock.Image,
    onBlockChange: (EditorBlock.Image) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    syncManager: com.example.data.remote.DriveSyncManager
) {
    val resolvedUrl = rememberDownloadedUri(block.urlOrPath, syncManager)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val isPlaceholder = block.urlOrPath.isEmpty() || block.urlOrPath == "Mantener pulsado para editar"
            var imageAspectRatio by remember(block.urlOrPath) { mutableStateOf<Float?>(null) }
            
            val mod = Modifier
                .fillMaxWidth(if (block.width == "Match") 1f else 0.5f)
                .let { modifier ->
                    if (isPlaceholder) {
                        modifier.height(if (block.height == "Wrap") 140.dp else 250.dp)
                    } else {
                        if (block.height == "Wrap") {
                            val ratio = imageAspectRatio
                            if (ratio != null) {
                                modifier.aspectRatio(ratio)
                            } else {
                                modifier.wrapContentHeight().heightIn(min = 100.dp)
                            }
                        } else {
                            modifier.height(250.dp)
                        }
                    }
                }
                .clip(RoundedCornerShape(8.dp))
                .graphicElement2SecondHold(
                    onOpenSettings = onOpenSettings
                )
            
            val imageModel: Any = remember(resolvedUrl, block.urlOrPath) {
                val target = if (resolvedUrl.isNotEmpty()) resolvedUrl else block.urlOrPath
                if (target.startsWith("/") || target.startsWith("file://")) {
                    val cleanPath = if (target.startsWith("file://")) target.removePrefix("file://") else target
                    java.io.File(cleanPath)
                } else {
                    target
                }
            }

            Box(contentAlignment = Alignment.Center) {
                if (isPlaceholder) {
                    Box(
                        modifier = mod.background(Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF9B72F3)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Palette, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Mantener pulsado para configurar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    coil.compose.AsyncImage(
                        model = imageModel,
                        contentDescription = block.caption,
                        modifier = mod.background(Color.DarkGray),
                        onSuccess = { state ->
                            val drawable = state.result.drawable
                            val w = drawable.intrinsicWidth
                            val h = drawable.intrinsicHeight
                            if (w > 0 && h > 0) {
                                imageAspectRatio = w.toFloat() / h.toFloat()
                            }
                        },
                        contentScale = if (block.height == "Wrap") {
                            androidx.compose.ui.layout.ContentScale.FillWidth
                        } else {
                            androidx.compose.ui.layout.ContentScale.Crop
                        }
                    )
                }
            }
            
            if (block.caption.isNotEmpty()) {
                Text(
                    text = block.caption,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioBlockView(
    block: EditorBlock.Audio,
    onBlockChange: (EditorBlock.Audio) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    syncManager: com.example.data.remote.DriveSyncManager
) {
    val resolvedUrl = rememberDownloadedUri(block.sourceUrl, syncManager)
    val baseContext = LocalContext.current
    val context = remember(baseContext) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            baseContext.createAttributionContext("media")
        } else {
            baseContext
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    var currentPos by remember { mutableStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(resolvedUrl) {
        var mp: android.media.MediaPlayer? = null
        val urlToUse = if (resolvedUrl.isNotEmpty()) resolvedUrl else block.sourceUrl
        if (urlToUse.isNotEmpty()) {
            try {
                mp = android.media.MediaPlayer().apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                    }
                    setDataSource(context, android.net.Uri.parse(urlToUse))
                    setOnPreparedListener { 
                        duration = it.duration
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPos = 0
                        progress = 0f
                    }
                    prepareAsync()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onDispose {
            mp?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            mediaPlayer?.let { mp ->
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        mp.playbackParams = mp.playbackParams.setSpeed(1.0f)
                    }
                } catch (e: Exception) {}
            }
            mediaPlayer?.start()
            while (isPlaying) {
                mediaPlayer?.let { mp ->
                    if (duration > 0) {
                        currentPos = mp.currentPosition
                        progress = currentPos.toFloat() / duration.toFloat()
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        } else {
            mediaPlayer?.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E1E22)) // Dark gray/black background
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings,
                onClick = {
                    if (block.sourceUrl.isEmpty()) {
                        onOpenSettings()
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (block.sourceUrl.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GeminiBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AudioFile, null, tint = GeminiBlue, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(block.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Sin archivo configurado", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Play button with purple circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF907CFF))
                        .clickable { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Info column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        block.name, 
                        color = Color.White, 
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Slider(
                        value = progress,
                        onValueChange = {
                            progress = it
                            val newPos = (it * duration).toInt()
                            mediaPlayer?.seekTo(newPos)
                            currentPos = newPos
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp), // Compact height
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF907CFF), 
                            activeTrackColor = Color(0xFF907CFF),
                            inactiveTrackColor = Color(0xFF333333)
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format("%d:%02d", currentPos / 1000 / 60, (currentPos / 1000) % 60), 
                            color = Color.Gray, 
                            fontSize = 12.sp
                        )
                        Text(
                            String.format("%d:%02d", duration / 1000 / 60, (duration / 1000) % 60), 
                            color = Color.Gray, 
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoBlockView(
    block: EditorBlock.Video,
    onBlockChange: (EditorBlock.Video) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    syncManager: com.example.data.remote.DriveSyncManager
) {
    val baseContext = LocalContext.current
    val context = remember(baseContext) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            baseContext.createAttributionContext("media")
        } else {
            baseContext
        }
    }
    val resolvedUrl = rememberDownloadedUri(block.sourceUrl, syncManager)
    val urlToUse = if (resolvedUrl.isNotEmpty()) resolvedUrl else block.sourceUrl

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    var currentPos by remember { mutableStateOf(0) }
    var videoView: android.widget.VideoView? by remember { mutableStateOf(null) }
    var isFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, videoView) {
        if (isPlaying) {
            videoView?.start()
            while (isPlaying) {
                videoView?.let { vv ->
                    if (duration > 0) {
                        currentPos = vv.currentPosition
                        progress = currentPos.toFloat() / duration.toFloat()
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        } else {
            videoView?.pause()
        }
    }

    if (isFullScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            var fsIsPlaying by remember { mutableStateOf(isPlaying) }
            var fsProgress by remember { mutableStateOf(0f) }
            var fsDuration by remember { mutableStateOf(duration) }
            var fsCurrentPos by remember { mutableStateOf(currentPos) }
            var fsVideoView: android.widget.VideoView? by remember { mutableStateOf(null) }

            LaunchedEffect(fsIsPlaying, fsVideoView) {
                if (fsIsPlaying) {
                    fsVideoView?.start()
                    while (fsIsPlaying) {
                        fsVideoView?.let { vv ->
                            if (fsDuration > 0) {
                                fsCurrentPos = vv.currentPosition
                                fsProgress = fsCurrentPos.toFloat() / fsDuration.toFloat()
                            }
                        }
                        kotlinx.coroutines.delay(100)
                    }
                } else {
                    fsVideoView?.pause()
                }
            }

            LaunchedEffect(Unit) {
                isPlaying = false
                videoView?.pause()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (urlToUse.isNotEmpty()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            val frameLayout = android.widget.FrameLayout(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            val vv = android.widget.VideoView(ctx).apply {
                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                                setVideoURI(android.net.Uri.parse(urlToUse))
                                setOnPreparedListener { mp ->
                                    fsDuration = mp.duration
                                    seekTo(currentPos)
                                    if (fsIsPlaying) {
                                        start()
                                    }
                                }
                                setOnCompletionListener {
                                    fsIsPlaying = false
                                    fsCurrentPos = 0
                                    fsProgress = 0f
                                    seekTo(0)
                                }
                                fsVideoView = this
                            }
                            frameLayout.addView(vv)
                            frameLayout
                        },
                        update = { },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                        .clickable { fsIsPlaying = !fsIsPlaying }
                ) {
                    IconButton(
                        onClick = {
                            currentPos = fsCurrentPos
                            isPlaying = fsIsPlaying
                            videoView?.seekTo(fsCurrentPos)
                            isFullScreen = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Salir de pantalla completa",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = block.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                    )

                    if (!fsIsPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                                .clickable { fsIsPlaying = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = String.format("%02d:%02d", fsCurrentPos / 1000 / 60, (fsCurrentPos / 1000) % 60),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = fsProgress,
                            onValueChange = {
                                fsProgress = it
                                val newPos = (it * fsDuration).toInt()
                                fsVideoView?.seekTo(newPos)
                                fsCurrentPos = newPos
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = String.format("%02d:%02d", fsDuration / 1000 / 60, (fsDuration / 1000) % 60),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = {
                                currentPos = fsCurrentPos
                                isPlaying = fsIsPlaying
                                videoView?.seekTo(fsCurrentPos)
                                isFullScreen = false
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Minimizar",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (block.width == "Match") 1f else 0.5f)
                    .height(if (block.height == "Wrap") 200.dp else 250.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1C24))
                    .graphicElement2SecondHold(
                        onOpenSettings = onOpenSettings,
                        onClick = {
                            if (block.sourceUrl.isEmpty()) {
                                onOpenSettings()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (block.sourceUrl.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoFile, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(block.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    androidx.compose.runtime.key(urlToUse) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { _ ->
                                val frameLayout = android.widget.FrameLayout(context).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                                val vv = android.widget.VideoView(context).apply {
                                    layoutParams = android.widget.FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.Gravity.CENTER
                                    )
                                    setVideoURI(android.net.Uri.parse(urlToUse))
                                    setOnPreparedListener { mp ->
                                        duration = mp.duration
                                    }
                                    setOnCompletionListener {
                                        isPlaying = false
                                        currentPos = 0
                                        progress = 0f
                                        seekTo(0)
                                    }
                                    videoView = this
                                }
                                frameLayout.addView(vv)
                                frameLayout
                            },
                            update = { view ->
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            )
                            .graphicElement2SecondHold(
                                onOpenSettings = onOpenSettings,
                                onClick = { isPlaying = !isPlaying }
                            )
                    ) {
                        Text(
                            text = block.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        )

                        if (!isPlaying) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.3f))
                                    .clickable { isPlaying = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = String.format("%02d:%02d", currentPos / 1000 / 60, (currentPos / 1000) % 60),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = progress,
                                onValueChange = {
                                    progress = it
                                    val newPos = (it * duration).toInt()
                                    videoView?.seekTo(newPos)
                                    currentPos = newPos
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Text(
                                text = String.format("%02d:%02d", duration / 1000 / 60, (duration / 1000) % 60),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        isFullScreen = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Maximizar",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBlockView(
    block: EditorBlock.File,
    onBlockChange: (EditorBlock.File) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    syncManager: com.example.data.remote.DriveSyncManager
) {
    val context = LocalContext.current
    val resolvedUrl = rememberDownloadedUri(block.sourceUrl, syncManager)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CosmicSurface)
            .border(1.dp, CosmicBorder, RoundedCornerShape(8.dp))
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings,
                onClick = {
                    val urlToUse = if (resolvedUrl.isNotEmpty()) resolvedUrl else block.sourceUrl
                    if (urlToUse.isNotEmpty()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(urlToUse), "*/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se puede abrir el archivo", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onOpenSettings()
                    }
                }
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.InsertDriveFile, null, tint = GeminiBlue, modifier = Modifier.size(32.dp))
            Column {
                Text(block.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (block.size.isNotEmpty()) {
                    Text(block.size, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoBlockView(
    block: EditorBlock.Todo,
    onBlockChange: (EditorBlock.Todo) -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (block.isChecked) GeminiBlue else Color.Transparent)
                .border(
                    width = 1.8.dp,
                    color = if (block.isChecked) GeminiBlue else TextSecondary.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(5.dp)
                )
                .clickable {
                    onBlockChange(block.copy(isChecked = !block.isChecked))
                },
            contentAlignment = Alignment.Center
        ) {
            if (block.isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completado",
                    tint = GeminiOnPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        BasicTextField(
            value = block.content,
            onValueChange = { onBlockChange(block.copy(content = it)) },
            textStyle = TextStyle(
                color = if (block.isChecked) TextTertiary else TextPrimary,
                fontSize = 15.sp,
                textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (block.content.isEmpty()) {
                    Text("Tarea pendiente...", color = TextTertiary, fontSize = 15.sp)
                }
                innerTextField()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalloutBlockView(
    block: EditorBlock.Callout,
    onBlockChange: (EditorBlock.Callout) -> Unit,
    onOpenSettings: () -> Unit
) {
    val bgColor = if (!block.bgColorHex.isNullOrEmpty()) {
        try { Color(android.graphics.Color.parseColor(block.bgColorHex)) } catch (e: Exception) { CosmicSurfaceVariant }
    } else {
        when (block.colorVariant) {
            "Blue" -> Color(0xFF132338)
            "Green" -> Color(0xFF132B1C)
            "Amber" -> Color(0xFF2E2012)
            "Red" -> Color(0xFF2E1315)
            else -> CosmicSurfaceVariant
        }
    }
    
    val borderColor = when (block.colorVariant) {
        "Blue" -> GeminiBlue.copy(alpha = 0.5f)
        "Green" -> Color(0xFF27AE60).copy(alpha = 0.5f)
        "Amber" -> Color(0xFFF2994A).copy(alpha = 0.5f)
        "Red" -> Color(0xFFEB5757).copy(alpha = 0.5f)
        else -> GeminiCyanAccent.copy(alpha = 0.35f)
    }

    val textColor = if (!block.textColorHex.isNullOrEmpty()) {
        try { Color(android.graphics.Color.parseColor(block.textColorHex)) } catch (e: Exception) { TextPrimary }
    } else {
        TextPrimary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings
            ),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = block.emoji.ifEmpty { "💡" },
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val nextEmoji = when (block.emoji) {
                            "💡" -> "⚠️"
                            "⚠️" -> "📌"
                            "📌" -> "🚀"
                            "🚀" -> "⭐"
                            else -> "💡"
                        }
                        onBlockChange(block.copy(emoji = nextEmoji))
                    }
                    .padding(2.dp)
            )

            BasicTextField(
                value = block.content,
                onValueChange = { onBlockChange(block.copy(content = it)) },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (block.content.isEmpty()) {
                        Text("Nota destacada / Callout...", color = textColor.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuoteBlockView(
    block: EditorBlock.Quote,
    onBlockChange: (EditorBlock.Quote) -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .background(GeminiBlue, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        BasicTextField(
            value = block.content,
            onValueChange = { onBlockChange(block.copy(content = it)) },
            textStyle = TextStyle(
                color = TextSecondary,
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (block.content.isEmpty()) {
                    Text("Cita textual...", color = TextTertiary, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                }
                innerTextField()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DividerBlockView(
    block: EditorBlock.Divider,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings
            ),
        contentAlignment = Alignment.Center
    ) {
        val thickness = block.thickness.dp
        val color = parseHexColorSafe(block.colorHex, CosmicBorder)
        
        when (block.style) {
            "Dotted" -> {
                Canvas(modifier = Modifier.fillMaxWidth().height(thickness)) {
                    val strokeWidth = thickness.toPx()
                    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(strokeWidth, strokeWidth * 2), 0f)
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = strokeWidth,
                        pathEffect = pathEffect
                    )
                }
            }
            "Dashed" -> {
                Canvas(modifier = Modifier.fillMaxWidth().height(thickness)) {
                    val strokeWidth = thickness.toPx()
                    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 4, strokeWidth * 2), 0f)
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = strokeWidth,
                        pathEffect = pathEffect
                    )
                }
            }
            else -> { // Solid
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = thickness,
                    color = color
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeBlockView(
    block: EditorBlock.Code,
    onBlockChange: (EditorBlock.Code) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val bgColor = if (!block.bgColorHex.isNullOrEmpty()) {
        try { Color(android.graphics.Color.parseColor(block.bgColorHex)) } catch (e: Exception) { Color(0xFF131720) }
    } else {
        Color(0xFF131720)
    }
    val textColor = if (!block.textColorHex.isNullOrEmpty()) {
        try { Color(android.graphics.Color.parseColor(block.textColorHex)) } catch (e: Exception) { Color(0xFFE6EDF3) }
    } else {
        Color(0xFFE6EDF3)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicElement2SecondHold(
                onOpenSettings = onOpenSettings
            ),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, Color(0xFF282E3A))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.language.ifEmpty { "CODE" }.uppercase(),
                    color = GeminiCyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Código", block.code))
                            Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", color = GeminiBlue, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = block.code,
                onValueChange = { onBlockChange(block.copy(code = it)) },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (block.code.isEmpty()) {
                        Text("// Escribe o pega tu código aquí...", color = textColor.copy(alpha = 0.5f), fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    innerTextField()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteEditorWorkspace(
    note: NoteEntity,
    aiLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NoteEntity) -> Unit,
    onAiModify: (String) -> Unit,
    onOpenChatbot: (String?) -> Unit,
    syncManager: com.example.data.remote.DriveSyncManager
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var blocks by remember(note.id) { mutableStateOf(parseBlocks(note.content)) }
    var tags by remember(note.id) { mutableStateOf(note.tags) }
    var aiQuery by remember { mutableStateOf("") }
    var inNoteSearchQuery by remember { mutableStateOf("") }
    var isSearchingInNote by remember { mutableStateOf(false) }

    // History undo/redo stacks
    var editingBlockSettings by remember { mutableStateOf<EditorBlock?>(null) }
    val undoStack = remember { mutableStateListOf<List<EditorBlock>>() }
    val redoStack = remember { mutableStateListOf<List<EditorBlock>>() }

    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    var selectedBlockIndex by remember { mutableStateOf(-1) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()
    var pendingCursorOffset by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingTabInsertionTrigger by remember { mutableStateOf<String?>(null) }

    fun pushHistory() {
        undoStack.add(cloneBlocks(blocks))
        redoStack.clear()
    }

    // Auto-save on block structural change
    fun updateBlocksAndSave(newBlocks: List<EditorBlock>) {
        blocks = newBlocks
        onSave(note.copy(title = title, content = serializeBlocks(newBlocks), tags = tags))
    }

    fun moveBlockUp(fromIndex: Int) {
        if (fromIndex > 0 && fromIndex in blocks.indices && blocks[fromIndex] is EditorBlock.Text) {
            pushHistory()
            val mutableBlocks = blocks.toMutableList()
            val currentBlock = mutableBlocks[fromIndex]
            mutableBlocks.removeAt(fromIndex)
            val newIndex = fromIndex - 1
            mutableBlocks.add(newIndex, currentBlock)
            selectedBlockIndex = newIndex
            updateBlocksAndSave(mutableBlocks)
            scope.launch {
                delay(50)
                try {
                    focusRequesters[currentBlock.id]?.requestFocus()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun moveBlockDown(fromIndex: Int) {
        if (fromIndex >= 0 && fromIndex < blocks.size - 1 && blocks[fromIndex] is EditorBlock.Text) {
            pushHistory()
            val mutableBlocks = blocks.toMutableList()
            val currentBlock = mutableBlocks[fromIndex]
            mutableBlocks.removeAt(fromIndex)
            val newIndex = fromIndex + 1
            mutableBlocks.add(newIndex, currentBlock)
            selectedBlockIndex = newIndex
            updateBlocksAndSave(mutableBlocks)
            scope.launch {
                delay(50)
                try {
                    focusRequesters[currentBlock.id]?.requestFocus()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Collapsed block filter list for collapsible dropdown sections
    val visibleBlocks = remember(blocks) {
        val list = mutableListOf<EditorBlock>()
        var isHiding = false
        for (b in blocks) {
            if (b is EditorBlock.Text && b.isCollapsedHeader) {
                list.add(b)
                isHiding = b.isCollapsed
            } else if (b is EditorBlock.Text && b.isHeader) {
                list.add(b)
                isHiding = false
            } else {
                if (!isHiding) {
                    list.add(b)
                }
            }
        }
        list
    }

    // Sleek formatting states
    var showFormattingPanel by remember { mutableStateOf(false) }
    var showInsertionPanel by remember { mutableStateOf(false) }
    var showAiPanel by remember { mutableStateOf(false) }
    var showTextFontPicker by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }

    var isImportingPdfInNote by remember { mutableStateOf(false) }
    val pdfPickerLauncherInNote = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            isImportingPdfInNote = true
            scope.launch {
                try {
                    val originalName = getFileName(context, uri) ?: "Documento"
                    val sizeString = getFileSize(context, uri) ?: "Desconocido"
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    
                    val cleanName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val destFile = java.io.File(
                        java.io.File(context.filesDir, "imported_pdfs").apply { mkdirs() },
                        "${System.currentTimeMillis()}_$cleanName"
                    )
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val isPdf = originalName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"
                    val isTextFile = isSupportedTextExtension(originalName, mimeType)

                    val isImageFile = mimeType.startsWith("image/", ignoreCase = true) ||
                                      originalName.endsWith(".png", ignoreCase = true) ||
                                      originalName.endsWith(".jpg", ignoreCase = true) ||
                                      originalName.endsWith(".jpeg", ignoreCase = true) ||
                                      originalName.endsWith(".webp", ignoreCase = true) ||
                                      originalName.endsWith(".gif", ignoreCase = true)

                    val newExtractedBlocks = mutableListOf<EditorBlock>()
                    
                    if (isPdf) {
                        var totalElementsExtracted = 0
                        val imageDir = java.io.File(context.filesDir, "extracted_pdf_images").apply { mkdirs() }
                        
                        try {
                            val reader = com.itextpdf.text.pdf.PdfReader(destFile.absolutePath)
                            val numPages = reader.numberOfPages
                            val parser = com.itextpdf.text.pdf.parser.PdfReaderContentParser(reader)
                            
                            for (page in 1..numPages) {
                                val extractor = PageElementExtractor(context, imageDir, totalElementsExtracted)
                                parser.processContent(page, extractor)
                                
                                val pageBlocks = processPageElements(extractor.elements)
                                if (pageBlocks.isNotEmpty()) {
                                    totalElementsExtracted += extractor.elements.size
                                    newExtractedBlocks.addAll(pageBlocks)
                                }
                            }
                            reader.close()
                        } catch (e: Exception) {
                            android.util.Log.e("PDFImport", "Error extracting content with iTextG in-note flow", e)
                        }

                        if (totalElementsExtracted == 0) {
                            newExtractedBlocks.add(
                                EditorBlock.Text(
                                    content = "No se pudo extraer texto legible ni imágenes de las páginas de este PDF.",
                                    isItalic = true,
                                    fontSize = 13,
                                    fontColor = "Red"
                                )
                            )
                        }
                    } else if (isTextFile) {
                        val textContent = try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                input.bufferedReader().use { it.readText() }
                            } ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        
                        newExtractedBlocks.add(
                            EditorBlock.Text(
                                content = "Archivo Importado: $originalName",
                                isBold = true,
                                fontSize = 20,
                                isHeader = true
                            )
                        )
                        newExtractedBlocks.add(
                            EditorBlock.File(
                                name = originalName,
                                sourceUrl = destFile.absolutePath,
                                size = sizeString
                            )
                        )
                        
                        if (textContent.isNotEmpty()) {
                            newExtractedBlocks.addAll(parseTextContentToBlocks(textContent))
                        } else {
                            newExtractedBlocks.add(
                                EditorBlock.Text(
                                    content = "(Archivo de texto vacío)",
                                    isItalic = true,
                                    fontSize = 13,
                                    fontColor = "Normal"
                                )
                            )
                        }
                    } else if (isImageFile) {
                        newExtractedBlocks.add(
                            EditorBlock.Text(
                                content = "Imagen Importada: $originalName",
                                isBold = true,
                                fontSize = 18,
                                isHeader = true
                            )
                        )
                        newExtractedBlocks.add(
                            EditorBlock.Image(
                                urlOrPath = destFile.absolutePath,
                                caption = originalName,
                                width = "Match",
                                height = "Wrap"
                            )
                        )
                        newExtractedBlocks.add(
                            EditorBlock.File(
                                name = originalName,
                                sourceUrl = destFile.absolutePath,
                                size = sizeString
                            )
                        )
                    } else {
                        // General file
                        newExtractedBlocks.add(
                            EditorBlock.Text(
                                content = "Archivo Importado: $originalName",
                                isBold = true,
                                fontSize = 18,
                                isHeader = true
                            )
                        )
                        newExtractedBlocks.add(
                            EditorBlock.File(
                                name = originalName,
                                sourceUrl = destFile.absolutePath,
                                size = sizeString
                            )
                        )
                        newExtractedBlocks.add(
                            EditorBlock.Text(
                                content = "Puedes pulsar prolongadamente el archivo de arriba para cambiar su configuración, o tocarlo para abrirlo.",
                                isItalic = true,
                                fontSize = 13,
                                fontColor = "Normal"
                            )
                        )
                    }
                    
                    pushHistory()
                    val newList = blocks.toMutableList()
                    newList.addAll(newExtractedBlocks)
                    updateBlocksAndSave(newList)
                    
                    Toast.makeText(context, "Archivo importado correctamente: $originalName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.util.Log.e("PDFImportInNote", "Error importing file", e)
                    Toast.makeText(context, "Error al importar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isImportingPdfInNote = false
                }
            }
        }
    }

    if (isImportingPdfInNote) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSurface,
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = GeminiBlue)
                    Text("Importando archivo...", color = TextPrimary, fontSize = 16.sp)
                }
            }
        }
    }

    Scaffold(
        containerColor = CosmicBackground,
        topBar = {
            if (isSearchingInNote) {
                Surface(
                    color = CosmicSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            isSearchingInNote = false
                            inNoteSearchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar búsqueda", tint = GeminiBlue)
                        }
                        
                        OutlinedTextField(
                            value = inNoteSearchQuery,
                            onValueChange = { inNoteSearchQuery = it },
                            placeholder = { Text("Buscar en esta nota...", color = TextSecondary, fontSize = 14.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = GeminiCyanAccent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (inNoteSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { inNoteSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = CosmicBackground,
                                unfocusedContainerColor = CosmicBackground,
                                focusedBorderColor = GeminiBlue,
                                unfocusedBorderColor = CosmicBorder
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("Editor de Nota", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                            onDismiss()
                        }, modifier = Modifier.testTag("back_editor_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = GeminiBlue)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchingInNote = true
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar en nota", tint = GeminiBlue)
                        }
                        IconButton(onClick = {
                            onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                            Toast.makeText(context, "Guardado exitosamente", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.testTag("save_note_editor_btn")) {
                            Icon(Icons.Default.Save, contentDescription = "Guardar", tint = GeminiBlue)
                        }
                        
                        var showEditorMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showEditorMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = GeminiBlue)
                            }
                            DropdownMenu(
                                expanded = showEditorMenu,
                                onDismissRequest = { showEditorMenu = false },
                                modifier = Modifier.background(CosmicSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exportar como PDF", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = GeminiBlue) },
                                    onClick = {
                                        showEditorMenu = false
                                        exportNoteToPdf(context, note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSurface)
                )
            }
        },
        bottomBar = {
            val isKeyboardVisible = WindowInsets.isImeVisible
            val hasSelectedTextBlock = selectedBlockIndex in blocks.indices && blocks[selectedBlockIndex] is EditorBlock.Text
            val showEditorToolbar = hasSelectedTextBlock || showFormattingPanel || showInsertionPanel || showAiPanel || showTextFontPicker || showTextColorPicker
            
            if (showEditorToolbar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicSurface)
                        .border(BorderStroke(1.dp, CosmicBorder))
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // 1. AI QUICK ACTIONS PANEL
                    if (showAiPanel) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicSurfaceVariant)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val prompts = listOf(
                                "✨ Resumir" to "Sintetiza esta nota en tres puntos clave.",
                                "📋 Extraer To-Dos" to "Extrae las tareas y acciones de esta nota en formato de lista To-Do accionable con casillas [ ].",
                                "💡 Ideas Clave" to "Genera 3 ideas y preguntas clave complementarias para profundizar en esta nota.",
                                "✍️ Expandir" to "Amplía el contenido de la nota agregando detalles y ejemplos útiles.",
                                "🎯 Corregir" to "Corrige la ortografía, redacción y gramática de la nota sin alterar el fondo.",
                                "🌐 Traducir" to "Traduce la nota completa al inglés de manera fluida.",
                                "🔖 Etiquetas" to "Sugiere 5 etiquetas adecuadas basadas en el contenido."
                            )
                            items(prompts) { (label, promptText) ->
                                AssistChip(
                                    onClick = {
                                        onAiModify(promptText)
                                    },
                                    label = { Text(label, fontSize = 11.sp, color = TextPrimary) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = CosmicSurface,
                                        labelColor = TextPrimary
                                    )
                                )
                            }
                        }
                    }

                    // 2. FORMATTING PANEL (Aa)
                    if (showFormattingPanel) {
                        val activeBlock = if (selectedBlockIndex in blocks.indices) blocks[selectedBlockIndex] as? EditorBlock.Text else null
                        if (activeBlock != null) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSurfaceVariant)
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Bold
                                item {
                                    IconToggleButton(
                                        checked = activeBlock.isBold,
                                        onCheckedChange = { 
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(isBold = it)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.FormatBold, "Negrita", tint = if (activeBlock.isBold) GeminiCyanAccent else TextPrimary)
                                    }
                                }
                                // Italic
                                item {
                                    IconToggleButton(
                                        checked = activeBlock.isItalic,
                                        onCheckedChange = { 
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(isItalic = it)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.FormatItalic, "Cursiva", tint = if (activeBlock.isItalic) GeminiCyanAccent else TextPrimary)
                                    }
                                }
                                // Underline
                                item {
                                    IconToggleButton(
                                        checked = activeBlock.isUnderline,
                                        onCheckedChange = { 
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(isUnderline = it)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.FormatUnderlined, "Subrayado", tint = if (activeBlock.isUnderline) GeminiCyanAccent else TextPrimary)
                                    }
                                }
                                item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(20.dp)) }
                                // Bullet List
                                item {
                                    IconToggleButton(
                                        checked = activeBlock.isBullet,
                                        onCheckedChange = { 
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(isBullet = it, isNumbered = false)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.FormatListBulleted, "Lista Viñetas", tint = if (activeBlock.isBullet) GeminiCyanAccent else TextPrimary)
                                    }
                                }
                                // Numbered List
                                item {
                                    IconToggleButton(
                                        checked = activeBlock.isNumbered,
                                        onCheckedChange = { 
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(isNumbered = it, isBullet = false)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.FormatListNumbered, "Lista Numerada", tint = if (activeBlock.isNumbered) GeminiCyanAccent else TextPrimary)
                                    }
                                }
                                // Collapsed Header
                                item {
                                    IconToggleButton(
                                        checked = activeBlock.isCollapsedHeader,
                                        onCheckedChange = { 
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(isCollapsedHeader = it)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.UnfoldLess, "Título Contraíble", tint = if (activeBlock.isCollapsedHeader) GeminiCyanAccent else TextPrimary)
                                    }
                                }
                                item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(20.dp)) }
                                // Font Size Down
                                item {
                                    IconButton(onClick = {
                                        if (activeBlock.fontSize > 10) {
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(fontSize = block.fontSize - 2)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    }) {
                                        Text("-", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                                item {
                                    Text("${activeBlock.fontSize}", color = GeminiCyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                // Font Size Up
                                item {
                                    IconButton(onClick = {
                                        if (activeBlock.fontSize < 36) {
                                            pushHistory()
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                                                    block.copy(fontSize = block.fontSize + 2)
                                                } else block
                                            }
                                            updateBlocksAndSave(updated)
                                        }
                                    }) {
                                        Text("+", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                                item { VerticalDivider(color = CosmicBorder, modifier = Modifier.height(20.dp)) }
                                // Font Family Selector
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CosmicSurface,
                                        border = BorderStroke(1.dp, CosmicBorder),
                                        modifier = Modifier.clickable { showFormattingPanel = true; showTextFontPicker = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.TextFields,
                                                contentDescription = "Paquete de tipografías",
                                                tint = GeminiCyanAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = getAppFontDisplayName(activeBlock.fontFamily),
                                                fontFamily = getAppFontFamily(activeBlock.fontFamily),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                // Text Color Recuadro (Opens HEX Color Dialog)
                                item {
                                    val textHex = if (activeBlock.fontColor.startsWith("#")) {
                                        activeBlock.fontColor
                                    } else when (activeBlock.fontColor.lowercase()) {
                                        "purple" -> "#D0BCFF"
                                        "blue" -> "#8AB4F8"
                                        "green" -> "#81C784"
                                        "red" -> "#E57373"
                                        "amber" -> "#FFB74D"
                                        "cyan" -> "#80DEEA"
                                        "pink" -> "#F48FB1"
                                        else -> "#FFFFFF"
                                    }
                                    val displayColor = parseHexColorSafe(textHex)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Color:", fontSize = 11.sp, color = TextSecondary)
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(displayColor)
                                                .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .clickable { showFormattingPanel = true; showTextColorPicker = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Palette,
                                                contentDescription = "Elegir color hex",
                                                tint = if (displayColor.red * 0.299 + displayColor.green * 0.587 + displayColor.blue * 0.114 > 0.6) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSurfaceVariant)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Selecciona un párrafo para aplicar formato", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // 3. INSERTIONS PANEL (+)
                    if (showInsertionPanel) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicSurfaceVariant)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Todo())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Tarea añadida", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Tarea", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Checklist, null, modifier = Modifier.size(14.dp), tint = GeminiCyanAccent) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Callout())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Destacado añadido", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Destacado", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(14.dp), tint = GeminiCyanAccent) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Quote())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Cita añadida", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Cita", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.FormatQuote, null, modifier = Modifier.size(14.dp), tint = GeminiCyanAccent) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Divider())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Divisor añadido", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Divisor", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.HorizontalRule, null, modifier = Modifier.size(14.dp), tint = GeminiCyanAccent) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Code())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Código añadido", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Código", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp), tint = GeminiCyanAccent) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Table())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Tabla añadida", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Tabla", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.GridOn, null, modifier = Modifier.size(14.dp), tint = GeminiBlue) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Image())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Imagen añadida", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Imagen", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp), tint = GeminiBlue) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Audio())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Audio añadido", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Audio", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Mic, null, modifier = Modifier.size(14.dp), tint = GeminiBlue) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.Video())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Video añadido", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Video", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(14.dp), tint = GeminiBlue) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.add(EditorBlock.File())
                                        updateBlocksAndSave(newList)
                                        Toast.makeText(context, "Archivo añadido", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Importar archivo", fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp), tint = GeminiBlue) }
                                )
                            }
                        }
                    }

                    // 4. MAIN RICH TOOLBAR ROW (Image 3 style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // AI Magic toggle -> direct shortcut to AI quick actions panel
                            item {
                                IconButton(onClick = {
                                    showAiPanel = !showAiPanel
                                    showInsertionPanel = false
                                    showFormattingPanel = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "IA Mágica",
                                        tint = if (showAiPanel) GeminiCyanAccent else TextPrimary
                                    )
                                }
                            }
                            // Insert Media (+) toggle
                            item {
                                IconButton(onClick = {
                                    showInsertionPanel = !showInsertionPanel
                                    showAiPanel = false
                                    showFormattingPanel = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Añadir",
                                        tint = if (showInsertionPanel) GeminiCyanAccent else TextPrimary
                                    )
                                }
                            }
                            // Formatting (Aa) toggle
                            item {
                                IconButton(onClick = {
                                    showFormattingPanel = !showFormattingPanel
                                    showAiPanel = false
                                    showInsertionPanel = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.FormatSize,
                                        contentDescription = "Formato",
                                        tint = if (showFormattingPanel) GeminiCyanAccent else TextPrimary
                                    )
                                }
                            }

                            
                            // Separator
                            item {
                                VerticalDivider(color = CosmicBorder, modifier = Modifier.height(20.dp))
                            }

                            // Undo
                            item {
                                IconButton(
                                    onClick = {
                                        if (undoStack.isNotEmpty()) {
                                            redoStack.add(cloneBlocks(blocks))
                                            val prev = undoStack.removeAt(undoStack.size - 1)
                                            blocks = prev
                                            updateBlocksAndSave(prev)
                                        }
                                    },
                                    enabled = undoStack.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = "Deshacer",
                                        tint = if (undoStack.isNotEmpty()) TextPrimary else TextTertiary
                                    )
                                }
                            }
                            // Redo
                            item {
                                IconButton(
                                    onClick = {
                                        if (redoStack.isNotEmpty()) {
                                            undoStack.add(cloneBlocks(blocks))
                                            val next = redoStack.removeAt(redoStack.size - 1)
                                            blocks = next
                                            updateBlocksAndSave(next)
                                        }
                                    },
                                    enabled = redoStack.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Redo,
                                        contentDescription = "Rehacer",
                                        tint = if (redoStack.isNotEmpty()) TextPrimary else TextTertiary
                                    )
                                }
                            }

                            // Separator
                            item {
                                VerticalDivider(color = CosmicBorder, modifier = Modifier.height(20.dp))
                            }

                            // Tab / Spacing block ("dar espaciado")
                            item {
                                val isActiveBlockText = selectedBlockIndex in blocks.indices && blocks[selectedBlockIndex] is EditorBlock.Text
                                IconButton(
                                    onClick = {
                                        if (selectedBlockIndex in blocks.indices) {
                                            val activeId = blocks[selectedBlockIndex].id
                                            pendingTabInsertionTrigger = activeId
                                        }
                                    },
                                    enabled = isActiveBlockText
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardTab,
                                        contentDescription = "Dar espaciado",
                                        tint = if (isActiveBlockText) TextPrimary else TextTertiary
                                    )
                                }
                            }

                            // Delete block ("eliminar")
                            item {
                                IconButton(
                                    onClick = {
                                        if (selectedBlockIndex in blocks.indices) {
                                            pushHistory()
                                            val indexToDelete = selectedBlockIndex
                                            val mutableBlocks = blocks.toMutableList()
                                            mutableBlocks.removeAt(indexToDelete)
                                            updateBlocksAndSave(mutableBlocks)
                                            
                                            val nextFocusIndex = if (indexToDelete > 0) indexToDelete - 1 else 0
                                            if (mutableBlocks.isNotEmpty()) {
                                                selectedBlockIndex = nextFocusIndex
                                                val targetBlock = mutableBlocks[nextFocusIndex]
                                                if (targetBlock is EditorBlock.Text) {
                                                    pendingCursorOffset = Pair(targetBlock.id, targetBlock.content.length)
                                                }
                                                scope.launch {
                                                    delay(50)
                                                    try {
                                                        focusRequesters[targetBlock.id]?.requestFocus()
                                                    } catch (e: Exception) {
                                                        // Ignore focus request if not yet ready
                                                    }
                                                }
                                            } else {
                                                selectedBlockIndex = -1
                                            }
                                        }
                                    },
                                    enabled = selectedBlockIndex in blocks.indices
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar bloque",
                                        tint = if (selectedBlockIndex in blocks.indices) TextPrimary else TextTertiary
                                    )
                                }
                            }

                            // Move Up block ("subir")
                            item {
                                val isSelectedText = selectedBlockIndex in blocks.indices && blocks[selectedBlockIndex] is EditorBlock.Text
                                val canMoveUp = isSelectedText && selectedBlockIndex > 0
                                IconButton(
                                    onClick = {
                                        moveBlockUp(selectedBlockIndex)
                                    },
                                    enabled = canMoveUp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Subir párrafo",
                                        tint = if (canMoveUp) TextPrimary else TextTertiary
                                    )
                                }
                            }

                            // Move Down block ("bajar")
                            item {
                                val isSelectedText = selectedBlockIndex in blocks.indices && blocks[selectedBlockIndex] is EditorBlock.Text
                                val canMoveDown = isSelectedText && selectedBlockIndex < blocks.size - 1
                                IconButton(
                                    onClick = {
                                        moveBlockDown(selectedBlockIndex)
                                    },
                                    enabled = canMoveDown
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Bajar párrafo",
                                        tint = if (canMoveDown) TextPrimary else TextTertiary
                                    )
                                }
                            }
                        }

                        // Hide keyboard / dismiss selection on far right
                        val kbController = LocalSoftwareKeyboardController.current
                        IconButton(onClick = { 
                            if (isKeyboardVisible) {
                                kbController?.hide()
                            } else {
                                selectedBlockIndex = -1
                                showFormattingPanel = false
                                showInsertionPanel = false
                                showAiPanel = false
                            }
                        }) {
                            Icon(
                                imageVector = if (isKeyboardVisible) Icons.Default.KeyboardArrowDown else Icons.Default.Close,
                                contentDescription = if (isKeyboardVisible) "Ocultar Teclado" else "Cerrar barra de formato",
                                tint = TextPrimary
                            )
                        }
                    }
                }
            } else {
                // Pristine bottom bar when keyboard is closed (Image 2 style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicBackground)
                        .border(BorderStroke(1.dp, CosmicBorder))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left: Search Button (mag glass circle)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CosmicSurface, CircleShape)
                            .clickable {
                                isSearchingInNote = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar en nota",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Middle: Pill-shaped Gemini Copilot Field (Pregúntale a la IA)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(CosmicSurface)
                            .border(1.dp, CosmicBorder, RoundedCornerShape(26.dp))
                            .clickable {
                                onOpenChatbot(null)
                            }
                            .padding(horizontal = 16.dp)
                            .testTag("editor_ai_ask_pill"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Preguntar",
                                tint = GeminiCyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pregúntale a la IA...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Right: Pen Circle Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CosmicSurface, CircleShape)
                            .clickable {
                                pushHistory()
                                val newBlock = EditorBlock.Text(content = "")
                                val newList = blocks.toMutableList()
                                newList.add(newBlock)
                                updateBlocksAndSave(newList)
                                selectedBlockIndex = newList.size - 1
                                scope.launch {
                                    delay(50)
                                    try {
                                        focusRequesters[newBlock.id]?.requestFocus()
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Escribir",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val lastBlock = blocks.lastOrNull()
                    val shouldCreateNewBlock = blocks.isEmpty() ||
                            lastBlock !is EditorBlock.Text ||
                            lastBlock.content.isNotEmpty()

                    if (shouldCreateNewBlock) {
                        pushHistory()
                        val newBlock = EditorBlock.Text(content = "")
                        val newList = blocks + newBlock
                        updateBlocksAndSave(newList)
                        selectedBlockIndex = newList.size - 1
                        scope.launch {
                            delay(50)
                            try {
                                focusRequesters[newBlock.id]?.requestFocus()
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    } else if (blocks.isNotEmpty()) {
                        val lastBlockNonNull = blocks.last()
                        selectedBlockIndex = blocks.size - 1
                        scope.launch {
                            delay(50)
                            try {
                                focusRequesters[lastBlockNonNull.id]?.requestFocus()
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Document Header Title - Borderless
            TextField(
                value = title,
                onValueChange = {
                    title = it
                    onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                },
                placeholder = { Text("Sin Título", color = TextTertiary, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input")
            )

            // Tags - Borderless
            TextField(
                value = tags,
                onValueChange = {
                    tags = it
                    onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                },
                placeholder = { Text("Etiquetas (separadas por comas)", color = TextTertiary.copy(alpha = 0.5f), fontSize = 12.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = GeminiBlue,
                    unfocusedTextColor = GeminiBlue
                ),
                textStyle = TextStyle(fontSize = 12.sp, color = GeminiBlue),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = CosmicBorder, modifier = Modifier.padding(vertical = 4.dp))

            // Render dynamic blocks inside the page canvas
            blocks.forEachIndexed { index, block ->
                val isVisible = visibleBlocks.contains(block)
                if (isVisible) {
                    key(block.id) {
                        val matchesSearch = remember(inNoteSearchQuery, block) {
                            if (inNoteSearchQuery.isEmpty()) false
                            else {
                                when (block) {
                                    is EditorBlock.Text -> block.content.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Table -> block.data.any { r -> r.any { c -> c.contains(inNoteSearchQuery, ignoreCase = true) } }
                                    is EditorBlock.Image -> block.caption.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Audio -> block.name.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Video -> block.title.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.File -> block.name.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Todo -> block.content.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Callout -> block.content.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Quote -> block.content.contains(inNoteSearchQuery, ignoreCase = true)
                                    is EditorBlock.Code -> block.code.contains(inNoteSearchQuery, ignoreCase = true)
                                    else -> false
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (block is EditorBlock.Text && (block.isHeader || block.isCollapsedHeader)) 6.dp else 2.dp)
                                .then(
                                    if (matchesSearch) {
                                        Modifier
                                            .background(GeminiBlue.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .border(1.dp, GeminiBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    } else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedBlockIndex = index
                                    showFormattingPanel = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (selectedBlockIndex == index || block !is EditorBlock.Text) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Arrastrar bloque",
                                        tint = GeminiCyanAccent,
                                        modifier = Modifier
                                            .padding(top = if (block is EditorBlock.Text) 2.dp else 12.dp)
                                            .padding(end = 4.dp)
                                            .size(20.dp)
                                            .pointerInput(index, blocks) {
                                                var totalDragY = 0f
                                                detectDragGestures(
                                                    onDragStart = { 
                                                        totalDragY = 0f 
                                                        selectedBlockIndex = index
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        totalDragY += dragAmount.y
                                                        val threshold = 60f // Adjust threshold as needed
                                                        if (totalDragY > threshold && index < blocks.size - 1) {
                                                            pushHistory()
                                                            val mutable = blocks.toMutableList()
                                                            java.util.Collections.swap(mutable, index, index + 1)
                                                            updateBlocksAndSave(mutable)
                                                            selectedBlockIndex = index + 1
                                                            totalDragY = 0f
                                                        } else if (totalDragY < -threshold && index > 0) {
                                                            pushHistory()
                                                            val mutable = blocks.toMutableList()
                                                            java.util.Collections.swap(mutable, index, index - 1)
                                                            updateBlocksAndSave(mutable)
                                                            selectedBlockIndex = index - 1
                                                            totalDragY = 0f
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp))
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when (block) {
                                        is EditorBlock.Text -> {
                                            var isFocused by remember { mutableStateOf(false) }
                                            val isSelected = selectedBlockIndex == index
                                            val textStyle = TextStyle(
                                                fontFamily = getAppFontFamily(block.fontFamily),
                                                fontSize = block.fontSize.sp,
                                                fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = if (block.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                                textAlign = when (block.alignment) {
                                                    "Center" -> TextAlign.Center
                                                    "Right" -> TextAlign.Right
                                                    else -> TextAlign.Left
                                                },
                                                color = when (block.fontColor.lowercase()) {
                                                    "purple" -> Color(0xFFD0BCFF)
                                                    "blue" -> Color(0xFF8AB4F8)
                                                    "green" -> Color(0xFF81C784)
                                                    "red" -> Color(0xFFE57373)
                                                    "amber" -> Color(0xFFFFB74D)
                                                    "cyan" -> Color(0xFF80DEEA)
                                                    "pink" -> Color(0xFFF48FB1)
                                                    else -> if (block.fontColor.startsWith("#")) {
                                                        try { Color(android.graphics.Color.parseColor(block.fontColor)) } catch (e: Exception) { TextPrimary }
                                                    } else TextPrimary
                                                }
                                            )

                                            var tfValue by remember(block.id) {
                                                val visibleContent = if (block.isCollapsedHeader && block.isCollapsed) {
                                                    block.content.split("\n").firstOrNull() ?: ""
                                                } else {
                                                    block.content
                                                }
                                                val initialText = "\u200B" + visibleContent
                                                val sel = initialText.length
                                                mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(sel)))
                                            }

                                            LaunchedEffect(block.content, block.isCollapsed, block.isCollapsedHeader) {
                                                val visibleContent = if (block.isCollapsedHeader && block.isCollapsed) {
                                                    block.content.split("\n").firstOrNull() ?: ""
                                                } else {
                                                    block.content
                                                }
                                                val expectedText = "\u200B" + visibleContent
                                                if (tfValue.text != expectedText && !isFocused) {
                                                    val newSelStart = tfValue.selection.start.coerceIn(1, expectedText.length)
                                                    val newSelEnd = tfValue.selection.end.coerceIn(1, expectedText.length)
                                                    tfValue = tfValue.copy(
                                                        text = expectedText,
                                                        selection = TextRange(newSelStart, newSelEnd)
                                                    )
                                                }
                                            }

                                            LaunchedEffect(pendingCursorOffset) {
                                                pendingCursorOffset?.let { (targetId, offset) ->
                                                    if (targetId == block.id) {
                                                        val expectedOffset = offset + 1
                                                        val safeOffset = expectedOffset.coerceIn(1, tfValue.text.length)
                                                        tfValue = tfValue.copy(selection = TextRange(safeOffset))
                                                        pendingCursorOffset = null
                                                    }
                                                }
                                            }

                                            LaunchedEffect(pendingTabInsertionTrigger) {
                                                if (pendingTabInsertionTrigger == block.id) {
                                                    val currentText = tfValue.text
                                                    val selStart = tfValue.selection.start
                                                    val selEnd = tfValue.selection.end
                                                    val before = currentText.substring(0, selStart)
                                                    val after = currentText.substring(selEnd)
                                                    val tabText = "    " // 4 spaces for 0.5 inches programming-like tab
                                                    val newText = before + tabText + after
                                                    val newSelection = TextRange(selStart + tabText.length)
                                                    tfValue = tfValue.copy(
                                                        text = newText,
                                                        selection = newSelection
                                                    )
                                                    val textToSave = if (newText.startsWith("\u200B")) newText.substring(1) else newText
                                                    val updated = blocks.mapIndexed { idx, b ->
                                                        if (idx == index && b is EditorBlock.Text) {
                                                            b.copy(content = textToSave)
                                                        } else b
                                                    }
                                                    blocks = updated
                                                    onSave(note.copy(title = title, content = serializeBlocks(updated), tags = tags))
                                                    pendingTabInsertionTrigger = null
                                                }
                                            }

                                            val focusRequester = focusRequesters.getOrPut(block.id) { FocusRequester() }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                Row(
                                                     verticalAlignment = Alignment.Top,
                                                     horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                     modifier = Modifier.fillMaxWidth()
                                                 ) {
                                                     if (block.isBullet) {
                                                         Box(
                                                             modifier = Modifier
                                                                 .padding(horizontal = 4.dp)
                                                                 .padding(top = 8.dp)
                                                                 .size(6.dp)
                                                                 .background(GeminiBlue, CircleShape)
                                                         )
                                                     }
                                                     if (block.isNumbered) {
                                                         Text(
                                                             text = "${index + 1}.",
                                                             color = GeminiBlue,
                                                             fontSize = block.fontSize.sp,
                                                             modifier = Modifier.padding(top = 2.dp)
                                                         )
                                                     }
                                                     if (block.isCollapsedHeader) {
                                                         IconButton(
                                                             onClick = {
                                                                 pushHistory()
                                                                 val updated = blocks.mapIndexed { idx, b ->
                                                                     if (idx == index && b is EditorBlock.Text) {
                                                                         b.copy(isCollapsed = !b.isCollapsed)
                                                                     } else b
                                                                 }
                                                                 updateBlocksAndSave(updated)
                                                             },
                                                             modifier = Modifier
                                                                 .size(24.dp)
                                                                 .padding(top = 2.dp)
                                                         ) {
                                                             Icon(
                                                                 imageVector = if (block.isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                                                 contentDescription = null,
                                                                 tint = GeminiBlue,
                                                                 modifier = Modifier.size(16.dp)
                                                             )
                                                         }
                                                     }

                                                     BasicTextField(
                                                        value = tfValue,
                                                        onValueChange = { newVal ->
                                                             if (index in blocks.indices && blocks[index].id == block.id) {
                                                                 val rawText = newVal.text
                                                                 
                                                                 if (!rawText.startsWith("​")) {
                                                                     // Zero-width space was deleted! Treat as BACKSPACE at the beginning of the block.
                                                                     if (index > 0) {
                                                                         val prevBlock = blocks[index - 1]
                                                                         if (prevBlock is EditorBlock.Text) {
                                                                             pushHistory()
                                                                             val mutableBlocks = blocks.toMutableList()
                                                                             val originalPrevTextLength = prevBlock.content.length
                                                                             
                                                                             val remainingContent = rawText
                                                                             val isCollapsed = block.isCollapsedHeader && block.isCollapsed
                                                                             val lines = block.content.split("\n")
                                                                             val currentBlockFullText = if (isCollapsed && lines.size > 1) {
                                                                                 val remainingLines = lines.drop(1).joinToString("\n")
                                                                                 if (remainingContent.isEmpty()) remainingLines else "${remainingContent}\n${remainingLines}"
                                                                             } else {
                                                                                 remainingContent
                                                                             }
                                                                             
                                                                             val mergedText = prevBlock.content + currentBlockFullText
                                                                             mutableBlocks[index - 1] = prevBlock.copy(content = mergedText)
                                                                             mutableBlocks.removeAt(index)
                                                                             
                                                                             pendingCursorOffset = Pair(prevBlock.id, originalPrevTextLength)
                                                                             updateBlocksAndSave(mutableBlocks)
                                                                             selectedBlockIndex = index - 1
                                                                             
                                                                             scope.launch {
                                                                                 delay(50)
                                                                                 try {
                                                                                     focusRequesters[prevBlock.id]?.requestFocus()
                                                                                 } catch (e: Exception) {
                                                                                     // Ignore
                                                                                 }
                                                                             }
                                                                         } else {
                                                                             // Previous block is not Text. Just remove this block.
                                                                             pushHistory()
                                                                             val mutableBlocks = blocks.toMutableList()
                                                                             mutableBlocks.removeAt(index)
                                                                             updateBlocksAndSave(mutableBlocks)
                                                                             selectedBlockIndex = index - 1
                                                                         }
                                                                     }
                                                                 } else {
                                                                     // Starts with ​, extract actual text
                                                                     val cleanText = rawText.substring(1)
                                                                     
                                                                     // Detect if a newline was inserted (Enter key)
                                                                     val oldText = tfValue.text
                                                                     val newText = rawText
                                                                     var updatedNewVal = newVal
                                                                     var finalCleanText = cleanText

                                                                     if (newText.length > oldText.length && newVal.selection.start > 1) {
                                                                         val insertIdx = newVal.selection.start - 1
                                                                         if (insertIdx in newText.indices && newText[insertIdx] == '\n') {
                                                                             // A newline was just inserted! Let's find the line before this newline
                                                                             val textBeforeNewline = newText.substring(0, insertIdx)
                                                                             val lastLine = textBeforeNewline.split("\n").lastOrNull() ?: ""
                                                                             val cleanLastLine = lastLine.removePrefix("\u200B")
                                                                             val leadingIndentation = cleanLastLine.takeWhile { it == ' ' || it == '\t' }
                                                                             
                                                                             if (leadingIndentation.isNotEmpty()) {
                                                                                 val newTextWithIndent = newText.substring(0, insertIdx + 1) + leadingIndentation + newText.substring(insertIdx + 1)
                                                                                 val newSelectionStart = newVal.selection.start + leadingIndentation.length
                                                                                 val newSelectionEnd = newVal.selection.end + leadingIndentation.length
                                                                                 updatedNewVal = newVal.copy(
                                                                                     text = newTextWithIndent,
                                                                                     selection = TextRange(newSelectionStart, newSelectionEnd)
                                                                                 )
                                                                                 finalCleanText = newTextWithIndent.substring(1)
                                                                             }
                                                                         }
                                                                     }

                                                                     val isCollapsed = block.isCollapsedHeader && block.isCollapsed
                                                                     val lines = block.content.split("\n")
                                                                     val textToSave = if (isCollapsed && lines.size > 1) {
                                                                         val remaining = lines.drop(1).joinToString("\n")
                                                                         if (finalCleanText.isEmpty()) remaining else "${finalCleanText}\n${remaining}"
                                                                     } else {
                                                                         finalCleanText
                                                                     }
                                                                     
                                                                     // Coerce selection to never go before index 1
                                                                     val cleanStart = updatedNewVal.selection.start.coerceIn(1, updatedNewVal.text.length)
                                                                     val cleanEnd = updatedNewVal.selection.end.coerceIn(1, updatedNewVal.text.length)
                                                                     val cleanSelection = TextRange(cleanStart, cleanEnd)
                                                                     
                                                                     tfValue = updatedNewVal.copy(selection = cleanSelection)
                                                                     
                                                                     if (block.content != textToSave) {
                                                                         val updated = blocks.mapIndexed { idx, b ->
                                                                             if (idx == index && b is EditorBlock.Text) {
                                                                                 b.copy(content = textToSave)
                                                                             } else b
                                                                         }
                                                                         blocks = updated
                                                                         // NO LONGER SAVING ON EVERY KEYSTROKE
                                                                     }
                                                                 }
                                                             }
                                                         },
                                                        textStyle = textStyle,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .focusRequester(focusRequester)
                                                            .onFocusChanged { focusState ->
                                                                isFocused = focusState.isFocused
                                                                if (focusState.isFocused) {
                                                                    selectedBlockIndex = index
                                                                } else {
                                                                    // Save when focus is lost
                                                                    onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                                                                }
                                                            }
                                                            .let { modifier ->
                                                                // Periodically save while focused
                                                                LaunchedEffect(blocks, isFocused) {
                                                                    if (isFocused) {
                                                                        delay(5000)
                                                                        onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                                                                    }
                                                                }
                                                                modifier
                                                            }
                                                            .onKeyEvent { keyEvent ->
                                                                 if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Tab) {
                                                                     pendingTabInsertionTrigger = block.id
                                                                     true
                                                                 } else {
                                                                     false
                                                                 }
                                                             }
                                                            .testTag("text_block_$index"),
                                                        decorationBox = { innerTextField ->
                                                            if (tfValue.text.isEmpty() || tfValue.text == "\u200B") {
                                                                Text(
                                                                    text = if (block.isCollapsedHeader) "Título contraíble..." else "Escribe algo aquí...",
                                                                    color = TextTertiary.copy(alpha = 0.4f),
                                                                    style = textStyle
                                                                )
                                                            }
                                                            innerTextField()
                                                        }
                                                    )
                                                }
                                            }
                                        }

                            is EditorBlock.Table -> {
                                TableBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }

                            is EditorBlock.Image -> {
                                ImageBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block },
                                    syncManager = syncManager
                                )
                            }

                            is EditorBlock.Audio -> {
                                AudioBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block },
                                    syncManager = syncManager
                                )
                            }

                            is EditorBlock.Video -> {
                                VideoBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block },
                                    syncManager = syncManager
                                )
                            }
                            is EditorBlock.File -> {
                                FileBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block },
                                    syncManager = syncManager
                                )
                            }
                            is EditorBlock.Todo -> {
                                TodoBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                            is EditorBlock.Callout -> {
                                CalloutBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                            is EditorBlock.Quote -> {
                                QuoteBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                            is EditorBlock.Divider -> {
                                DividerBlockView(
                                    block = block,
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                            is EditorBlock.Code -> {
                                CodeBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // Scheduled reminder status banner inside Editor
        if (note.reminderTime != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                border = BorderStroke(1.dp, GeminiCyanAccent.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, tint = GeminiCyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Recordatorio programado:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        formatter.format(Date(note.reminderTime)),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (editingBlockSettings != null) {
            val currentBlock = editingBlockSettings!!
            BlockSettingsBottomSheet(
                block = currentBlock,
                onDismiss = { editingBlockSettings = null },
                onBlockChange = { updatedBlock ->
                    pushHistory()
                    val updated = blocks.map { if (it.id == updatedBlock.id) updatedBlock else it }
                    updateBlocksAndSave(updated)
                    editingBlockSettings = updatedBlock
                },
                onDelete = {
                    pushHistory()
                    val newList = blocks.filter { it.id != currentBlock.id }
                    updateBlocksAndSave(newList)
                    editingBlockSettings = null
                }
            )
        }

        val activeTextBlock = if (selectedBlockIndex in blocks.indices) blocks[selectedBlockIndex] as? EditorBlock.Text else null

        if (showTextFontPicker && activeTextBlock != null) {
            TypographyPickerDialog(
                selectedFontId = activeTextBlock.fontFamily,
                onFontSelected = { selectedFontId ->
                    pushHistory()
                    val updated = blocks.mapIndexed { idx, block ->
                        if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                            block.copy(fontFamily = selectedFontId)
                        } else block
                    }
                    updateBlocksAndSave(updated)
                    showTextFontPicker = false
                    showFormattingPanel = true
                },
                onDismiss = { 
                    showTextFontPicker = false 
                    showFormattingPanel = true
                }
            )
        }

        if (showTextColorPicker && activeTextBlock != null) {
            val currentColorHex = if (activeTextBlock.fontColor.startsWith("#")) {
                activeTextBlock.fontColor
            } else when (activeTextBlock.fontColor.lowercase()) {
                "purple" -> "#D0BCFF"
                "blue" -> "#8AB4F8"
                "green" -> "#81C784"
                "red" -> "#E57373"
                "amber" -> "#FFB74D"
                "cyan" -> "#80DEEA"
                "pink" -> "#F48FB1"
                else -> "#FFFFFF"
            }
            HexColorPickerDialog(
                initialColorHex = currentColorHex,
                title = "Color de Texto",
                onColorSelected = { selectedHex ->
                    pushHistory()
                    val updated = blocks.mapIndexed { idx, block ->
                        if (idx == selectedBlockIndex && block is EditorBlock.Text) {
                            block.copy(fontColor = selectedHex)
                        } else block
                    }
                    updateBlocksAndSave(updated)
                    showTextColorPicker = false
                    showFormattingPanel = true
                },
                onDismiss = { 
                    showTextColorPicker = false 
                    showFormattingPanel = true
                }
            )
        }
    }
}
}
}

@Composable
fun rememberDownloadedUri(
    uriString: String,
    syncManager: com.example.data.remote.DriveSyncManager
): String {
    if (!uriString.startsWith("gdrive://")) {
        return uriString
    }
    var resolvedUri by remember(uriString) { mutableStateOf("") }
    LaunchedEffect(uriString) {
        val file = syncManager.getLocalFileForDriveUri(uriString)
        if (file != null) {
            resolvedUri = android.net.Uri.fromFile(file).toString()
        }
    }
    return resolvedUri
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSettingsBottomSheet(
    block: EditorBlock,
    onDismiss: () -> Unit,
    onBlockChange: (EditorBlock) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CosmicSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CosmicBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Configuración del Elemento", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            when (block) {
                is EditorBlock.Table -> TableSettingsContent(block, onBlockChange)
                is EditorBlock.Image -> ImageSettingsContent(block, onBlockChange)
                is EditorBlock.Audio -> AudioSettingsContent(block, onBlockChange)
                is EditorBlock.Video -> VideoSettingsContent(block, onBlockChange)
                is EditorBlock.File -> FileSettingsContent(block, onBlockChange)
                is EditorBlock.Divider -> DividerSettingsContent(block, onBlockChange)
                is EditorBlock.Code -> CodeSettingsContent(block, onBlockChange)
                is EditorBlock.Callout -> CalloutSettingsContent(block, onBlockChange)
                else -> {}
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onDelete(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E2429)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Eliminar Elemento", color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TableSettingsContent(block: EditorBlock.Table, onBlockChange: (EditorBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filas", color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (block.rows > 1) onBlockChange(block.copy(rows = block.rows - 1, data = block.data.dropLast(1))) }) { Icon(Icons.Default.Remove, null, tint = GeminiBlue) }
                Text(block.rows.toString(), color = TextPrimary, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { onBlockChange(block.copy(rows = block.rows + 1, data = block.data + listOf(List(block.cols) { "" }))) }) { Icon(Icons.Default.Add, null, tint = GeminiBlue) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Columnas", color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (block.cols > 1) onBlockChange(block.copy(cols = block.cols - 1, data = block.data.map { it.dropLast(1) })) }) { Icon(Icons.Default.Remove, null, tint = GeminiBlue) }
                Text(block.cols.toString(), color = TextPrimary, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { onBlockChange(block.copy(cols = block.cols + 1, data = block.data.map { it + "" })) }) { Icon(Icons.Default.Add, null, tint = GeminiBlue) }
            }
        }
        HorizontalDivider(color = CosmicBorder)
        HexColorBox(
            selectedColorHex = block.headerColor,
            onColorSelected = { onBlockChange(block.copy(headerColor = it)) },
            label = "Color de Encabezado (HEX)"
        )
        HorizontalDivider(color = CosmicBorder)
        Text("Alineación de Celdas", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Left", "Center", "Right").forEach { align ->
                FilterChip(
                    selected = block.cellColor == align,
                    onClick = { onBlockChange(block.copy(cellColor = align)) },
                    label = { Text(align, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        HorizontalDivider(color = CosmicBorder)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Bordes", color = TextPrimary)
            Switch(checked = block.borderColor != "None", onCheckedChange = { onBlockChange(block.copy(borderColor = if (it) "Border" else "None")) })
        }
        HorizontalDivider(color = CosmicBorder)
        Text("Márgenes", color = TextPrimary)
        Slider(value = block.margin.toFloat(), onValueChange = { onBlockChange(block.copy(margin = it.toInt())) }, valueRange = 0f..32f)
        HorizontalDivider(color = CosmicBorder)
        Text("Ancho", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match", "Wrap").forEach { w ->
                FilterChip(
                    selected = block.tableWidth == w,
                    onClick = { onBlockChange(block.copy(tableWidth = w)) },
                    label = { Text(w, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
    }
}


@Composable
fun ImageSettingsContent(block: EditorBlock.Image, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Ignore if persistable permission is not available
            }
            onBlockChange(block.copy(urlOrPath = uri.toString()))
            Toast.makeText(context, "Imagen cambiada", Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.caption,
            onValueChange = { onBlockChange(block.copy(caption = it)) },
            label = { Text("Pie de foto / Descripción") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch(arrayOf("image/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhotoLibrary, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar / Cambiar Imagen", color = Color.White)
        }
        Text("Ancho", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match" to "Completo", "Wrap" to "Ajustado").forEach { (w, label) ->
                FilterChip(
                    selected = block.width == w,
                    onClick = { onBlockChange(block.copy(width = w)) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Alto", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wrap" to "Proporcional", "Fixed" to "Fijo").forEach { (h, label) ->
                FilterChip(
                    selected = block.height == h,
                    onClick = { onBlockChange(block.copy(height = h)) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
    }
}



@Composable
fun AudioSettingsContent(block: EditorBlock.Audio, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Ignore
            }
            val name = getFileName(context, uri) ?: "Audio"
            onBlockChange(block.copy(sourceUrl = uri.toString(), name = name))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.name,
            onValueChange = { onBlockChange(block.copy(name = it)) },
            label = { Text("Nombre del Audio") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch(arrayOf("audio/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AudioFile, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Audio", color = Color.White)
        }
    }
}



@Composable
fun VideoSettingsContent(block: EditorBlock.Video, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Ignore
            }
            onBlockChange(block.copy(sourceUrl = uri.toString()))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.title,
            onValueChange = { onBlockChange(block.copy(title = it)) },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch(arrayOf("video/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.VideoFile, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Vídeo (Archivos)", color = Color.White)
        }
        Text("Ancho", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match", "Wrap").forEach { w ->
                FilterChip(
                    selected = block.width == w,
                    onClick = { onBlockChange(block.copy(width = w)) },
                    label = { Text(w, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Alto", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wrap", "Fixed").forEach { h ->
                FilterChip(
                    selected = block.height == h,
                    onClick = { onBlockChange(block.copy(height = h)) },
                    label = { Text(h, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
    }
}


@Composable
fun FileSettingsContent(block: EditorBlock.File, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
            } catch (e: SecurityException) {
                // Ignore
            }
            val name = getFileName(context, uri) ?: "Archivo"
            val size = getFileSize(context, uri) ?: "Desconocido"
            onBlockChange(block.copy(sourceUrl = uri.toString(), name = name, size = size))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.name,
            onValueChange = { onBlockChange(block.copy(name = it)) },
            label = { Text("Nombre del Archivo") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch(arrayOf("*/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Folder, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Archivo (Max 500MB)", color = Color.White)
        }
        if (block.sourceUrl.isNotEmpty()) {
            Text("Archivo seleccionado: ${block.name}", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun DividerSettingsContent(block: EditorBlock.Divider, onBlockChange: (EditorBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Estilo de Línea", color = TextPrimary, fontWeight = FontWeight.Bold)
        HexColorBox(
            selectedColorHex = block.colorHex ?: "#2A3142",
            onColorSelected = { onBlockChange(block.copy(colorHex = it)) },
            label = "Color de Línea (HEX)"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Solid", "Dashed", "Dotted").forEach { style ->
                FilterChip(
                    selected = block.style == style,
                    onClick = { onBlockChange(block.copy(style = style)) },
                    label = { Text(style, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Grosor: ${block.thickness}dp", color = TextPrimary)
        Slider(
            value = block.thickness.toFloat(),
            onValueChange = { onBlockChange(block.copy(thickness = it.toInt())) },
            valueRange = 1f..10f,
            steps = 9
        )
    }
}

@Composable
fun CodeSettingsContent(block: EditorBlock.Code, onBlockChange: (EditorBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Personalización de Código", color = TextPrimary, fontWeight = FontWeight.Bold)
        
        ColorPickerPreview(
            label = "Color de Fondo",
            selectedColorHex = block.bgColorHex ?: "#131720",
            onColorSelected = { onBlockChange(block.copy(bgColorHex = it)) }
        )

        ColorPickerPreview(
            label = "Color de Texto",
            selectedColorHex = block.textColorHex ?: "#E6EDF3",
            onColorSelected = { onBlockChange(block.copy(textColorHex = it)) }
        )
        
        OutlinedTextField(
            value = block.language,
            onValueChange = { onBlockChange(block.copy(language = it)) },
            label = { Text("Lenguaje") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CalloutSettingsContent(block: EditorBlock.Callout, onBlockChange: (EditorBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Personalización de Destacado", color = TextPrimary, fontWeight = FontWeight.Bold)
        
        @OptIn(ExperimentalLayoutApi::class)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Icono:", color = TextSecondary)
            Text(
                text = block.emoji.ifEmpty { "💡" },
                fontSize = 24.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CosmicSurfaceVariant)
                    .clickable { /* emoji menu is below */ }
                    .padding(8.dp)
            )
            // Icon options
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("💡", "⚠️", "📌", "🚀", "⭐", "✅", "❌", "ℹ️").forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (block.emoji == emoji) GeminiBlue.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { onBlockChange(block.copy(emoji = emoji)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 18.sp)
                    }
                }
            }
        }

        ColorPickerPreview(
            label = "Color de Fondo",
            selectedColorHex = block.bgColorHex ?: "#1E1C24",
            onColorSelected = { onBlockChange(block.copy(bgColorHex = it)) }
        )

        ColorPickerPreview(
            label = "Color de Texto",
            selectedColorHex = block.textColorHex ?: "#FFFFFF",
            onColorSelected = { onBlockChange(block.copy(textColorHex = it)) }
        )
    }
}

@Composable
fun ColorHexTable(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#FFFFFF", "#000000", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF",
        "#808080", "#800000", "#808000", "#008000", "#800080", "#008080", "#000080",
        "#D0BCFF", "#8AB4F8", "#81C784", "#E57373", "#FFB74D", "#131720", "#1E1C24", "#3B2E5C", "#233B5E"
    )

    var showCustomHexDialog by remember { mutableStateOf(false) }

    Column {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(40.dp),
            modifier = Modifier.height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(colors) { colorHex ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(colorHex)))
                        .border(
                            width = if (selectedColorHex.uppercase() == colorHex.uppercase()) 2.dp else 1.dp,
                            color = if (selectedColorHex.uppercase() == colorHex.uppercase()) GeminiCyanAccent else CosmicBorder,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorHex) }
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CosmicSurfaceVariant)
                        .border(1.dp, CosmicBorder, CircleShape)
                        .clickable { showCustomHexDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Palette, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showCustomHexDialog) {
        var hexInput by remember { mutableStateOf(selectedColorHex) }
        AlertDialog(
            onDismissRequest = { showCustomHexDialog = false },
            containerColor = CosmicSurface,
            title = { Text("Color Hex Personalizado", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("Hex (ej: #FF0000)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (hexInput.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))) {
                        onColorSelected(hexInput)
                        showCustomHexDialog = false
                    }
                }) { Text("Aplicar") }
            }
        )
    }
}

@Composable
fun ColorPickerPreview(
    label: String,
    selectedColorHex: String,
    onColorSelected: (String) -> Unit
) {
    HexColorBox(
        selectedColorHex = selectedColorHex,
        onColorSelected = onColorSelected,
        label = label,
        compact = false
    )
}

fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun getFileSize(context: android.content.Context, uri: android.net.Uri): String? {
    var size: Long = 0
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    if (cursor != null && cursor.moveToFirst()) {
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (sizeIndex != -1) {
            size = cursor.getLong(sizeIndex)
        }
        cursor.close()
    }
    if (size <= 0) return null
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getUriBase64AndMime(context: android.content.Context, uri: android.net.Uri): Pair<String, String>? {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        if (bytes != null) {
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Pair(base64, mimeType)
        } else null
    } catch (e: Exception) {
        android.util.Log.e("ChatbotUI", "Failed to encode uri to base64", e)
        null
    }
}

// Helper JSON serialization for chatbot conversations
fun serializeChatMessages(messages: List<Pair<String, Boolean>>): String {
    val array = org.json.JSONArray()
    for (pair in messages) {
        val obj = org.json.JSONObject()
        obj.put("text", pair.first)
        obj.put("isUser", pair.second)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeChatMessages(json: String): List<Pair<String, Boolean>> {
    val list = mutableListOf<Pair<String, Boolean>>()
    try {
        val array = org.json.JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(obj.getString("text") to obj.getBoolean("isUser"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

// Format note mentions with underlines and click listener
@Composable
fun formatMessageText(
    text: String,
    notesList: List<NoteEntity>
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val words = text.split("(?<=\\s)|(?=\\s)|(?<=\\n)|(?=\\n)".toRegex())
        for (word in words) {
            if (word.startsWith("@") && word.length > 1) {
                // Remove trailing punctuation for title matching
                val cleanWord = word.substring(1).trimEnd { !it.isLetterOrDigit() && it != ' ' }
                val matchingNote = notesList.find { it.title.equals(cleanWord, ignoreCase = true) }
                if (matchingNote != null) {
                    val start = this.length
                    append(word)
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            color = Color(0xFF907CFF),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        ),
                        start = start,
                        end = this.length
                    )
                    addStringAnnotation(
                        tag = "NOTE_LINK",
                        annotation = matchingNote.id,
                        start = start,
                        end = this.length
                    )
                } else {
                    append(word)
                }
            } else {
                append(word)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatbotUI(viewModel: AetherViewModel, onDismiss: () -> Unit) {
    var message by remember { mutableStateOf("") }
    val messagesList by viewModel.chatMessages.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentChatSessionId.collectAsStateWithLifecycle()
    val isSending by viewModel.isChatbotSending.collectAsStateWithLifecycle()
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()
    val modelDownloadStatus by viewModel.modelVerifier.status.collectAsStateWithLifecycle()
    val llmModelState by viewModel.localLlm.modelState.collectAsStateWithLifecycle()

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = CosmicSurfaceVariant,
            title = { Text("Ajustes de IA Local (Qwen)", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Cuenta actual:", color = TextSecondary, fontSize = 14.sp)
                    Text(currentEmail ?: "No has iniciado sesión", color = TextPrimary, fontWeight = FontWeight.Bold)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = GeminiCyanAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Qwen2.5-1.5B (Local)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            when (val status = modelDownloadStatus) {
                                is ModelDownloadStatus.Ready -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Modelo descargado (~1.0 GB)", color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Inferencia 100% offline y privada en tu dispositivo vía llama.cpp.", color = TextSecondary, fontSize = 12.sp)
                                    
                                    if (llmModelState !is LlmModelState.Ready) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch { viewModel.localLlm.ensureModelLoaded(viewModel.modelVerifier.modelFile) }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GeminiBlue),
                                            border = BorderStroke(1.dp, GeminiBlue)
                                        ) {
                                            Text("Cargar en Memoria RAM", fontSize = 12.sp)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Estado: Cargado en memoria RAM listo para responder.", color = GeminiCyanAccent, fontSize = 11.sp)
                                    }
                                }
                                is ModelDownloadStatus.Downloading -> {
                                    Text("Descargando Qwen: ${(status.progress * 100).toInt()}%", color = GeminiBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { status.progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = GeminiBlue,
                                        trackColor = CosmicBorder
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(onClick = { viewModel.modelVerifier.cancelDownload() }) {
                                        Text("Cancelar Descarga", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                                else -> {
                                    Text("Descarga el modelo Qwen2.5-1.5B para ejecutar la IA de forma local y privada sin conexión a internet.", color = TextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            scope.launch { viewModel.modelVerifier.startDownload() }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Descargar Modelo Qwen (1.5B)", color = GeminiOnPrimary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (currentEmail != null && currentEmail != "offline") {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.createDriveDatabaseStructure()
                                showSettingsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("create_drive_db_structure_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GeminiBlue),
                            border = BorderStroke(1.dp, GeminiBlue)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp), tint = GeminiBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Estructurar BD en Google Drive", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                ) {
                    Text("Cerrar", color = GeminiOnPrimary)
                }
            },
            dismissButton = {
                if (currentEmail != null) {
                    TextButton(onClick = {
                        viewModel.syncManager.disconnectDrive()
                        showSettingsDialog = false
                        onDismiss()
                    }) {
                        Text("Cerrar Sesión", color = Color.Red)
                    }
                }
            }
        )
    }


    // Collect Room state
    val allSessions by viewModel.allChatSessions.collectAsStateWithLifecycle(emptyList())
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle(emptyList())

    // TTS state
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    
    DisposableEffect(context) {
        val ttsInstance = android.speech.tts.TextToSpeech(context) { status -> }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    // Attachment State
    var attachedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var attachedImageBase64 by remember { mutableStateOf<String?>(null) }
    var attachedImageMimeType by remember { mutableStateOf<String?>(null) }
    
    // File Attachment State
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachedFileContent by remember { mutableStateOf<String?>(null) }
    var attachedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Pre-attached note from notes view
    var attachedNoteFromScreen by remember { mutableStateOf<NoteEntity?>(null) }
    val chatbotPreAttachedNote by viewModel.chatbotPreAttachedNote.collectAsStateWithLifecycle()
    LaunchedEffect(chatbotPreAttachedNote) {
        if (chatbotPreAttachedNote != null) {
            attachedNoteFromScreen = chatbotPreAttachedNote
        }
    }

    // Pre-attached paragraph citation from notes view
    var attachedTextFromScreen by remember { mutableStateOf<String?>(null) }
    val chatbotPreAttachedText by viewModel.chatbotPreAttachedText.collectAsStateWithLifecycle()
    LaunchedEffect(chatbotPreAttachedText) {
        if (chatbotPreAttachedText != null) {
            attachedTextFromScreen = chatbotPreAttachedText
        }
    }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showHistoryBottomSheet by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    // Pick visual media launcher
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            attachedImageUri = uri
            val result = getUriBase64AndMime(context, uri)
            if (result != null) {
                attachedImageBase64 = result.first
                attachedImageMimeType = result.second
            }
        }
    }

    // Pick document file launcher
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                val contentResolver = context.contentResolver
                var name = "archivo.txt"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val text = if (name.endsWith(".pdf", ignoreCase = true)) {
                        try {
                            val reader = com.itextpdf.text.pdf.PdfReader(inputStream)
                            val numPages = reader.numberOfPages.coerceAtMost(10)
                            val extracted = StringBuilder()
                            for (p in 1..numPages) {
                                val pageText = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, p)
                                if (pageText.isNotBlank()) {
                                    extracted.append(pageText).append("\n")
                                }
                            }
                            reader.close()
                            val fullExtracted = extracted.toString().trim()
                            if (fullExtracted.isNotBlank()) fullExtracted else "Documento PDF sin texto extraíble."
                        } catch (e: Exception) {
                            "No se pudo extraer texto del PDF: ${e.message}"
                        }
                    } else {
                        inputStream.bufferedReader().use { it.readText() }
                    }
                    attachedFileContent = text.take(3500)
                    attachedFileName = name
                    attachedFileUri = uri
                }
                Toast.makeText(context, "Archivo cargado: $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Close on back button press
    androidx.activity.compose.BackHandler {
        onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .padding(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // HEADER ROW: Matching Actions Exactly!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: History Icon + New Chat Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    showHistoryBottomSheet = true
                }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Historial",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = {
                    viewModel.clearChat()
                    attachedImageUri = null
                    attachedImageBase64 = null
                    attachedImageMimeType = null
                    attachedFileName = null
                    attachedFileContent = null
                    attachedFileUri = null
                    attachedNoteFromScreen = null
                    attachedTextFromScreen = null
                    Toast.makeText(context, "Nuevo Chat Vacío", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nuevo Chat",
                        tint = TextPrimary
                    )
                }
            }

            // Center: AI Name
            Text(
                text = "Aura",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )

            // Right Group: Sound/Speaker Toggle + Settings Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    isMuted = !isMuted
                    if (isMuted) {
                        tts?.stop()
                        Toast.makeText(context, "Voz silenciada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Sonido activado", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Sonido",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = {
                    showSettingsDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = TextPrimary
                    )
                }
            }
        }

        // MESSAGES AREA / GREETING SCREEN
        if (messagesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Aura AI Sparkle",
                        tint = Color(0xFF907CFF),
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = "¿Que tienes en mente?",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messagesList.size) { index ->
                    val (msg, isUser) = messagesList[index]
                    if (isUser) {
                        // USER BUBBLE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .background(Color(0xFF322F37), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                val annotatedText = formatMessageText(msg, allNotes)
                                androidx.compose.foundation.text.ClickableText(
                                    text = annotatedText,
                                    style = TextStyle(
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic
                                    ),
                                    onClick = { offset ->
                                        annotatedText.getStringAnnotations(tag = "NOTE_LINK", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                val noteId = annotation.item
                                                val matchingNote = allNotes.find { it.id == noteId }
                                                if (matchingNote != null) {
                                                    viewModel.selectNote(matchingNote)
                                                    onDismiss()
                                                }
                                            }
                                    }
                                )
                            }
                        }
                    } else {
                        // ASSISTANT MESSAGE
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 40.dp)
                        ) {
                            val annotatedText = formatMessageText(msg, allNotes)
                            androidx.compose.foundation.text.ClickableText(
                                text = annotatedText,
                                style = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                modifier = Modifier.padding(bottom = 8.dp),
                                onClick = { offset ->
                                    annotatedText.getStringAnnotations(tag = "NOTE_LINK", start = offset, end = offset)
                                        .firstOrNull()?.let { annotation ->
                                            val noteId = annotation.item
                                            val matchingNote = allNotes.find { it.id == noteId }
                                            if (matchingNote != null) {
                                                viewModel.selectNote(matchingNote)
                                                onDismiss()
                                            }
                                        }
                                }
                            )

                            // ACTION ICONS UNDER MESSAGE
                            Column(
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (!isMuted) {
                                                tts?.speak(msg, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                                Toast.makeText(context, "Leyendo mensaje...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Desactiva el silencio para escuchar", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Escuchar",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg))
                                            Toast.makeText(context, "Mensaje copiado", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Gracias por tu reporte!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = "Reportar",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            var lastUserMsg: String? = null
                                            for (i in messagesList.indices.reversed()) {
                                                if (messagesList[i].second) {
                                                    lastUserMsg = messagesList[i].first
                                                    break
                                                }
                                            }
                                            if (lastUserMsg != null) {
                                                viewModel.sendChatbotMessage(lastUserMsg, lastUserMsg)
                                            }
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Regenerar",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                                                 // Export to Note Actions (Pasar a Nota / Convertir en Bloques)
                                 Row(
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.spacedBy(8.dp)
                                 ) {
                                     TextButton(
                                         onClick = {
                                             viewModel.sendChatbotMessage(
                                                 "Pasar a Nota",
                                                 "Crea una nueva nota titulada 'Resumen de IA' basada en el siguiente contenido usando el comando:\n[CREATE_NOTE_START]\nTITLE: Resumen de IA\nCONTENT_START\n$msg\nCONTENT_END\n[CREATE_NOTE_END]"
                                             )
                                             Toast.makeText(context, "Pasando a nota...", Toast.LENGTH_SHORT).show()
                                             onDismiss()
                                         },
                                         contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                         colors = ButtonDefaults.textButtonColors(contentColor = GeminiCyanAccent)
                                     ) {
                                         Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                                         Spacer(modifier = Modifier.width(3.dp))
                                         Text("Pasar a Nota", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     }

                                     TextButton(
                                         onClick = {
                                             viewModel.sendChatbotMessage(
                                                 "Convertir en Bloques",
                                                 "Crea una nueva nota estructurada y formateada en bloques visuales (títulos, listas, viñetas) basada en el siguiente contenido usando el comando:\n[CREATE_NOTE_START]\nTITLE: Representación y Bloques de IA\nCONTENT_START\n$msg\nCONTENT_END\n[CREATE_NOTE_END]"
                                             )
                                             Toast.makeText(context, "Convirtiendo en bloques...", Toast.LENGTH_SHORT).show()
                                             onDismiss()
                                         },
                                         contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                         colors = ButtonDefaults.textButtonColors(contentColor = GeminiBlue)
                                     ) {
                                         Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(13.dp))
                                         Spacer(modifier = Modifier.width(3.dp))
                                         Text("Convertir en Bloques", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     }
                                 }

                                 Spacer(modifier = Modifier.height(6.dp))

// INPUT ROW PILL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color(0xFF907CFF), RoundedCornerShape(32.dp))
                .background(Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { showAttachmentMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Adjuntar",
                        tint = TextPrimary
                    )
                }
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false },
                    modifier = Modifier.background(CosmicSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Adjuntar Imagen", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF907CFF)) },
                        onClick = {
                            showAttachmentMenu = false
                            imagePickerLauncher.launch(arrayOf("image/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Adjuntar Archivo de Texto", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFF907CFF)) },
                        onClick = {
                            showAttachmentMenu = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Transparent Input Box using BasicTextField
            BasicTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF907CFF)),
                decorationBox = { innerTextField ->
                    if (message.isEmpty()) {
                        Text(
                            text = "Haz una pregunta o usa @nota",
                            color = TextSecondary.copy(alpha = 0.8f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Send Button
            IconButton(
                onClick = {
                    if (message.isBlank() && attachedImageUri == null && attachedFileName == null && attachedNoteFromScreen == null && attachedTextFromScreen == null) return@IconButton
                    val userMsg = message
                    var displayMsg = userMsg
                    if (displayMsg.isBlank()) {
                        displayMsg = if (attachedImageUri != null) "[Imagen]" else if (attachedFileName != null) "[Archivo]" else if (attachedNoteFromScreen != null) "[Nota]" else "[Párrafo]"
                    }

                    // Build final prompt for Gemini containing full context!
                    var finalMsgForApi = userMsg

                    // Append screen note context
                    if (attachedNoteFromScreen != null) {
                        val mdContent = convertBlocksToMarkdown(attachedNoteFromScreen!!.content)
                        finalMsgForApi += "\n\n[Contexto - Nota de pantalla (ID: \"${attachedNoteFromScreen!!.id}\"): \"${attachedNoteFromScreen!!.title}\"\nContenido en Markdown:\n$mdContent]"
                    } else if (selectedNote != null) {
                        val mdContent = convertBlocksToMarkdown(selectedNote!!.content)
                        finalMsgForApi += "\n\n[Contexto - Nota de pantalla actual (ID: \"${selectedNote!!.id}\"): \"${selectedNote!!.title}\"\nContenido en Markdown:\n$mdContent]"
                    }

                    // Append paragraph citation context
                    if (attachedTextFromScreen != null) {
                        finalMsgForApi += "\n\n[Contexto - Párrafo citado de la nota:\n\"${attachedTextFromScreen}\"]"
                    }

                    // Append attached file context
                    if (attachedFileContent != null) {
                        finalMsgForApi += "\n\n[Contexto - Archivo Adjunto \"${attachedFileName}\":\n${attachedFileContent}]"
                    }

                    // Append inline @ mentioned notes context!
                    allNotes.forEach { note ->
                        if (userMsg.contains("@${note.title}", ignoreCase = true)) {
                            val mdContent = convertBlocksToMarkdown(note.content)
                            finalMsgForApi += "\n\n[Contexto - Nota Mencionada (ID: \"${note.id}\") \"${note.title}\":\n$mdContent]"
                        }
                    }

                    val imgB64 = attachedImageBase64
                    val imgMime = attachedImageMimeType

                    // Clear attachments
                    attachedImageUri = null
                    attachedImageBase64 = null
                    attachedImageMimeType = null
                    attachedFileName = null
                    attachedFileContent = null
                    attachedFileUri = null
                    attachedNoteFromScreen = null
                    attachedTextFromScreen = null
                    message = ""

                    viewModel.sendChatbotMessage(
                        userDisplayMsg = displayMsg,
                        promptWithContext = finalMsgForApi,
                        imgB64 = imgB64,
                        imgMime = imgMime
                    )
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF322F37), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "Enviar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Modal Bottom Sheet for Chat Session History!
    if (showHistoryBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistoryBottomSheet = false },
            containerColor = CosmicSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historial de chats",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = {
                        allSessions.forEach { viewModel.deleteChatSession(it) }
                        viewModel.clearChat()
                        showHistoryBottomSheet = false
                        Toast.makeText(context, "Todo el historial borrado", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Borrar todo", color = Color(0xFFFF897D), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (allSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay conversaciones anteriores.", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allSessions.size) { idx ->
                            val session = allSessions[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                                    .background(CosmicSurfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.setChatSession(session.id, deserializeChatMessages(session.messagesJson))
                                        showHistoryBottomSheet = false
                                        Toast.makeText(context, "Conversación cargada", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(fontStyle = FontStyle.Italic)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))
                                    Text(
                                        text = dateStr,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteChatSession(session)
                                        if (currentSessionId == session.id) {
                                            viewModel.clearChat()
                                        }
                                        Toast.makeText(context, "Conversación eliminada", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Borrar",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

}
}
}
}
}

fun renderPdfFirstPage(context: android.content.Context, pdfFile: java.io.File): String? {
    try {
        val parcelFileDescriptor = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
        if (pdfRenderer.pageCount > 0) {
            val page = pdfRenderer.openPage(0)
            val targetWidth = 600
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()
            
            val bitmap = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            val coverDir = java.io.File(context.filesDir, "pdf_covers").apply { mkdirs() }
            val coverFile = java.io.File(coverDir, "cover_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(coverFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            pdfRenderer.close()
            parcelFileDescriptor.close()
            return coverFile.absolutePath
        }
        pdfRenderer.close()
        parcelFileDescriptor.close()
    } catch (e: Exception) {
        android.util.Log.e("PDFRender", "Failed to render PDF cover", e)
    }
    return null
}
}

sealed class ExtractedElement {
    abstract val y: Float
    
    data class Text(
        val text: String,
        val x: Float,
        override val y: Float,
        val fontSize: Float = 12f,
        val isBold: Boolean = false
    ) : ExtractedElement()
    
    data class Image(
        val imagePath: String,
        override val y: Float
    ) : ExtractedElement()
}

class PageElementExtractor(
    private val context: android.content.Context,
    private val imageDir: java.io.File,
    private var imgCounter: Int = 0
) : com.itextpdf.text.pdf.parser.RenderListener {
    
    val elements = mutableListOf<ExtractedElement>()
    
    override fun beginTextBlock() {}
    
    override fun renderText(renderInfo: com.itextpdf.text.pdf.parser.TextRenderInfo) {
        val text = renderInfo.getText()
        if (!text.isNullOrEmpty()) {
            val startPoint = renderInfo.getBaseline()?.getStartPoint()
            val x = startPoint?.get(0) ?: 0f
            val y = startPoint?.get(1) ?: 0f

            val fontHeight = try {
                val ascent = renderInfo.getAscentLine()?.getStartPoint()?.get(1) ?: 0f
                val descent = renderInfo.getDescentLine()?.getStartPoint()?.get(1) ?: 0f
                Math.abs(ascent - descent)
            } catch (e: Exception) { 12f }

            val fontName = try {
                renderInfo.getFont()?.getPostscriptFontName() ?: ""
            } catch (e: Exception) { "" }

            val isBold = fontName.contains("bold", ignoreCase = true) ||
                         fontName.contains("black", ignoreCase = true) ||
                         fontName.contains("heavy", ignoreCase = true)

            elements.add(ExtractedElement.Text(text, x, y, fontHeight, isBold))
        }
    }
    
    override fun endTextBlock() {}
    
    override fun renderImage(renderInfo: com.itextpdf.text.pdf.parser.ImageRenderInfo) {
        try {
            val imageObj = renderInfo.getImage()
            if (imageObj != null) {
                val imageBytes = imageObj.getImageAsBytes()
                if (imageBytes != null && imageBytes.size >= 800) {
                    val fileType = imageObj.getFileType() ?: "png"
                    val ext = if (fileType.equals("jpg", ignoreCase = true) || fileType.equals("jpeg", ignoreCase = true)) "jpg" else "png"
                    
                    val imageFile = java.io.File(imageDir, "img_${System.currentTimeMillis()}_${imgCounter++}.$ext")
                    java.io.FileOutputStream(imageFile).use { out ->
                        out.write(imageBytes)
                    }
                    
                    val startPoint = renderInfo.getStartPoint()
                    val y = startPoint?.get(1) ?: 0f
                    
                    elements.add(ExtractedElement.Image(imageFile.absolutePath, y))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PDFImport", "Error extracting image in PageElementExtractor", e)
        }
    }
}

fun processPageElements(elements: List<ExtractedElement>): List<EditorBlock> {
    val textElements = elements.filterIsInstance<ExtractedElement.Text>()
    val imageElements = elements.filterIsInstance<ExtractedElement.Image>()
    
    val lines = mutableListOf<ExtractedElement.Text>()
    val sortedTexts = textElements.sortedWith(compareByDescending<ExtractedElement.Text> { it.y }.thenBy { it.x })
    
    val currentLineChunks = mutableListOf<ExtractedElement.Text>()
    for (chunk in sortedTexts) {
        if (currentLineChunks.isEmpty()) {
            currentLineChunks.add(chunk)
        } else {
            val lastY = currentLineChunks.last().y
            if (Math.abs(lastY - chunk.y) < 5.0f) {
                currentLineChunks.add(chunk)
            } else {
                lines.add(mergeChunksToLine(currentLineChunks))
                currentLineChunks.clear()
                currentLineChunks.add(chunk)
            }
        }
    }
    if (currentLineChunks.isNotEmpty()) {
        lines.add(mergeChunksToLine(currentLineChunks))
    }
    
    val combinedList = mutableListOf<ExtractedElement>()
    combinedList.addAll(lines)
    combinedList.addAll(imageElements)
    
    val sortedCombined = combinedList.sortedByDescending { it.y }
    val blocks = mutableListOf<EditorBlock>()
    
    val currentParagraph = java.lang.StringBuilder()
    var lastLineY: Float? = null
    var lastLineFontSize: Float = 12f

    fun flushParagraph() {
        if (currentParagraph.isNotEmpty()) {
            val pText = currentParagraph.toString().trim()
            if (pText.isNotEmpty()) {
                blocks.add(EditorBlock.Text(content = pText, fontSize = 15))
            }
            currentParagraph.setLength(0)
        }
    }

    for (element in sortedCombined) {
        when (element) {
            is ExtractedElement.Text -> {
                val rawText = element.text.trim()
                if (rawText.isEmpty()) continue

                // 1. Eliminar subtítulo "PDF importado:" y variantes
                if (rawText.startsWith("PDF Importado", ignoreCase = true) ||
                    rawText.startsWith("PDF importado", ignoreCase = true) ||
                    rawText.equals("PDF Importado:", ignoreCase = true)) {
                    continue
                }

                // 2. Eliminar subtítulo "contenido extraído del PDF" y variantes
                if (rawText.contains(Regex("contenido extra[íi]do del PDF", RegexOption.IGNORE_CASE))) {
                    continue
                }

                // 3. Eliminar contadores de páginas y pie de página ("Inicio 1", "Inicio 2", "nombredelarchivo1", "''2", "''3", etc.)
                val isFooterZone = element.y < 50f
                val isHeaderZone = element.y > 760f
                val isPureNumberOrQuoted = rawText.matches(Regex("^['\"`´«»\\[\\(\\-—–~]*\\s*\\d+\\s*['\"`´»\\]\\)\\-—–~]*$"))
                val isTitlePlusPage = rawText.matches(Regex("^[a-zA-Z0-9_áéíóúñÁÉÍÓÚÑ\\s.\\-]{1,35}\\s*\\d+$", RegexOption.IGNORE_CASE)) && (isFooterZone || isHeaderZone || rawText.length < 25)
                val isPageKeyword = rawText.matches(Regex("^(p[aá]g(ina)?\\.?|page)\\s*\\d+.*", RegexOption.IGNORE_CASE)) ||
                                    rawText.matches(Regex("^[–—\\-~]*\\s*P[aá]gina\\s+\\d+.*", RegexOption.IGNORE_CASE))

                if (isPureNumberOrQuoted || isTitlePlusPage || isPageKeyword || (isFooterZone && (rawText.any { it.isDigit() } || rawText.length < 20))) {
                    continue
                }

                val fSize = element.fontSize
                val bold = element.isBold

                // 4. Menús desplegables / Secciones contraíbles (ej. "▼ Una nueva vida.")
                val isDropdown = rawText.startsWith("▼") || rawText.startsWith("▶") || rawText.startsWith("▾")
                if (isDropdown) {
                    flushParagraph()
                    val cleanDropdown = rawText.removePrefix("▼").removePrefix("▶").removePrefix("▾").trim()
                    blocks.add(
                        EditorBlock.Text(
                            content = cleanDropdown,
                            fontSize = 20,
                            isBold = true,
                            isHeader = true,
                            isCollapsedHeader = true,
                            isCollapsed = false
                        )
                    )
                    lastLineY = element.y
                    lastLineFontSize = fSize
                    continue
                }

                // 5. Citas o líneas destacadas (ej. "| Cuida y protege...")
                val isQuote = rawText.startsWith("| ") || rawText.startsWith("|") || rawText.startsWith("> ")
                if (isQuote) {
                    flushParagraph()
                    val cleanQuote = rawText.removePrefix("|").removePrefix(">").trim()
                    blocks.add(EditorBlock.Quote(content = cleanQuote))
                    lastLineY = element.y
                    lastLineFontSize = fSize
                    continue
                }

                // 6. Sub-viñetas (ej. "◦ ¿Soy alguien malo?...")
                val isSubBullet = rawText.startsWith("◦") || rawText.startsWith("  ◦") || rawText.startsWith("\t◦")
                if (isSubBullet) {
                    flushParagraph()
                    val cleanSub = rawText.removePrefix("◦").removePrefix("  ◦").removePrefix("\t◦").trim()
                    blocks.add(
                        EditorBlock.Text(
                            content = "  ◦ $cleanSub",
                            fontSize = 14,
                            isBullet = true
                        )
                    )
                    lastLineY = element.y
                    lastLineFontSize = fSize
                    continue
                }

                // Heading hierarchy detection (preservar títulos, subtítulos, encabezados y secciones menores)
                val isTitle = fSize >= 19.5f || (bold && fSize >= 18f && rawText.length < 100) ||
                              rawText.startsWith("# ") || rawText.startsWith("Título:", ignoreCase = true)
                val isSubtitle = !isTitle && (fSize in 16.5f..19.4f || (bold && fSize in 15.5f..17.9f && rawText.length < 100) ||
                                 rawText.startsWith("## ") || rawText.startsWith("Subtítulo:", ignoreCase = true) ||
                                 rawText.endsWith("**") ||
                                 Regex("^(Capítulo|Capitulo|Chapter)\\s+[\\dA-Za-z]+", RegexOption.IGNORE_CASE).find(rawText) != null)
                val isHeading = !isTitle && !isSubtitle && (fSize in 14.0f..16.4f ||
                                (bold && rawText.length < 90 && (Regex("^\\d+(\\.\\d+)*\\s+[A-ZÁÉÍÓÚÑ]").find(rawText) != null || rawText.all { it.isUpperCase() || !it.isLetter() })) ||
                                rawText.startsWith("### ") ||
                                listOf("Introducción", "Resumen", "Abstract", "Metodología", "Resultados", "Conclusiones", "Discusión", "Referencias", "Bibliografía", "Objetivos", "Marco Teórico", "Antecedentes", "Requisitos", "Temario", "Índice").any { rawText.equals(it, ignoreCase = true) || rawText.startsWith("$it:", ignoreCase = true) })
                val isMinorSection = !isTitle && !isSubtitle && !isHeading && (
                                     (bold && fSize in 12.5f..13.9f && rawText.length < 80) ||
                                     rawText.startsWith("#### ") ||
                                     Regex("^\\d+\\.\\d+\\.\\d+\\s+").find(rawText) != null)

                // Bullet or list item
                val isBullet = rawText.startsWith("•") || rawText.startsWith("–") || rawText.startsWith("- ") || rawText.startsWith("* ")
                val isNumbered = Regex("^\\d+[\\.\\-]\\s+(.*)").matchEntire(rawText) != null

                // Checkbox item
                val todoMatch = Regex("^[-*+]?\\s*\\[([ xX])\\]\\s*(.*)").matchEntire(rawText)
                val isUnicodeCheckbox = rawText.startsWith("☐") || rawText.startsWith("☑")

                if (isTitle || isSubtitle || isHeading || isMinorSection) {
                    flushParagraph()
                    val targetSize = when {
                        isTitle -> 24
                        isSubtitle -> 20
                        isHeading -> 18
                        else -> 16
                    }
                    val cleanText = rawText.removePrefix("#").removePrefix("#").removePrefix("#").removePrefix("#").removeSuffix("**").trim()
                    blocks.add(
                        EditorBlock.Text(
                            content = cleanText,
                            fontSize = targetSize,
                            isBold = true,
                            isHeader = true
                        )
                    )
                    lastLineY = element.y
                    lastLineFontSize = fSize
                } else if (isBullet) {
                    flushParagraph()
                    val cleanBullet = rawText.removePrefix("•").removePrefix("–").removePrefix("-").removePrefix("*").trim()
                    blocks.add(
                        EditorBlock.Text(
                            content = cleanBullet,
                            fontSize = 14,
                            isBullet = true
                        )
                    )
                    lastLineY = element.y
                    lastLineFontSize = fSize
                } else if (isNumbered) {
                    flushParagraph()
                    val match = Regex("^\\d+[\\.\\-]\\s+(.*)").matchEntire(rawText)
                    val cleanNumbered = match?.groupValues?.get(1)?.trim() ?: rawText
                    blocks.add(
                        EditorBlock.Text(
                            content = cleanNumbered,
                            fontSize = 14,
                            isNumbered = true
                        )
                    )
                    lastLineY = element.y
                    lastLineFontSize = fSize
                } else if (todoMatch != null || isUnicodeCheckbox) {
                    flushParagraph()
                    val checked = todoMatch?.groupValues?.get(1)?.equals("x", ignoreCase = true) == true || rawText.startsWith("☑")
                    val cleanTodo = if (todoMatch != null) todoMatch.groupValues[2].trim() else rawText.removePrefix("☐").removePrefix("☑").trim()
                    blocks.add(
                        EditorBlock.Todo(
                            content = cleanTodo,
                            isChecked = checked
                        )
                    )
                    lastLineY = element.y
                    lastLineFontSize = fSize
                } else {
                    val prevY = lastLineY
                    val isLineDistanceBreak = prevY != null && Math.abs(prevY - element.y) > (lastLineFontSize * 1.30f)
                    val startsWithDialogueOrPunct = rawText.startsWith("—") || rawText.startsWith("–") ||
                                                    rawText.startsWith("¿") || rawText.startsWith("¡") ||
                                                    rawText.startsWith("\"") || rawText.startsWith("“") ||
                                                    rawText.startsWith("«")
                    if (isLineDistanceBreak || (startsWithDialogueOrPunct && currentParagraph.isNotEmpty())) {
                        flushParagraph()
                    }
                    if (currentParagraph.isNotEmpty()) {
                        if (currentParagraph.endsWith("-")) {
                            currentParagraph.setLength(currentParagraph.length - 1)
                        } else {
                            currentParagraph.append(" ")
                        }
                    }
                    currentParagraph.append(rawText)
                    lastLineY = element.y
                    lastLineFontSize = fSize
                }
            }
            is ExtractedElement.Image -> {
                flushParagraph()
                blocks.add(
                    EditorBlock.Image(
                        urlOrPath = element.imagePath,
                        caption = "",
                        width = "Match",
                        height = "Wrap"
                    )
                )
                lastLineY = null
            }
        }
    }
    
    flushParagraph()
    return blocks
}

private fun mergeChunksToLine(chunks: List<ExtractedElement.Text>): ExtractedElement.Text {
    val sortedChunks = chunks.sortedBy { it.x }
    val lineText = sortedChunks.joinToString("") { it.text }
    val avgY = sortedChunks.map { it.y }.average().toFloat()
    val minX = sortedChunks.minOfOrNull { it.x } ?: 0f
    val maxFontSize = sortedChunks.maxOfOrNull { it.fontSize } ?: 12f
    val anyBold = sortedChunks.any { it.isBold }
    return ExtractedElement.Text(lineText, minX, avgY, maxFontSize, anyBold)
}

fun isSupportedTextExtension(fileName: String, mimeType: String): Boolean {
    val textExtensions = setOf(
        "txt", "text", "md", "markdown", "ini", "cfg", "conf", "json", "jsonc", "xml", "yaml", "yml", "toml", "csv", "tsv", "log", "err", "bak", "rtf", "doc", "docx", "docm", "dot", "dotx", "dotm", "wps", "wpd", "msg", "eml",
        "bat", "cmd", "ps1", "psm1", "psd1", "vbs", "vbe", "reg", "inf", "config", "resx", "sln", "csproj", "vbproj", "vba", "bas", "cls", "frm",
        "html", "htm", "xhtml", "css", "scss", "sass", "less", "js", "jsx", "ts", "tsx", "c", "cpp", "h", "hpp", "cs", "java", "kt", "kts", "py",
        "rb", "pl", "sh", "bash", "zsh", "sql", "php", "asp", "aspx", "jsp", "go", "rs", "lua", "r", "swift", "asc", "nfo", "diz", "srt", "vtt",
        "sub", "cue", "m3u", "m3u8", "diff", "patch", "rst", "tex", "ltx", "sty", "bib", "asciidoc", "adoc", "env", "dist", "properties",
        "gitignore", "gitconfig", "dockerfile", "makefile", "po", "pot", "readme", "ans", "1st", "me"
    )
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return textExtensions.contains(ext) || 
           mimeType.startsWith("text/", ignoreCase = true) || 
           mimeType == "application/json" || 
           mimeType == "application/javascript"
}

fun parseSingleTextBlock(rawText: String): EditorBlock.Text {
    var text = rawText
    var fontColor = "Normal"
    var isBold = false
    var isItalic = false
    var fontSize = 15
    var isHeader = false

    // Color tag extraction: [color:Purple]texto[/color] or [color=Purple]texto or [color:#HEX]texto
    val colorTagRegex = Regex("\\[color[:=]([A-Za-z0-9#]+)\\](.*?)(?:\\[/color\\])?", RegexOption.DOT_MATCHES_ALL)
    val colorMatch = colorTagRegex.find(text)
    if (colorMatch != null) {
        val col = colorMatch.groupValues[1]
        fontColor = if (col.startsWith("#")) col else (col[0].uppercaseChar() + col.substring(1).lowercase())
        text = text.replace(colorMatch.value, colorMatch.groupValues[2]).trim()
    } else {
        // [Purple] Texto
        val prefixColorRegex = Regex("^\\[(Purple|Blue|Green|Red|Amber|Cyan|Pink|#[0-9A-Fa-f]{6})\\]\\s*(.*)", RegexOption.IGNORE_CASE)
        val prefixMatch = prefixColorRegex.matchEntire(text)
        if (prefixMatch != null) {
            val col = prefixMatch.groupValues[1]
            fontColor = if (col.startsWith("#")) col else (col[0].uppercaseChar() + col.substring(1).lowercase())
            text = prefixMatch.groupValues[2].trim()
        }
    }

    // Bold / Italic wrapping
    if (text.startsWith("**") && text.endsWith("**") && text.length >= 4) {
        isBold = true
        text = text.removeSurrounding("**").trim()
    } else if (text.startsWith("__") && text.endsWith("__") && text.length >= 4) {
        isBold = true
        text = text.removeSurrounding("__").trim()
    }

    if (text.startsWith("*") && text.endsWith("*") && text.length >= 2) {
        isItalic = true
        text = text.removeSurrounding("*").trim()
    } else if (text.startsWith("_") && text.endsWith("_") && text.length >= 2) {
        isItalic = true
        text = text.removeSurrounding("_").trim()
    }

    // Explicit title / subtitle labels
    if (text.startsWith("Título:", ignoreCase = true) || text.startsWith("Title:", ignoreCase = true)) {
        fontSize = 24
        isBold = true
        isHeader = true
        text = text.substringAfter(":").trim()
    } else if (text.startsWith("Subtítulo:", ignoreCase = true) || text.startsWith("Subtitle:", ignoreCase = true)) {
        fontSize = 20
        isBold = true
        isHeader = true
        text = text.substringAfter(":").trim()
    }

    return EditorBlock.Text(
        content = text,
        fontSize = fontSize,
        fontColor = fontColor,
        isBold = isBold,
        isItalic = isItalic,
        isHeader = isHeader
    )
}

fun parseTextContentToBlocks(textContent: String): List<EditorBlock> {
    val blocks = mutableListOf<EditorBlock>()
    if (textContent.isBlank()) return blocks

    val lines = textContent.split("\n")
    val currentParagraph = java.lang.StringBuilder()

    fun flushParagraph() {
        if (currentParagraph.isNotEmpty()) {
            val trimmed = currentParagraph.toString().trim()
            if (trimmed.isNotEmpty()) {
                blocks.add(parseSingleTextBlock(trimmed))
            }
            currentParagraph.setLength(0)
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()

        // Empty line handling: flushes paragraph to give spacing
        if (line.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // 1. Markdown Table
        if (line.startsWith("|") && line.endsWith("|") && line.length > 2) {
            flushParagraph()
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            val rows = mutableListOf<List<String>>()
            for (tblLine in tableLines) {
                val isSeparator = tblLine.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").trim().isEmpty()
                if (isSeparator) continue
                val parts = tblLine.split("|").map { it.trim() }
                if (parts.size > 2) {
                    val cells = parts.subList(1, parts.size - 1)
                    rows.add(cells)
                }
            }
            if (rows.isNotEmpty()) {
                val maxCols = rows.maxOf { it.size }.coerceAtLeast(1)
                val numRows = rows.size
                val paddedData = rows.map { row ->
                    val padded = row.toMutableList()
                    while (padded.size < maxCols) {
                        padded.add("")
                    }
                    padded.toList()
                }
                blocks.add(EditorBlock.Table(
                    rows = numRows,
                    cols = maxCols,
                    data = paddedData,
                    headerColor = "Purple"
                ))
            }
            continue
        }

        // 2. To-Do / Checkbox item (- [ ] / - [x] / * [ ] / * [x] / + [ ] / [ ] / [x])
        val todoMatch = Regex("^[-*+]?\\s*\\[([ xX])\\]\\s*(.*)").matchEntire(line)
        if (todoMatch != null) {
            flushParagraph()
            val isChecked = todoMatch.groupValues[1].equals("x", ignoreCase = true)
            val todoContent = todoMatch.groupValues[2].trim()
            blocks.add(EditorBlock.Todo(content = todoContent, isChecked = isChecked))
            i++
            continue
        }
        val todoKeywordMatch = Regex("^(?:TODO|TAREA|CHECKBOX):\\s*(.*)", RegexOption.IGNORE_CASE).matchEntire(line)
        if (todoKeywordMatch != null) {
            flushParagraph()
            blocks.add(EditorBlock.Todo(content = todoKeywordMatch.groupValues[1].trim(), isChecked = false))
            i++
            continue
        }

        // 3. Markdown Admonitions (> [!NOTE], > [!TIP], > [!WARNING], > [!IMPORTANT], > [!CAUTION])
        val admonitionMatch = Regex("^>\\s*\\[!(NOTE|TIP|WARNING|IMPORTANT|CAUTION|INFO)\\]\\s*(.*)", RegexOption.IGNORE_CASE).matchEntire(line)
        if (admonitionMatch != null) {
            flushParagraph()
            val type = admonitionMatch.groupValues[1].uppercase()
            var content = admonitionMatch.groupValues[2].trim()
            if (content.isEmpty() && i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                i++
                content = lines[i].trim().removePrefix(">").trim()
            }
            val (emoji, col) = when (type) {
                "TIP" -> "💡" to "Purple"
                "WARNING", "CAUTION" -> "⚠️" to "Amber"
                "IMPORTANT" -> "⭐" to "Red"
                else -> "📌" to "Blue"
            }
            blocks.add(EditorBlock.Callout(emoji = emoji, colorVariant = col, content = content))
            i++
            continue
        }

        // 4. Quote (> text)
        if (line.startsWith("> ")) {
            flushParagraph()
            blocks.add(EditorBlock.Quote(content = line.substring(2).trim()))
            i++
            continue
        }

        // 5. Callout with Emojis (💡, ⚠️, 📌, 🚀, ⭐, 🔥, ℹ️, 📝, ✅)
        val calloutEmojis = listOf("💡", "⚠️", "📌", "🚀", "⭐", "🔥", "ℹ️", "📝", "✅")
        val matchingEmoji = calloutEmojis.firstOrNull { line.startsWith(it) }
        if (matchingEmoji != null) {
            flushParagraph()
            var calloutText = line.substring(matchingEmoji.length).trim()
            var variant = when (matchingEmoji) {
                "💡" -> "Purple"
                "⚠️" -> "Amber"
                "📌" -> "Blue"
                "🚀" -> "Green"
                "⭐" -> "Red"
                else -> "Purple"
            }
            val colorPrefix = Regex("^\\[(Purple|Blue|Green|Red|Amber)\\]\\s*(.*)", RegexOption.IGNORE_CASE).matchEntire(calloutText)
            if (colorPrefix != null) {
                val c = colorPrefix.groupValues[1]
                variant = c[0].uppercaseChar() + c.substring(1).lowercase()
                calloutText = colorPrefix.groupValues[2].trim()
            }
            blocks.add(EditorBlock.Callout(emoji = matchingEmoji, content = calloutText, colorVariant = variant))
            i++
            continue
        }

        // 6. Divider (---, ***, ___, --- [dotted], --- [dashed])
        if (line == "---" || line == "***" || line == "___" || line.startsWith("---") || line.startsWith("***")) {
            flushParagraph()
            val style = when {
                line.contains("dotted", ignoreCase = true) -> "Dotted"
                line.contains("dashed", ignoreCase = true) -> "Dashed"
                else -> "Solid"
            }
            blocks.add(EditorBlock.Divider(style = style))
            i++
            continue
        }

        // 7. Code block (```lang ... ```)
        if (line.startsWith("```")) {
            flushParagraph()
            val lang = line.removePrefix("```").trim().ifEmpty { "Kotlin" }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) i++
            blocks.add(EditorBlock.Code(code = codeLines.joinToString("\n"), language = lang))
            continue
        }

        // 8. Markdown Headers (#, ##, ###, ####)
        if (line.startsWith("#")) {
            flushParagraph()
            val headerLevel = line.takeWhile { it == '#' }.length
            var headerText = line.substring(headerLevel).trim()
            var fontColor = "Normal"
            val colMatch = Regex("\\[color[:=]([A-Za-z0-9#]+)\\](.*?)(?:\\[/color\\])?").find(headerText)
            if (colMatch != null) {
                val col = colMatch.groupValues[1]
                fontColor = if (col.startsWith("#")) col else (col[0].uppercaseChar() + col.substring(1).lowercase())
                headerText = headerText.replace(colMatch.value, colMatch.groupValues[2]).trim()
            }
            val fontSize = when (headerLevel) {
                1 -> 24
                2 -> 20
                3 -> 18
                else -> 16
            }
            blocks.add(EditorBlock.Text(content = headerText, isHeader = true, fontSize = fontSize, isBold = true, fontColor = fontColor))
            i++
            continue
        }

        // 9. Bullet List item (*, -, +)
        if (line.startsWith("* ") || line.startsWith("- ") || line.startsWith("+ ")) {
            flushParagraph()
            val bulletText = line.substring(2).trim()
            val parsedText = parseSingleTextBlock(bulletText)
            blocks.add(parsedText.copy(isBullet = true, fontSize = 14))
            i++
            continue
        }

        // 10. Numbered List item
        val numberedMatch = Regex("^\\d+\\.\\s+(.*)").matchEntire(line)
        if (numberedMatch != null) {
            flushParagraph()
            val numberedText = numberedMatch.groupValues[1].trim()
            val parsedText = parseSingleTextBlock(numberedText)
            blocks.add(parsedText.copy(isNumbered = true, fontSize = 14))
            i++
            continue
        }

        // 11. Parse inline Markdown Images or default Paragraph text
        val mdImageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
        val htmlImageRegex = Regex("<img[^>]+src=[\\\"']([^\\\"']+)[\\\"'][^>]*>")
        
        var remainingLine = lines[i]
        while (remainingLine.isNotEmpty()) {
            val mdMatch = mdImageRegex.find(remainingLine)
            val htmlMatch = htmlImageRegex.find(remainingLine)

            if (mdMatch != null && (htmlMatch == null || mdMatch.range.first < htmlMatch.range.first)) {
                val beforeText = remainingLine.substring(0, mdMatch.range.first)
                if (beforeText.isNotEmpty()) {
                    currentParagraph.append(beforeText)
                }
                flushParagraph()

                val caption = mdMatch.groupValues[1]
                val url = mdMatch.groupValues[2]
                blocks.add(EditorBlock.Image(
                    urlOrPath = url,
                    caption = caption.ifEmpty { "Imagen" },
                    width = "Match",
                    height = "Wrap"
                ))

                remainingLine = remainingLine.substring(mdMatch.range.last + 1)
            } else if (htmlMatch != null) {
                val beforeText = remainingLine.substring(0, htmlMatch.range.first)
                if (beforeText.isNotEmpty()) {
                    currentParagraph.append(beforeText)
                }
                flushParagraph()

                val url = htmlMatch.groupValues[1]
                val altRegex = Regex("alt=[\\\"']([^\\\"']+)[\\\"']")
                val altMatch = altRegex.find(htmlMatch.value)
                val caption = altMatch?.groupValues?.get(1) ?: "Imagen"

                blocks.add(EditorBlock.Image(
                    urlOrPath = url,
                    caption = caption,
                    width = "Match",
                    height = "Wrap"
                ))

                remainingLine = remainingLine.substring(htmlMatch.range.last + 1)
            } else {
                currentParagraph.append(remainingLine).append("\n")
                break
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

fun convertBlocksToMarkdown(content: String): String {
    val blocks = parseBlocks(content)
    val sb = java.lang.StringBuilder()
    for (block in blocks) {
        when (block) {
            is EditorBlock.Text -> {
                val prefix = when {
                    block.isHeader -> {
                        val level = when (block.fontSize) {
                            24 -> "# "
                            20 -> "## "
                            18 -> "### "
                            else -> "#### "
                        }
                        level
                    }
                    block.isBullet -> "* "
                    block.isNumbered -> "1. "
                    else -> ""
                }
                sb.append(prefix).append(block.content).append("\n")
            }
            is EditorBlock.Table -> {
                sb.append("\n")
                val maxCols = block.cols
                if (maxCols > 0) {
                    val firstRow = block.data.getOrNull(0) ?: List(maxCols) { "" }
                    sb.append("| ").append(firstRow.joinToString(" | ")).append(" |\n")
                    sb.append("| ").append(List(maxCols) { "---" }.joinToString(" | ")).append(" |\n")
                    for (r in 1 until block.rows) {
                        val row = block.data.getOrNull(r) ?: List(maxCols) { "" }
                        sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                    }
                }
                sb.append("\n")
            }
            is EditorBlock.Image -> {
                sb.append("\n![")
                  .append(block.caption.ifEmpty { "Imagen" })
                  .append("](")
                  .append(block.urlOrPath)
                  .append(")\n")
            }
            is EditorBlock.Audio -> {
                sb.append("\n[Audio: ").append(block.name).append("]\n")
            }
            is EditorBlock.Video -> {
                sb.append("\n[Video: ").append(block.title).append("]\n")
            }
            is EditorBlock.File -> {
                sb.append("\n[Archivo: ").append(block.name).append("]\n")
            }
            is EditorBlock.Todo -> {
                sb.append(if (block.isChecked) "- [x] " else "- [ ] ").append(block.content).append("\n")
            }
            is EditorBlock.Callout -> {
                sb.append("\n").append(block.emoji).append(" ").append(block.content).append("\n\n")
            }
            is EditorBlock.Quote -> {
                sb.append("> ").append(block.content).append("\n")
            }
            is EditorBlock.Divider -> {
                sb.append("\n---\n\n")
            }
            is EditorBlock.Code -> {
                sb.append("\n```").append(block.language).append("\n").append(block.code).append("\n```\n")
            }
        }
    }
    return sb.toString().trim()
}



