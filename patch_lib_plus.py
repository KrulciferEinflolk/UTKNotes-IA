import sys

filepath = "app/src/main/java/com/example/ui/LibraryScreen.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Add an "add book" item at the end of the grid
old_grid = """        items(books, key = { it.id }) { book ->
            Book25D(book = book, modifier = Modifier.clickable { viewModel.selectBook(book) })
        }"""
new_grid = """        items(books, key = { it.id }) { book ->
            Book25D(book = book, modifier = Modifier.clickable { viewModel.selectBook(book) })
        }
        item {
            AddBookCard(onClick = { viewModel.addBook(title = "Nuevo Libro") })
        }"""
content = content.replace(old_grid, new_grid)

# Add an "add book" item at the end of the carousel
old_carousel = """    val pagerState = rememberPagerState(pageCount = { books.size })"""
new_carousel = """    val pagerState = rememberPagerState(pageCount = { books.size + 1 })"""
content = content.replace(old_carousel, new_carousel)

old_carousel_box = """        Box(
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
new_carousel_box = """        Box(
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
                Book25D(book = books[page], modifier = Modifier.clickable { viewModel.selectBook(books[page]) })
            } else {
                AddBookCard(onClick = { viewModel.addBook(title = "Nuevo Libro") })
            }
        }"""
content = content.replace(old_carousel_box, new_carousel_box)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Library plus button patched.")
