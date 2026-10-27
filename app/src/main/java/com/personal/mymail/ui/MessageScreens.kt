package com.personal.mymail.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.personal.mymail.data.AttachmentInfo
import com.personal.mymail.data.MailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(vm: MailViewModel, nav: NavHostController, messageId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var message by remember { mutableStateOf<MailMessage?>(null) }
    var attachments by remember { mutableStateOf<List<AttachmentInfo>>(emptyList()) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var pendingSave by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(messageId) {
        message = vm.repo.getMessage(messageId)
        attachments = vm.repo.attachments(messageId)
        vm.repo.markRead(messageId)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = pendingSave
        pendingSave = null
        if (uri != null && file != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                    }.isSuccess
                }
                snackbar.showSnackbar(if (saved) "Saved" else "Couldn't save the file")
            }
        }
    }

    /** Downloads the attachment (if needed), then runs [action] with the local file. */
    fun withFile(attachment: AttachmentInfo, action: (File) -> Unit) {
        scope.launch {
            busyId = attachment.id
            val result = vm.repo.downloadAttachment(attachment)
            busyId = null
            val file = result.getOrNull()
            if (file != null) {
                action(file)
            } else {
                snackbar.showSnackbar(
                    "Couldn't download: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                )
            }
        }
    }

    fun openFile(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch {
                snackbar.showSnackbar("No app on this phone can open that kind of file. Try Save.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val m = message
        if (m == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        val recipientCount = extractEmails(m.toAddresses).size + extractEmails(m.ccAddresses).size

        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SelectionContainer {
                Column {
                    Text(m.subject, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${m.fromName} <${m.fromAddress}>",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (m.toAddresses.isNotBlank()) {
                        Text(
                            "To: ${m.toAddresses}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (m.ccAddresses.isNotBlank()) {
                        Text(
                            "Cc: ${m.ccAddresses}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatFullDate(m.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { nav.navigate("compose?mode=reply&msg=${m.id}") }) {
                    Text("Reply")
                }
                if (recipientCount > 1) {
                    OutlinedButton(onClick = { nav.navigate("compose?mode=replyall&msg=${m.id}") }) {
                        Text("Reply all")
                    }
                }
                OutlinedButton(onClick = { nav.navigate("compose?mode=forward&msg=${m.id}") }) {
                    Text("Forward")
                }
            }

            if (attachments.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Attachments", style = MaterialTheme.typography.titleSmall)
                attachments.forEach { att ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(att.name, maxLines = 1)
                            val size = formatSize(att.size)
                            if (size.isNotEmpty()) {
                                Text(
                                    size,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (busyId == att.id) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                enabled = busyId == null,
                                onClick = { withFile(att) { file -> openFile(file, att.mimeType) } }
                            ) { Text("Open") }
                            TextButton(
                                enabled = busyId == null,
                                onClick = {
                                    withFile(att) { file ->
                                        pendingSave = file
                                        saveLauncher.launch(att.name)
                                    }
                                }
                            ) { Text("Save") }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SelectionContainer {
                Text(m.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
