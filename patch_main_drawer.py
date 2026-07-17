import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update AetherTopBar call
old_topbar = """AetherTopBar(
                    selectedBook = selectedBook,
                    selectedPage = selectedPage,
                    searchQuery = searchQuery,
                    syncState = syncState,
                    userEmail = userEmail,
                    isConnected = isConnected,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSyncClick = { viewModel.triggerDriveSync() }
                )"""

new_topbar = """AetherTopBar(
                    selectedBook = selectedBook,
                    selectedPage = selectedPage,
                    searchQuery = searchQuery,
                    syncState = syncState,
                    userEmail = userEmail,
                    isConnected = isConnected,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onBack = if (selectedPage != null) { { viewModel.clearSelectedPage() } } else null,
                    onSyncClick = { viewModel.triggerDriveSync() }
                )"""
content = content.replace(old_topbar, new_topbar)

# 2. This is tricky. I'll need to remove the ModalNavigationDrawer manually if it's too big, 
# but I can just replace the whole AetherAppScreen.

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("AetherTopBar call updated.")
