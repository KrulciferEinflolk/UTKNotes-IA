import sys

filepath = "app/src/main/java/com/example/ui/LibraryScreen.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add FloatingActionButton to LibraryMainScreen
old_scaffold = """    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CosmicBackground,
        bottomBar = {
            LibraryBottomNavigationBar(currentTab) { currentTab = it }
        }
    ) {"""
new_scaffold = """    var showAddBookDialog by rememberSaveable { mutableStateOf(false) }
    
    if (showAddBookDialog) {
        AlertDialog(
            onDismissRequest = { showAddBookDialog = false },
            title = { Text("Nuevo Libro") },
            text = { Text("¿Deseas crear un nuevo libro?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addBook(title = "Nuevo Libro")
                    showAddBookDialog = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CosmicBackground,
        floatingActionButton = {
            if (currentTab == NavigationTab.Home) {
                FloatingActionButton(
                    onClick = { showAddBookDialog = true },
                    containerColor = GeminiBlue,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir Libro")
                }
            }
        },
        bottomBar = {
            LibraryBottomNavigationBar(currentTab) { currentTab = it }
        }
    ) {"""
content = content.replace(old_scaffold, new_scaffold)

# 2. Add clickable to Book25D
old_grid = """        items(books, key = { it.id }) { book ->
            Book25D(book = book)
        }"""
new_grid = """        items(books, key = { it.id }) { book ->
            Book25D(book = book, modifier = Modifier.clickable { viewModel.selectBook(book) })
        }"""
content = content.replace(old_grid, new_grid)

old_carousel = """        Box(
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
            Book25D(book = books[page])
        }"""
new_carousel = """        Box(
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
            Book25D(book = books[page], modifier = Modifier.clickable { viewModel.selectBook(books[page]) })
        }"""
content = content.replace(old_carousel, new_carousel)

# 3. Implement search logic
old_search = """        Text("Resultados de búsqueda", color = TextSecondary, fontSize = 14.sp)
        // Here we could implement the search results displaying books and notes.
    }"""
new_search = """        Text("Resultados de búsqueda", color = TextSecondary, fontSize = 14.sp)
        
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
    }"""
content = content.replace(old_search, new_search)

# Pass viewModel properly
old_homescreen = """fun LibraryHomeScreen(
    books: List<BookEntity>,
    viewMode: LibraryViewMode,
    onToggleViewMode: () -> Unit
) {"""
new_homescreen = """fun LibraryHomeScreen(
    viewModel: AetherViewModel,
    books: List<BookEntity>,
    viewMode: LibraryViewMode,
    onToggleViewMode: () -> Unit
) {"""
content = content.replace(old_homescreen, new_homescreen)

content = content.replace("LibraryHomeScreen(\n                        books = books,", "LibraryHomeScreen(\n                        viewModel = viewModel,\n                        books = books,")
content = content.replace("LibraryGridView(books)", "LibraryGridView(viewModel, books)")
content = content.replace("LibraryCarouselView(books)", "LibraryCarouselView(viewModel, books)")

old_grid_sig = "fun LibraryGridView(books: List<BookEntity>) {"
new_grid_sig = "fun LibraryGridView(viewModel: AetherViewModel, books: List<BookEntity>) {"
content = content.replace(old_grid_sig, new_grid_sig)

old_carousel_sig = "fun LibraryCarouselView(books: List<BookEntity>) {"
new_carousel_sig = "fun LibraryCarouselView(viewModel: AetherViewModel, books: List<BookEntity>) {"
content = content.replace(old_carousel_sig, new_carousel_sig)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Library fixes applied.")
