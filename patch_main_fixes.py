import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add back navigation logic to MainActivity onCreate
old_main = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CosmicBackground
                ) { innerPadding ->
                    LibraryMainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }"""
new_main = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CosmicBackground
                ) { innerPadding ->
                    val selectedBook by viewModel.selectedBook.collectAsStateWithLifecycle()
                    if (selectedBook == null) {
                        LibraryMainScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        androidx.activity.compose.BackHandler {
                            viewModel.selectBook(null)
                        }
                        AetherAppScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }"""
content = content.replace(old_main, new_main)

# 2. Fix the typing issue in MainActivity
# In NotePageBlockRender:
# We need to add var isFocused by remember { mutableStateOf(false) } inside the text block
text_block_start = """                            is EditorBlock.Text -> {
                                val textStyle = TextStyle("""
text_block_start_new = """                            is EditorBlock.Text -> {
                                var isFocused by remember { mutableStateOf(false) }
                                val textStyle = TextStyle("""
content = content.replace(text_block_start, text_block_start_new)

# Update the focus modifier
focus_mod_old = """.onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        selectedBlockIndex = index
                                                    }
                                                }"""
focus_mod_new = """.onFocusChanged { focusState ->
                                                    isFocused = focusState.isFocused
                                                    if (focusState.isFocused) {
                                                        selectedBlockIndex = index
                                                    }
                                                }"""
content = content.replace(focus_mod_old, focus_mod_new)

# Update the LaunchedEffect
effect_old = """                                    if (tfValue.text != expectedText) {
                                        val newSelStart = tfValue.selection.start.coerceIn(1, expectedText.length)
                                        val newSelEnd = tfValue.selection.end.coerceIn(1, expectedText.length)
                                        tfValue = tfValue.copy(
                                            text = expectedText,
                                            selection = TextRange(newSelStart, newSelEnd)
                                        )
                                    }"""
effect_new = """                                    if (tfValue.text != expectedText && !isFocused) {
                                        val newSelStart = tfValue.selection.start.coerceIn(1, expectedText.length)
                                        val newSelEnd = tfValue.selection.end.coerceIn(1, expectedText.length)
                                        tfValue = tfValue.copy(
                                            text = expectedText,
                                            selection = TextRange(newSelStart, newSelEnd)
                                        )
                                    }"""
content = content.replace(effect_old, effect_new)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Main fixes applied.")
