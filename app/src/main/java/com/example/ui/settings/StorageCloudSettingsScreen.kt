package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.settings.AccountStorageSummary
import com.example.data.local.settings.AccountTransferMode
import com.example.data.local.settings.BookStorageItem
import com.example.data.local.settings.StorageMetricsCalculator
import com.example.data.remote.SyncState
import com.example.ui.AetherViewModel
import com.example.ui.TransferFeedback
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiOnPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText

private data class TransferDialogRequest(
    val id: String,
    val title: String,
    val isBook: Boolean,
    val sourceAccountId: String
)

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
    val availableAccounts by viewModel.availableStorageAccounts.collectAsStateWithLifecycle()
    val inspectedAccountId by viewModel.inspectedAccountId.collectAsStateWithLifecycle()
    val transferStatusMessage by viewModel.transferStatusMessage.collectAsStateWithLifecycle()
    val transferFeedback by viewModel.transferFeedback.collectAsStateWithLifecycle()
    val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
    val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()
    val syncState by viewModel.syncManager.syncState.collectAsStateWithLifecycle()
    val gmsAuthDiagnostic by viewModel.syncManager.gmsAuthDiagnostic.collectAsStateWithLifecycle()
    val expandedBooks = remember { mutableStateMapOf<String, Boolean>() }
    var transferRequest by remember { mutableStateOf<TransferDialogRequest?>(null) }

    val activeAccountId = currentEmail?.takeIf { it.isNotBlank() } ?: "offline"
    val selectedAccountId = inspectedAccountId ?: activeAccountId
    val isInspectingActiveAccount = selectedAccountId.equals(activeAccountId, ignoreCase = true)
    val selectedAccountLabel = if (selectedAccountId == "offline") appText("Modo Local", "Local Mode") else selectedAccountId
    val activeAccountLabel = if (activeAccountId == "offline") appText("Modo Local", "Local Mode") else activeAccountId

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportAccountBackupToUri(selectedAccountId, uri)
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAccountBackupFromUri(selectedAccountId, uri)
        }
    }

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
                    .widthIn(max = 640.dp)
                    .testTag("storage_cloud_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Multi-Account Isolated Storage Selector Card
                if (availableAccounts.isNotEmpty()) {
                    item {
                        Surface(
                            color = CosmicSurface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, CosmicBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("storage_accounts_selector_card")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = GeminiBlue)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = appText("Almacenamiento por cuenta", "Per-Account Storage"),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = appText(
                                        "Cada usuario conserva su propio espacio separado. Toca una cuenta para inspeccionar su peso y mover o copiar libros y notas.",
                                        "Each user keeps separate storage. Tap an account to inspect its weight and move or copy books and notes."
                                    ),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(availableAccounts, key = { it.accountId }) { summary ->
                                        val isSelected = summary.accountId == selectedAccountId
                                        AccountStorageChip(
                                            summary = summary,
                                            isSelected = isSelected,
                                            onClick = { viewModel.selectStorageAccount(summary.accountId) }
                                        )
                                    }
                                }
                                if (!isInspectingActiveAccount) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = GeminiBlue.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, GeminiBlue.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                tint = GeminiBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = appText(
                                                    "Inspeccionando $selectedAccountLabel. Usa ⇄ para transferir hacia $activeAccountLabel u otra cuenta.",
                                                    "Inspecting $selectedAccountLabel. Tap ⇄ to transfer to $activeAccountLabel or another account."
                                                ),
                                                color = TextPrimary,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Transfer status banner with optional jump to destination account
                val activeFeedback = transferFeedback
                    ?: transferStatusMessage?.takeIf { it.isNotBlank() }?.let { TransferFeedback(it) }
                if (activeFeedback != null) {
                    item {
                        Surface(
                            color = if (activeFeedback.isError) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            } else {
                                GeminiBlue.copy(alpha = 0.14f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (activeFeedback.isError) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                                } else {
                                    GeminiBlue.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("storage_transfer_status_banner")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = if (activeFeedback.isError) MaterialTheme.colorScheme.error else GeminiBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeFeedback.message,
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearTransferStatusMessage() },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = appText("Cerrar aviso", "Dismiss"),
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                activeFeedback.targetAccountId?.let { destId ->
                                    val destLabel = if (destId == "offline") {
                                        appText("Modo Local", "Local Mode")
                                    } else {
                                        destId
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.selectStorageAccount(destId) },
                                            border = BorderStroke(1.dp, GeminiBlue),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("transfer_view_destination_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = GeminiBlue,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = appText("Ver destino ($destLabel)", "View destination"),
                                                color = GeminiBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = GeminiBlue)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = appText("Uso en el dispositivo", "Device Storage Usage"),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = selectedAccountLabel,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                                label = appText("Modelos de IA local (compartido)", "Local AI models (shared)"),
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

                // Google Drive Connection & Diagnostic Card
                item {
                    val isRealGoogleAccount = isConnected && !currentEmail.isNullOrBlank() && currentEmail != "offline"
                    val isErrorState = syncState is SyncState.Error || !gmsAuthDiagnostic.isNullOrBlank()
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
                                            "Modo offline (puedes exportar o importar copias JSON)",
                                            "Offline mode (you can export or import JSON backups)"
                                        ),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (syncState is SyncState.Success || syncState is SyncState.Error || syncState is SyncState.Syncing || !gmsAuthDiagnostic.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val statusMsg = when {
                                    syncState is SyncState.Syncing -> appText("Sincronizando con Google Drive...", "Syncing with Google Drive...")
                                    syncState is SyncState.Error -> (syncState as SyncState.Error).message
                                    !gmsAuthDiagnostic.isNullOrBlank() -> gmsAuthDiagnostic.orEmpty()
                                    syncState is SyncState.Success -> (syncState as SyncState.Success).message
                                    else -> ""
                                }
                                if (statusMsg.isNotBlank()) {
                                    Surface(
                                        color = if (isErrorState) {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        } else {
                                            GeminiBlue.copy(alpha = 0.10f)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isErrorState) {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                            } else {
                                                GeminiBlue.copy(alpha = 0.35f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("drive_diagnostic_box")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = statusMsg,
                                                color = if (isErrorState) MaterialTheme.colorScheme.error else GeminiBlue,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (isRealGoogleAccount) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.triggerDriveSync(allowInteractiveAuth = true, forceRetryGms = true) },
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
                                        onClick = { viewModel.createDriveDatabaseStructure(forceRetryGms = true) },
                                        border = BorderStroke(1.dp, GeminiBlue),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Text(appText("Carpetas Drive", "Drive Folders"), color = GeminiBlue, fontSize = 12.sp)
                                    }
                                }
                                if (viewModel.syncManager.hasCustomDesktopOAuthClient()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.launchAlternativeBrowserOAuth("", "") },
                                        border = BorderStroke(1.dp, GeminiBlue.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("drive_alt_oauth_btn")
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = appText("Autorizar Drive en navegador (OAuth alternativo)", "Authorize Drive in browser (Alt OAuth)"),
                                            color = GeminiBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.switchAccountSession()
                                        onSignedOut?.invoke()
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(appText("Cerrar sesión de Google", "Disconnect Google Account"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val safePrefix = selectedAccountId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                                        exportBackupLauncher.launch("utk_notes_respaldo_${safePrefix}.json")
                                    },
                                    border = BorderStroke(1.dp, CosmicBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("backup_export_json_btn")
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(appText("Exportar JSON", "Export JSON"), color = TextPrimary, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                    },
                                    border = BorderStroke(1.dp, CosmicBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("backup_import_json_btn")
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(appText("Importar JSON", "Import JSON"), color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Per-book & per-note Drive sync switches + transfer controls + weights
                item {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = if (isInspectingActiveAccount) {
                                appText("Libros, notas y transferencia", "Books, Notes & Transfer")
                            } else {
                                appText("Contenido de $selectedAccountLabel", "Content in $selectedAccountLabel")
                            },
                            color = GeminiBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = appText(
                                "Toca ⇄ para mover o copiar libros y notas entre cuentas, o activa los interruptores de Drive.",
                                "Tap ⇄ to move or copy books and notes between accounts, or toggle Drive backup switches."
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
                                    "Aún no hay libros o notas en esta cuenta ($selectedAccountLabel).",
                                    "There are no books or notes in this account ($selectedAccountLabel) yet."
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
                            allowDriveSwitches = isInspectingActiveAccount,
                            onToggleExpand = { expandedBooks[bookItem.book.id] = !isExpanded },
                            onToggleBookSync = { enabled ->
                                viewModel.setBookDriveSyncEnabled(bookItem.book.id, enabled)
                            },
                            onToggleNoteSync = { noteId, enabled ->
                                viewModel.setNoteDriveSyncEnabled(noteId, enabled)
                            },
                            onTransferBook = {
                                transferRequest = TransferDialogRequest(
                                    id = bookItem.book.id,
                                    title = bookItem.book.title.ifBlank { "Libro sin título" },
                                    isBook = true,
                                    sourceAccountId = selectedAccountId
                                )
                            },
                            onTransferNote = { noteItem ->
                                transferRequest = TransferDialogRequest(
                                    id = noteItem.note.id,
                                    title = noteItem.note.title.ifBlank { "Nota sin título" },
                                    isBook = false,
                                    sourceAccountId = selectedAccountId
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    transferRequest?.let { req ->
        AccountTransferDialog(
            request = req,
            availableAccounts = availableAccounts,
            preferredDestinationAccount = if (!req.sourceAccountId.equals(activeAccountId, ignoreCase = true)) activeAccountId else null,
            onDismiss = { transferRequest = null },
            onConfirmTransfer = { targetAccountId, mode ->
                if (req.isBook) {
                    viewModel.transferBookBetweenAccounts(req.id, targetAccountId, mode)
                } else {
                    viewModel.transferNoteBetweenAccounts(req.id, targetAccountId, mode)
                }
                transferRequest = null
            }
        )
    }
}

@Composable
private fun AccountStorageChip(
    summary: AccountStorageSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) GeminiBlue.copy(alpha = 0.16f) else CosmicSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) GeminiBlue else CosmicBorder
        ),
        modifier = Modifier.testTag("storage_account_chip_${summary.accountId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (summary.accountId == "offline") Icons.Default.Smartphone else Icons.Default.AccountCircle,
                contentDescription = null,
                tint = if (isSelected) GeminiBlue else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.isCurrentAccount) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = GeminiBlue.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = appText("Activa", "Active"),
                                color = GeminiBlue,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${summary.bookCount} lib · ${summary.noteCount} not · ${StorageMetricsCalculator.formatByteSize(summary.totalBytes)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AccountTransferDialog(
    request: TransferDialogRequest,
    availableAccounts: List<AccountStorageSummary>,
    preferredDestinationAccount: String?,
    onDismiss: () -> Unit,
    onConfirmTransfer: (String, AccountTransferMode) -> Unit
) {
    val destinationCandidates = remember(availableAccounts, request.sourceAccountId) {
        availableAccounts.filter { !it.accountId.equals(request.sourceAccountId, ignoreCase = true) }
    }
    val candidateKeys = remember(destinationCandidates) { destinationCandidates.map { it.accountId } }

    var selectedMode by remember(request.id) { mutableStateOf(AccountTransferMode.MOVE) }
    var useCustomEmail by remember(request.id, request.sourceAccountId, candidateKeys) {
        mutableStateOf(false)
    }
    var selectedTargetId by remember(request.id, request.sourceAccountId, candidateKeys, preferredDestinationAccount) {
        mutableStateOf(
            preferredDestinationAccount?.takeIf { pref ->
                destinationCandidates.any { it.accountId.equals(pref, ignoreCase = true) }
            } ?: destinationCandidates.firstOrNull()?.accountId ?: ""
        )
    }
    var customEmailText by remember(request.id) { mutableStateOf("") }

    val sourceLabel = if (request.sourceAccountId == "offline") appText("Modo Local", "Local Mode") else request.sourceAccountId
    val resolvedTarget = if (useCustomEmail) customEmailText.trim() else selectedTargetId.trim()
    val canConfirm = resolvedTarget.isNotBlank() &&
        !resolvedTarget.equals(request.sourceAccountId, ignoreCase = true) &&
        (!useCustomEmail || resolvedTarget.equals("offline", ignoreCase = true) || resolvedTarget.contains("@"))

    val bodyScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicSurface,
        modifier = Modifier.testTag("account_transfer_dialog"),
        title = {
            Column {
                Text(
                    text = if (request.isBook) appText("Transferir libro", "Transfer Book") else appText("Transferir nota", "Transfer Note"),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "\"${request.title}\" · ${appText("Origen", "Source")}: $sourceLabel",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(bodyScroll),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = appText("1. Acción de transferencia", "1. Transfer action"),
                    color = GeminiBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { selectedMode = AccountTransferMode.MOVE },
                        color = if (selectedMode == AccountTransferMode.MOVE) GeminiBlue.copy(alpha = 0.18f) else CosmicSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selectedMode == AccountTransferMode.MOVE) 1.5.dp else 1.dp,
                            color = if (selectedMode == AccountTransferMode.MOVE) GeminiBlue else CosmicBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("transfer_mode_move")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = if (selectedMode == AccountTransferMode.MOVE) GeminiBlue else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = appText("Mover", "Move"),
                                color = if (selectedMode == AccountTransferMode.MOVE) GeminiBlue else TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Surface(
                        onClick = { selectedMode = AccountTransferMode.COPY },
                        color = if (selectedMode == AccountTransferMode.COPY) GeminiBlue.copy(alpha = 0.18f) else CosmicSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selectedMode == AccountTransferMode.COPY) 1.5.dp else 1.dp,
                            color = if (selectedMode == AccountTransferMode.COPY) GeminiBlue else CosmicBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("transfer_mode_copy")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (selectedMode == AccountTransferMode.COPY) GeminiBlue else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = appText("Copiar", "Copy"),
                                color = if (selectedMode == AccountTransferMode.COPY) GeminiBlue else TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Text(
                    text = if (selectedMode == AccountTransferMode.MOVE) {
                        appText("Mueve el elemento a la cuenta elegida y lo quita de la cuenta origen.", "Moves the item to the target account and removes it from source.")
                    } else {
                        appText("Crea una copia independiente en la cuenta destino conservando el original.", "Creates an independent copy in the destination account while keeping the original.")
                    },
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )

                Text(
                    text = appText("2. Elige la cuenta destino en la app", "2. Choose destination account in app"),
                    color = GeminiBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )

                if (destinationCandidates.isEmpty()) {
                    Text(
                        text = appText(
                            "Aún no hay otra cuenta registrada con libros o notas. Inicia sesión con otra cuenta o escribe un correo abajo.",
                            "No other accessed accounts yet. Sign in with another account or type an email below."
                        ),
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }

                destinationCandidates.forEach { acc ->
                    val checked = !useCustomEmail && selectedTargetId.equals(acc.accountId, ignoreCase = true)
                    Surface(
                        onClick = {
                            useCustomEmail = false
                            selectedTargetId = acc.accountId
                        },
                        color = if (checked) GeminiBlue.copy(alpha = 0.14f) else CosmicSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (checked) GeminiBlue else CosmicBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_target_option_${acc.accountId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (checked) GeminiBlue else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.displayName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${acc.bookCount} lib · ${acc.noteCount} not · ${StorageMetricsCalculator.formatByteSize(acc.totalBytes)}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = { useCustomEmail = true },
                    color = if (useCustomEmail) GeminiBlue.copy(alpha = 0.14f) else CosmicSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (useCustomEmail) GeminiBlue else CosmicBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_target_custom_option")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (useCustomEmail) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (useCustomEmail) GeminiBlue else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appText("Otra cuenta de Google (escribir correo)...", "Another Google account (type email)..."),
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }

                if (useCustomEmail) {
                    OutlinedTextField(
                        value = customEmailText,
                        onValueChange = { customEmailText = it },
                        singleLine = true,
                        label = { Text(appText("Correo de Google destino", "Target Google email")) },
                        placeholder = { Text("usuario@gmail.com") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeminiBlue,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = GeminiBlue,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_custom_email_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmTransfer(resolvedTarget, selectedMode) },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_transfer_btn")
            ) {
                Text(
                    text = if (selectedMode == AccountTransferMode.MOVE) appText("Confirmar mover", "Confirm move") else appText("Confirmar copia", "Confirm copy"),
                    color = GeminiOnPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(appText("Cancelar", "Cancel"), color = TextSecondary)
            }
        }
    )
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
    allowDriveSwitches: Boolean,
    onToggleExpand: () -> Unit,
    onToggleBookSync: (Boolean) -> Unit,
    onToggleNoteSync: (String, Boolean) -> Unit,
    onTransferBook: () -> Unit,
    onTransferNote: (com.example.data.local.settings.NoteStorageItem) -> Unit
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
                IconButton(
                    onClick = onTransferBook,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_book_btn_${item.book.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = appText("Transferir libro", "Transfer book"),
                        tint = GeminiBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = appText("Ver notas", "Show notes"),
                    tint = TextSecondary
                )
                if (allowDriveSwitches) {
                    Spacer(modifier = Modifier.width(4.dp))
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
                                        if (allowDriveSwitches && !item.isDriveSyncEnabled) {
                                            appText(" · Libro excluido de Drive", " · Book excluded from Drive")
                                        } else "",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { onTransferNote(noteItem) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("transfer_note_btn_${noteItem.note.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = appText("Transferir nota", "Transfer note"),
                                    tint = GeminiBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (allowDriveSwitches) {
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
}
