package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.settings.BookStorageItem
import com.example.data.local.settings.StorageMetricsCalculator
import com.example.data.remote.SyncState
import com.example.ui.AetherViewModel
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiOnPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCloudSettingsScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit,
    onSignedOut: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.refreshStorageMetrics()
    }

    val metrics by viewModel.storageMetrics.collectAsStateWithLifecycle()
    val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
    val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()
    val syncState by viewModel.syncManager.syncState.collectAsStateWithLifecycle()
    val expandedBooks = remember { mutableStateMapOf<String, Boolean>() }

    val totalAppBytes = metrics.totalLibraryBytes + metrics.totalModelsBytes + metrics.totalBackupsBytes + metrics.totalCrashLogsBytes

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_cloud_settings_screen"),
        containerColor = CosmicBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appText("Almacenamiento y nube", "Storage & Cloud"),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("storage_back_button")
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
                        onClick = { viewModel.refreshStorageMetrics() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = appText("Recalcular tamaño", "Refresh sizes"),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicBackground)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Storage Card
                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = GeminiBlue)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = appText("Uso en el dispositivo", "Device Storage Usage"),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Text(
                                    text = StorageMetricsCalculator.formatByteSize(totalAppBytes),
                                    color = GeminiBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.testTag("storage_total_size_label")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            StorageBreakdownRow(
                                label = appText("Libros, notas y adjuntos", "Books, notes & attachments"),
                                value = StorageMetricsCalculator.formatByteSize(metrics.totalLibraryBytes)
                            )
                            StorageBreakdownRow(
                                label = appText("Modelos de IA local", "Local AI models"),
                                value = StorageMetricsCalculator.formatByteSize(metrics.totalModelsBytes)
                            )
                            StorageBreakdownRow(
                                label = appText("Respaldos locales JSON", "Local JSON backups"),
                                value = StorageMetricsCalculator.formatByteSize(metrics.totalBackupsBytes)
                            )
                            StorageBreakdownRow(
                                label = appText("Registros de diagnóstico", "Diagnostic logs"),
                                value = StorageMetricsCalculator.formatByteSize(metrics.totalCrashLogsBytes)
                            )
                        }
                    }
                }

                // Google Drive Connection Card
                item {
                    val isRealGoogleAccount = isConnected && !currentEmail.isNullOrBlank() && currentEmail != "offline"
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isRealGoogleAccount) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isRealGoogleAccount) GeminiBlue else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Google Drive",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = if (isRealGoogleAccount) currentEmail.orEmpty() else appText(
                                            "Modo offline (los interruptores configuran qué subir al conectar)",
                                            "Offline mode (switches configure what syncs once connected)"
                                        ),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (syncState is SyncState.Success || syncState is SyncState.Error || syncState is SyncState.Syncing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val statusMsg = when (val s = syncState) {
                                    is SyncState.Syncing -> appText("Sincronizando con Google Drive...", "Syncing with Google Drive...")
                                    is SyncState.Success -> s.message
                                    is SyncState.Error -> s.message
                                    else -> ""
                                }
                                if (statusMsg.isNotBlank()) {
                                    Text(
                                        text = statusMsg,
                                        color = if (syncState is SyncState.Error) MaterialTheme.colorScheme.error else GeminiBlue,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (isRealGoogleAccount) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.triggerDriveSync() },
                                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("storage_sync_now_btn")
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, tint = GeminiOnPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(appText("Sincronizar ahora", "Sync now"), color = GeminiOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.createDriveDatabaseStructure() },
                                        border = BorderStroke(1.dp, GeminiBlue),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Text(appText("Carpetas Drive", "Drive Folders"), color = GeminiBlue, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.syncManager.disconnectDrive()
                                        onSignedOut?.invoke()
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(appText("Cerrar sesión de Google", "Disconnect Google Account"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Per-book & per-note Drive sync switches + weights
                item {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = appText("Sincronización selectiva con Drive", "Selective Google Drive Sync"),
                            color = GeminiBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = appText(
                                "Activa o desactiva qué libros y notas se respaldan en Google Drive y revisa su peso en la app.",
                                "Toggle which books and notes back up to Google Drive and inspect their exact app weight."
                            ),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (metrics.books.isEmpty()) {
                    item {
                        Surface(
                            color = CosmicSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CosmicBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = appText(
                                    "Aún no hay libros o notas en esta cuenta.",
                                    "There are no books or notes in this account yet."
                                ),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(metrics.books, key = { it.book.id }) { bookItem ->
                        val isExpanded = expandedBooks[bookItem.book.id] == true
                        BookDriveSyncCard(
                            item = bookItem,
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedBooks[bookItem.book.id] = !isExpanded },
                            onToggleBookSync = { enabled ->
                                viewModel.setBookDriveSyncEnabled(bookItem.book.id, enabled)
                            },
                            onToggleNoteSync = { noteId, enabled ->
                                viewModel.setNoteDriveSyncEnabled(noteId, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.5.sp)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    }
}

@Composable
private fun BookDriveSyncCard(
    item: BookStorageItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleBookSync: (Boolean) -> Unit,
    onToggleNoteSync: (String, Boolean) -> Unit
) {
    Surface(
        color = CosmicSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_book_card_${item.book.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(GeminiBlue.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = GeminiBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.book.title.ifBlank { appText("Libro sin título", "Untitled book") },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.notes.size} ${appText("notas", "notes")} · ${StorageMetricsCalculator.formatByteSize(item.totalBytes)}",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = appText("Ver notas", "Show notes"),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = item.isDriveSyncEnabled,
                    onCheckedChange = onToggleBookSync,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GeminiOnPrimary,
                        checkedTrackColor = GeminiBlue
                    ),
                    modifier = Modifier.testTag("drive_book_switch_${item.book.id}")
                )
            }

            AnimatedVisibility(visible = isExpanded && item.notes.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
                    item.notes.forEach { noteItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = noteItem.note.title.ifBlank { appText("Nota sin título", "Untitled note") },
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = StorageMetricsCalculator.formatByteSize(noteItem.sizeBytes) +
                                        if (!item.isDriveSyncEnabled) {
                                            appText(" · Libro excluido de Drive", " · Book excluded from Drive")
                                        } else "",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = noteItem.isDriveSyncEnabled,
                                enabled = item.isDriveSyncEnabled,
                                onCheckedChange = { onToggleNoteSync(noteItem.note.id, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GeminiOnPrimary,
                                    checkedTrackColor = GeminiBlue
                                ),
                                modifier = Modifier.testTag("drive_note_switch_${noteItem.note.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}
