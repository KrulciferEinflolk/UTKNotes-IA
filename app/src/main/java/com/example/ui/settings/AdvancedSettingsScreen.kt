package com.example.ui.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.settings.CrashReportEntry
import com.example.ui.AetherViewModel
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val reports by viewModel.crashReports.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy · HH:mm:ss", Locale.getDefault()) }

    val noLogsMsg = appText("No hay informes para copiar", "No crash reports to copy")
    val copiedMsg = appText("Informe copiado al portapapeles", "Report copied to clipboard")
    val shareTitle = appText("Compartir informe de fallos", "Share crash report")

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("advanced_settings_screen"),
        containerColor = CosmicBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appText("Avanzado", "Advanced"),
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
                            .testTag("advanced_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = appText("Volver", "Back"),
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
                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = GeminiBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appText("Informe de fallos", "Crash Reports"),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = appText(
                                    "Captura automáticamente excepciones no controladas y fallos de red/IA para diagnosticar problemas sin exponer tus claves.",
                                    "Automatically captures uncaught exceptions and AI/network failures for diagnostics without exposing API keys."
                                ),
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.recordDiagnosticTestCrash() },
                                    border = BorderStroke(1.dp, GeminiBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("crash_diagnostic_trigger_btn")
                                ) {
                                    Text(
                                        text = appText("Registrar prueba", "Test Entry"),
                                        color = GeminiBlue,
                                        fontSize = 12.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (reports.isEmpty()) {
                                            Toast.makeText(context, noLogsMsg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val allText = reports.joinToString("\n\n") {
                                                viewModel.crashReportManager.formatReportForShare(it)
                                            }
                                            clipboard.setText(AnnotatedString(allText))
                                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, CosmicBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("crash_copy_all_btn")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(appText("Copiar", "Copy"), color = TextPrimary, fontSize = 12.sp)
                                }
                            }

                            if (reports.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            val combined = reports.joinToString("\n\n") {
                                                viewModel.crashReportManager.formatReportForShare(it)
                                            }
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "UTK Notes Crash Report")
                                                putExtra(Intent.EXTRA_TEXT, combined)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, shareTitle))
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(appText("Compartir", "Share"), color = GeminiBlue, fontSize = 12.5.sp)
                                    }

                                    TextButton(
                                        onClick = { viewModel.clearAllCrashReports() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.testTag("crash_clear_all_btn")
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(appText("Limpiar (${reports.size})", "Clear (${reports.size})"), fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (reports.isEmpty()) {
                    item {
                        Surface(
                            color = CosmicSurfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CosmicBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("crash_empty_state")
                        ) {
                            Text(
                                text = appText(
                                    "Sin fallos registrados. La aplicación funciona con estabilidad.",
                                    "No crashes recorded. Everything is running smoothly."
                                ),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(reports, key = { it.id }) { entry ->
                        val isExpanded = expandedMap[entry.id] == true
                        CrashReportCard(
                            entry = entry,
                            formattedTime = dateFormat.format(Date(entry.timestamp)),
                            isExpanded = isExpanded,
                            onToggle = { expandedMap[entry.id] = !isExpanded },
                            onCopySingle = {
                                clipboard.setText(AnnotatedString(viewModel.crashReportManager.formatReportForShare(entry)))
                                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GeminiBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appText("Información del sistema", "System Information"),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "UTK Notes v1.9.01 · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                                color = TextSecondary,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "${Build.MANUFACTURER} ${Build.MODEL}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportCard(
    entry: CrashReportEntry,
    formattedTime: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCopySingle: () -> Unit
) {
    Surface(
        color = CosmicSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_report_card_${entry.id}")
            .clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.message.ifBlank { "Error" },
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        maxLines = if (isExpanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$formattedTime · ${entry.threadName}" +
                            if (entry.engineLabel.isNotBlank()) " · ${entry.engineLabel}" else "",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onCopySingle) {
                    Icon(Icons.Default.ContentCopy, contentDescription = appText("Copiar informe", "Copy report"), tint = GeminiBlue, modifier = Modifier.size(18.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = entry.stackTrace,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
