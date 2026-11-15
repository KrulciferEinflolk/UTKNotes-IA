package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.memory.NoteMemoryVaultManager
import com.example.data.local.settings.AccountTransferMode
import com.example.data.model.BookEntity
import com.example.data.model.LabItemEntity
import com.example.data.model.LabItemType
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.remote.DriveSyncManager
import com.example.data.repository.NotesRepository
import com.example.ui.AetherViewModel
import com.example.ui.editor.mergeParagraphContentsWithSpace
import com.example.ui.settings.SettingsHubScreen
import com.example.ui.settings.StorageCloudSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiAccountAndMergeRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var appContext: Application

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.getSharedPreferences("utk_notes_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            AppDatabase.getDatabase(appContext).clearAllTables()
        }
    }

    @After
    fun tearDown() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            AppDatabase.getDatabase(appContext).clearAllTables()
        }
        appContext.getSharedPreferences("utk_notes_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `mergeParagraphContentsWithSpace joins paragraphs with single space and sets seam cursor`() {
        val standard = mergeParagraphContentsWithSpace("Párrafo de arriba", "párrafo actual")
        assertEquals("Párrafo de arriba párrafo actual", standard.mergedContent)
        assertEquals("Párrafo de arriba ".length, standard.cursorOffsetInMerged)

        val alreadySpaced = mergeParagraphContentsWithSpace("Final ", "Inicio")
        assertEquals("Final Inicio", alreadySpaced.mergedContent)
        assertEquals("Final ".length, alreadySpaced.cursorOffsetInMerged)

        val leadingSpace = mergeParagraphContentsWithSpace("Final", " Inicio")
        assertEquals("Final Inicio", leadingSpace.mergedContent)

        val emptyFirst = mergeParagraphContentsWithSpace("", "Solo segundo")
        assertEquals("Solo segundo", emptyFirst.mergedContent)
        assertEquals(0, emptyFirst.cursorOffsetInMerged)

        val emptySecond = mergeParagraphContentsWithSpace("Solo primero", "")
        assertEquals("Solo primero", emptySecond.mergedContent)
        assertEquals("Solo primero".length, emptySecond.cursorOffsetInMerged)
    }

    @Test
    fun `account isolation keeps offline separate from Google accounts and supports Move and Copy transfers`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.notesDao()
            val labDao = db.labDao()
            val repo = NotesRepository(dao)
            val vault = NoteMemoryVaultManager(appContext)

            // Seed legacy empty userEmail rows and an offline book + lab item
            dao.insertBook(BookEntity(id = "book_legacy", title = "Legado", userEmail = ""))
            dao.insertPage(PageEntity(id = "page_legacy", bookId = "book_legacy", title = "P1", userEmail = ""))
            dao.insertNote(NoteEntity(id = "note_legacy", pageId = "page_legacy", title = "Nota Legado", content = "Hola", userEmail = ""))
            labDao.insertLabItem(LabItemEntity(id = "lab_offline", itemType = LabItemType.WORD, termLanguage = "kal", termSpanish = "sol", userEmail = ""))

            // Seed separate Google account
            dao.insertBook(BookEntity(id = "book_alice", title = "Libro Alice", userEmail = "alice@gmail.com"))
            dao.insertPage(PageEntity(id = "page_alice", bookId = "book_alice", title = "AP1", userEmail = "alice@gmail.com"))
            dao.insertNote(NoteEntity(id = "note_alice", pageId = "page_alice", title = "Nota Alice", content = "Secreta", userEmail = "alice@gmail.com"))
            labDao.insertLabItem(LabItemEntity(id = "lab_alice", itemType = LabItemType.WORD, termLanguage = "lun", termSpanish = "luna", userEmail = "alice@gmail.com"))

            // Normalize legacy empty records strictly to "offline"
            repo.normalizeEmptyAccountRecords()
            labDao.normalizeEmptyLabItemsToOffline()

            val offlineBooks = repo.getAllBooksList("offline")
            val aliceBooks = repo.getAllBooksList("alice@gmail.com")
            assertEquals(listOf("book_legacy"), offlineBooks.map { it.id })
            assertEquals(listOf("book_alice"), aliceBooks.map { it.id })
            assertEquals(listOf("lab_offline"), labDao.getAllLabItemsFlow("offline").first().map { it.id })
            assertEquals(listOf("lab_alice"), labDao.getAllLabItemsFlow("alice@gmail.com").first().map { it.id })

            // Save memory bank on note_legacy to verify vault cloning on COPY
            vault.addKeyFact("note_legacy", "Dato clave de memoria")

            // COPY book_legacy from offline -> alice@gmail.com
            assertTrue(repo.transferBookToAccount("book_legacy", "alice@gmail.com", AccountTransferMode.COPY, vault))
            assertEquals(1, repo.getAllBooksList("offline").size)
            val aliceBooksAfterCopy = repo.getAllBooksList("alice@gmail.com")
            assertEquals(2, aliceBooksAfterCopy.size)
            assertTrue(aliceBooksAfterCopy.any { it.title == "Legado" && it.id != "book_legacy" })
            val aliceCopiedNote = repo.searchNotesList("", "alice@gmail.com").first { it.title == "Nota Legado" }
            assertEquals("Dato clave de memoria", vault.getMemoryBank(aliceCopiedNote.id).keyFacts.firstOrNull())

            // MOVE book_legacy from offline -> bob@gmail.com
            assertTrue(repo.transferBookToAccount("book_legacy", "bob@gmail.com", AccountTransferMode.MOVE, vault))
            assertEquals(0, repo.getAllBooksList("offline").size)
            assertEquals(listOf("book_legacy"), repo.getAllBooksList("bob@gmail.com").map { it.id })
            assertEquals(listOf("note_legacy"), repo.searchNotesList("", "bob@gmail.com").map { it.id })

            // MOVE individual note_alice from alice@gmail.com -> offline
            assertTrue(repo.transferNoteToAccount("note_alice", "offline", AccountTransferMode.MOVE, vault))
            assertEquals(listOf("note_alice"), repo.searchNotesList("", "offline").map { it.id })
            assertTrue(repo.searchNotesList("", "alice@gmail.com").none { it.id == "note_alice" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `NoteEditorWorkspace merge up and merge down join paragraphs with space and support Undo and Redo`() {
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(appContext, db)
        var latestSavedNote = NoteEntity(
            id = "note_merge_ui",
            pageId = "pg_1",
            title = "Prueba Fusión",
            content = serializeBlocks(
                listOf(
                    EditorBlock.Text(id = "b0", content = "Párrafo uno"),
                    EditorBlock.Text(id = "b1", content = "párrafo dos"),
                    EditorBlock.Text(id = "b2", content = "párrafo tres")
                )
            )
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = latestSavedNote,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = { latestSavedNote = it },
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            // At index 0: merge up disabled, merge down enabled
            composeTestRule.onNodeWithTag("text_block_0").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_merge_up_btn"))
            composeTestRule.onNodeWithTag("editor_merge_up_btn").assertIsNotEnabled()

            // Focus middle paragraph (index 1) and tap ⤴ (merge up)
            composeTestRule.onNodeWithTag("text_block_1").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_merge_up_btn"))
            composeTestRule.onNodeWithTag("editor_merge_up_btn").performClick()
            composeTestRule.waitForIdle()

            val afterMergeUp = parseBlocks(latestSavedNote.content)
            assertEquals(2, afterMergeUp.size)
            assertEquals("Párrafo uno párrafo dos", (afterMergeUp[0] as EditorBlock.Text).content)
            assertEquals("párrafo tres", (afterMergeUp[1] as EditorBlock.Text).content)

            // Undo restores 3 blocks
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_undo_btn"))
            composeTestRule.onNodeWithTag("editor_undo_btn").performClick()
            composeTestRule.waitForIdle()
            val afterUndo = parseBlocks(latestSavedNote.content)
            assertEquals(3, afterUndo.size)
            assertEquals("párrafo dos", (afterUndo[1] as EditorBlock.Text).content)

            // Focus middle paragraph (index 1) and tap ⤵ (merge down)
            composeTestRule.onNodeWithTag("text_block_1").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_merge_down_btn"))
            composeTestRule.onNodeWithTag("editor_merge_down_btn").performClick()
            composeTestRule.waitForIdle()

            val afterMergeDown = parseBlocks(latestSavedNote.content)
            assertEquals(2, afterMergeDown.size)
            assertEquals("Párrafo uno", (afterMergeDown[0] as EditorBlock.Text).content)
            assertEquals("párrafo dos párrafo tres", (afterMergeDown[1] as EditorBlock.Text).content)
        } finally {
            db.close()
        }
    }

    @Test
    fun `SettingsHubScreen reveals floating Switch User button and returns to welcome screen preserving data`() = runBlocking {
        val db = AppDatabase.getDatabase(appContext)
        db.notesDao().insertBook(BookEntity(id = "book_user1", title = "Cuaderno Uno", userEmail = "usuario1@gmail.com"))

        val viewModel = AetherViewModel(appContext)
        viewModel.syncManager.connectDrive("usuario1@gmail.com")
        var signedOutInvoked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsHubScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onAccountSignedOut = { signedOutInvoked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Initially hidden
        assertEquals(0, composeTestRule.onAllNodesWithTag("settings_switch_user_floating_btn").fetchSemanticsNodes().size)

        // Tap User Account card -> floating button appears below it
        composeTestRule.onNodeWithTag("settings_user_account_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_switch_user_floating_btn").assertIsDisplayed()

        // Tap "Cambiar de usuario"
        composeTestRule.onNodeWithTag("settings_switch_user_floating_btn").performClick()
        composeTestRule.waitForIdle()

        assertTrue(signedOutInvoked)
        assertFalse(viewModel.syncManager.isConnected.value)
        assertNull(viewModel.syncManager.userEmail.value)
        // Local data for usuario1 remains untouched
        assertEquals(1, db.notesDao().getAllBooks("usuario1@gmail.com").size)
    }

    @Test
    fun `StorageCloudSettingsScreen shows per-account weights and transfers book between offline and Google account`() = runBlocking {
        val db = AppDatabase.getDatabase(appContext)
        db.notesDao().insertBook(BookEntity(id = "bk_local", title = "Libro Local", userEmail = "offline"))
        db.notesDao().insertPage(PageEntity(id = "pg_local", bookId = "bk_local", title = "General", userEmail = "offline"))
        db.notesDao().insertNote(NoteEntity(id = "nt_local", pageId = "pg_local", title = "Nota Local", content = "Contenido local", userEmail = "offline"))

        db.notesDao().insertBook(BookEntity(id = "bk_cloud", title = "Libro Nube", userEmail = "alice@gmail.com"))
        db.notesDao().insertPage(PageEntity(id = "pg_cloud", bookId = "bk_cloud", title = "General", userEmail = "alice@gmail.com"))
        db.notesDao().insertNote(NoteEntity(id = "nt_cloud", pageId = "pg_cloud", title = "Nota Nube", content = "Contenido nube", userEmail = "alice@gmail.com"))

        val viewModel = AetherViewModel(appContext)
        viewModel.syncManager.connectDrive("alice@gmail.com")

        composeTestRule.setContent {
            MyApplicationTheme {
                StorageCloudSettingsScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onSignedOut = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("storage_account_chip_offline").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("storage_account_chip_alice@gmail.com").fetchSemanticsNodes().isNotEmpty()
        }

        // Switch inspected account chip to "offline"
        composeTestRule.onNodeWithTag("storage_account_chip_offline").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.inspectedAccountId.value == "offline" &&
                viewModel.storageMetrics.value.books.any { it.book.id == "bk_local" }
        }
        composeTestRule.onNodeWithTag("storage_cloud_lazy_column")
            .performScrollToNode(hasTestTag("storage_book_card_bk_local"))
        composeTestRule.waitForIdle()

        // Open Transfer dialog for Libro Local and COPY it to alice@gmail.com
        composeTestRule.onNodeWithTag("transfer_book_btn_bk_local").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_transfer_dialog").assertIsDisplayed()
        // Accessed accounts are listed directly without forcing manual email input
        assertEquals(0, composeTestRule.onAllNodesWithTag("transfer_custom_email_input").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("transfer_mode_copy").performClick()
        composeTestRule.onNodeWithTag("transfer_target_option_alice@gmail.com").performClick()
        composeTestRule.onNodeWithTag("confirm_transfer_btn").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            !viewModel.transferStatusMessage.value.isNullOrBlank()
        }
        // Stays on current inspected account after transfer, with option to jump to destination
        assertEquals("offline", viewModel.inspectedAccountId.value)
        composeTestRule.onNodeWithTag("storage_cloud_lazy_column")
            .performScrollToNode(hasTestTag("storage_transfer_status_banner"))
        composeTestRule.onNodeWithText("Libro Local", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("transfer_view_destination_btn").assertIsDisplayed()

        // Verify Room now has 2 books in alice@gmail.com and still 1 in offline
        val aliceBooks = db.notesDao().getAllBooks("alice@gmail.com")
        val offlineBooks = db.notesDao().getAllBooks("offline")
        assertEquals(1, offlineBooks.size)
        assertEquals(2, aliceBooks.size)
        assertTrue(aliceBooks.any { it.title == "Libro Local" })

        // Tap "Ver destino" jumps to destination account (alice@gmail.com) showing copied book
        composeTestRule.onNodeWithTag("transfer_view_destination_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.inspectedAccountId.value == "alice@gmail.com" &&
                viewModel.storageMetrics.value.books.size == 2
        }
        assertEquals(2, viewModel.storageMetrics.value.books.size)
    }

    @Test
    fun `DriveSyncManager blocks repeat GMS loops after cancel, saves local backup, and generates PKCE URL`() {
        runBlocking {
            val db = AppDatabase.getDatabase(appContext)
            db.notesDao().insertBook(BookEntity(id = "bk_gms", title = "Libro GMS", userEmail = "usuario.gms@gmail.com"))
            db.notesDao().insertPage(PageEntity(id = "pg_gms", bookId = "bk_gms", title = "General", userEmail = "usuario.gms@gmail.com"))
            db.notesDao().insertNote(NoteEntity(id = "nt_gms", pageId = "pg_gms", title = "Nota GMS", content = "Texto", userEmail = "usuario.gms@gmail.com"))

            val syncManager = DriveSyncManager(appContext, db)
            syncManager.connectDrive("usuario.gms@gmail.com")

            // Simulated Play Services popup instant cancel (RESULT_CANCELED, null data)
            val outcome = syncManager.handleAuthorizationResolutionResult(android.app.Activity.RESULT_CANCELED, null)
            assertTrue(outcome is com.example.data.remote.AuthResolutionOutcome.Failed)
            assertTrue(syncManager.isGmsBlockedForAccount("usuario.gms@gmail.com"))
            val diagnostic = syncManager.gmsAuthDiagnostic.value.orEmpty()
            assertFalse(diagnostic.isBlank())
            assertFalse(diagnostic.contains("SHA-1", ignoreCase = true))
            assertFalse(diagnostic.contains(appContext.packageName, ignoreCase = true))
            assertFalse(diagnostic.contains("GitHub Workflow", ignoreCase = true))
            assertFalse(diagnostic.contains("UnregisteredOnApiConsole", ignoreCase = true))
            assertFalse(diagnostic.contains(DriveSyncManager.DEFAULT_WEB_OAUTH_CLIENT_ID, ignoreCase = true))

            val backupFile = java.io.File(appContext.getExternalFilesDir(null), "utk_notes_backup_usuario.gms@gmail.com.json")
            assertTrue(backupFile.exists())

            // Background / automatic synchronize must NOT emit another GMS IntentSender popup while blocked
            syncManager.synchronize(allowInteractiveAuth = false, forceRetryGms = false)
            assertNull(syncManager.pendingAuthIntentSender.value)

            // PKCE verification for browser OAuth fallback
            val verifier = DriveSyncManager.generatePkceCodeVerifier()
            val challenge = DriveSyncManager.computePkceCodeChallenge(verifier)
            assertTrue(verifier.length >= 43)
            assertTrue(challenge.isNotBlank() && verifier != challenge)
            val oauthUrl = DriveSyncManager.buildBrowserOAuthUrl(
                clientId = "custom-client.apps.googleusercontent.com",
                redirectUri = "http://127.0.0.1:41234",
                codeChallenge = challenge,
                state = "st-123",
                loginHint = "usuario.gms@gmail.com"
            )
            assertTrue(oauthUrl.contains("code_challenge_method=S256"))
            assertTrue(oauthUrl.contains("drive.appdata"))
            assertTrue(oauthUrl.contains("login_hint=usuario.gms%40gmail.com"))

            backupFile.delete()
        }
    }

    @Test
    fun `DriveSyncManager exports and imports account JSON backup across isolated accounts`() {
        runBlocking {
            val db = AppDatabase.getDatabase(appContext)
            db.notesDao().insertBook(BookEntity(id = "bk_exp", title = "Cuaderno Exportable", userEmail = "offline"))
            db.notesDao().insertPage(PageEntity(id = "pg_exp", bookId = "bk_exp", title = "Sección 1", userEmail = "offline"))
            db.notesDao().insertNote(NoteEntity(id = "nt_exp", pageId = "pg_exp", title = "Apunte", content = "Contenido portable", userEmail = "offline"))

            val syncManager = DriveSyncManager(appContext, db)
            val tempFile = java.io.File.createTempFile("utk_export_test", ".json", appContext.cacheDir)
            try {
                val fileUri = android.net.Uri.fromFile(tempFile)
                val exportRes = syncManager.exportAccountBackupToUri("offline", fileUri)
                assertTrue(exportRes.isSuccess)
                assertTrue(tempFile.readText().contains("Cuaderno Exportable"))

                val importRes = syncManager.importAccountBackupFromUri("destino@gmail.com", fileUri)
                assertTrue(importRes.isSuccess)

                val importedBooks = db.notesDao().getAllBooks("destino@gmail.com")
                val importedNotes = db.notesDao().getAllNotes("destino@gmail.com")
                assertEquals(1, importedBooks.size)
                assertEquals("Cuaderno Exportable", importedBooks.first().title)
                assertEquals(1, importedNotes.size)
                assertEquals("Contenido portable", importedNotes.first().content)
            } finally {
                tempFile.delete()
            }
        }
    }

    @Test
    fun `StorageCloudSettingsScreen hides SHA-1, package name, and Client ID even when GMS diagnostic is active`() {
        val backupFile = java.io.File(appContext.getExternalFilesDir(null), "utk_notes_backup_privado@gmail.com.json")
        try {
            val viewModel = AetherViewModel(appContext)
            viewModel.syncManager.connectDrive("privado@gmail.com")
            runBlocking {
                viewModel.syncManager.handleAuthorizationResolutionResult(android.app.Activity.RESULT_CANCELED, null)
            }

            composeTestRule.setContent {
                MyApplicationTheme {
                    StorageCloudSettingsScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onSignedOut = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("storage_cloud_lazy_column")
                .performScrollToNode(hasTestTag("drive_diagnostic_box"))
            composeTestRule.onNodeWithTag("drive_diagnostic_box").assertIsDisplayed()
            composeTestRule.onNodeWithTag("storage_cloud_lazy_column")
                .performScrollToNode(hasTestTag("backup_export_json_btn"))
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("backup_export_json_btn").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("drive_alt_oauth_btn").fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodesWithTag("copy_apk_sha1_btn").fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodesWithTag("launch_browser_oauth_confirm_btn").fetchSemanticsNodes().size)
            composeTestRule.onNodeWithText("SHA-1", substring = true, ignoreCase = true).assertDoesNotExist()
            composeTestRule.onNodeWithText(appContext.packageName, substring = true).assertDoesNotExist()
            composeTestRule.onNodeWithText(DriveSyncManager.DEFAULT_WEB_OAUTH_CLIENT_ID, substring = true).assertDoesNotExist()
        } finally {
            backupFile.delete()
        }
    }

    @Test
    fun `DriveSyncManager falls back to GoogleAuthUtil when AuthorizationClient resolution fails and skips GIS on preferLegacyAuth`() {
        runBlocking {
            val db = AppDatabase.getDatabase(appContext)
            val syncManager = DriveSyncManager(appContext, db)
            syncManager.connectDrive("fallback.code8@gmail.com")

            // 1. If AuthorizationClient returns an Intent that fails resolution, GoogleAuthUtil fallback obtains token
            var fetchedScopes = mutableListOf<String>()
            syncManager.legacyTokenFetcher = { _, scope ->
                fetchedScopes.add(scope)
                "legacy-access-token-123"
            }
            val outcome = syncManager.handleAuthorizationResolutionResult(
                android.app.Activity.RESULT_CANCELED,
                android.content.Intent()
            )
            assertTrue(outcome is com.example.data.remote.AuthResolutionOutcome.Authorized)
            assertFalse(syncManager.isGmsBlockedForAccount("fallback.code8@gmail.com"))
            assertTrue(fetchedScopes.isNotEmpty())
            assertNull(syncManager.gmsAuthDiagnostic.value)

            // 2. If GoogleAuthUtil throws UserRecoverableAuthException during fallback, it emits recoveryIntent once without looping
            syncManager.resetGmsBlock("fallback.code8@gmail.com")
            syncManager.invalidateCachedAccessToken()
            val recoveryActionIntent = android.content.Intent("com.google.android.gms.auth.GOOGLE_SIGN_IN")
            syncManager.legacyTokenFetcher = { _, _ ->
                throw com.google.android.gms.auth.UserRecoverableAuthException("Consent required", recoveryActionIntent)
            }
            val pendingOutcome = syncManager.handleAuthorizationResolutionResult(
                android.app.Activity.RESULT_CANCELED,
                android.content.Intent()
            )
            assertTrue(pendingOutcome is com.example.data.remote.AuthResolutionOutcome.PendingRecovery)
            assertEquals(recoveryActionIntent.action, syncManager.recoveryIntent.value?.action)

            // Second attempt without forceRetryGms tries AccountPicker / GoogleSignIn fallbacks once before blocking without looping
            syncManager.clearRecoveryIntent()
            val nextOutcome = syncManager.handleAuthorizationResolutionResult(
                android.app.Activity.RESULT_CANCELED,
                android.content.Intent()
            )
            if (nextOutcome is com.example.data.remote.AuthResolutionOutcome.PendingRecovery) {
                if (syncManager.accountPickerIntent.value != null) {
                    syncManager.clearAccountPickerIntent()
                    val afterPicker = syncManager.handleAccountPickerResult(android.app.Activity.RESULT_CANCELED, null)
                    if (afterPicker is com.example.data.remote.AuthResolutionOutcome.PendingRecovery) {
                        syncManager.clearGoogleSignInIntent()
                        val blockedOutcome = syncManager.handleAuthorizationResolutionResult(
                            android.app.Activity.RESULT_CANCELED,
                            android.content.Intent()
                        )
                        assertTrue(blockedOutcome is com.example.data.remote.AuthResolutionOutcome.Failed)
                    } else {
                        assertTrue(afterPicker is com.example.data.remote.AuthResolutionOutcome.Failed)
                    }
                } else {
                    syncManager.clearGoogleSignInIntent()
                    val blockedOutcome = syncManager.handleAuthorizationResolutionResult(
                        android.app.Activity.RESULT_CANCELED,
                        android.content.Intent()
                    )
                    assertTrue(blockedOutcome is com.example.data.remote.AuthResolutionOutcome.Failed)
                }
            } else {
                assertTrue(nextOutcome is com.example.data.remote.AuthResolutionOutcome.Failed)
            }
            assertNull(syncManager.recoveryIntent.value)
            assertNull(syncManager.accountPickerIntent.value)
            assertNull(syncManager.googleSignInIntent.value)
            assertTrue(syncManager.isGmsBlockedForAccount("fallback.code8@gmail.com"))

            // 3. Cancellation of legacy recovery dialog blocks cleanly without private data
            val diag = syncManager.handleLegacyRecoveryCancellation(android.app.Activity.RESULT_CANCELED)
            assertFalse(diag.contains("SHA-1", ignoreCase = true))
            assertFalse(diag.contains(appContext.packageName, ignoreCase = true))

            // 4. AccountPicker result enforces multi-account isolation and grants token via GoogleAuthUtil
            syncManager.resetGmsBlock("fallback.code8@gmail.com")
            syncManager.legacyTokenFetcher = { account, _ ->
                assertEquals("fallback.code8@gmail.com", account.name)
                "picker-granted-token-456"
            }
            val mismatchData = android.content.Intent().putExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME, "otro@gmail.com")
            val mismatchOutcome = syncManager.handleAccountPickerResult(android.app.Activity.RESULT_OK, mismatchData)
            assertTrue(mismatchOutcome is com.example.data.remote.AuthResolutionOutcome.Failed)

            syncManager.resetGmsBlock("fallback.code8@gmail.com")
            val matchingData = android.content.Intent()
                .putExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME, "fallback.code8@gmail.com")
                .putExtra(android.accounts.AccountManager.KEY_ACCOUNT_TYPE, "com.google")
            val pickerSuccess = syncManager.handleAccountPickerResult(android.app.Activity.RESULT_OK, matchingData)
            assertTrue(pickerSuccess is com.example.data.remote.AuthResolutionOutcome.Authorized)

            java.io.File(appContext.getExternalFilesDir(null), "utk_notes_backup_fallback.code8@gmail.com.json").delete()
        }
    }
}


