package com.personal.mymail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.personal.mymail.mail.OutAttachment
import com.personal.mymail.mail.OutgoingMail
import kotlinx.coroutines.launch

private const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L

/**
 * Writes a message. [mode] is "new", "reply", "replyall" or "forward";
 * for the last three, [messageId] is the message being answered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(vm: MailViewModel, nav: NavHostController, mode: String, messageId: Long) {
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val senders = accounts.filter { it.smtpHost.isNotBlank() }

    var fromId by rememberSaveable { mutableStateOf(-1L) }
    var to by rememberSaveable { mutableStateOf("") }
    var cc by rememberSaveable { mutableStateOf("") }
    var bcc by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var showCc by rememberSaveable { mutableStateOf(false) }
    var inReplyTo by rememberSaveable { mutableStateOf("") }
    var prepared by rememberSaveable { mutableStateOf(mode == "new") }

    val attachments = remember { mutableStateListOf<OutAttachment>() }
    var working by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fill in the fields for a reply, reply all or forward (once).
    LaunchedEffect(mode, messageId) {
        if (prepared) return@LaunchedEffect
        val original = vm.repo.getMessage(messageId)
        if (original == null) {
            prepared = true
            return@LaunchedEffect
        }
        fromId = original.accountId
        val own = vm.repo.ownAddresses()
        val senderIsMe = original.fromAddress.lowercase() in own

        when (mode) {
            "reply", "replyall" -> {
                to = if (senderIsMe) {
                    extractEmails(original.toAddresses).joinToString(", ")
                } else {
                    original.fromAddress
                }
                subject = withPrefix("Re:", original.subject)
                inReplyTo = original.messageIdHeader
                if (mode == "replyall") {
                    val others = (extractEmails(original.toAddresses) +
                        extractEmails(original.ccAddresses))
                        .distinctBy { it.lowercase() }
                        .filter { it.lowercase() !in own && !it.equals(original.fromAddress, true) }
                    if (others.isNotEmpty()) {
                        cc = others.joinToString(", ")
                        showCc = true
                    }
                }
                body = quotedReply(original)
            }
            "forward" -> {
                subject = withPrefix("Fwd:", original.subject)
                body = forwardedBody(original)
                val originalAttachments = vm.repo.attachments(original.id)
                if (originalAttachments.isNotEmpty()) {
                    working = "Getting the original attachments…"
                    for (att in originalAttachments) {
                        val result = vm.repo.downloadAttachment(att)
                        val file = result.getOrNull()
                        if (file != null) {
                            attachments += OutAttachment(file, att.name, att.mimeType, file.length())
                        } else {
                            error = "Couldn't attach ${att.name}: " +
                                (result.exceptionOrNull()?.message ?: "unknown error")
                        }
                    }
                    working = null
                }
            }
        }
        prepared = true
    }

    val from = senders.firstOrNull { it.id == fromId } ?: senders.firstOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            for (uri in uris) {
                try {
                    attachments += vm.repo.importAttachment(uri)
                } catch (e: Exception) {
                    error = "Couldn't attach a file: ${e.message ?: "unknown error"}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            "reply" -> "Reply"
                            "replyall" -> "Reply all"
                            "forward" -> "Forward"
                            else -> "New message"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = working == null,
                        onClick = { picker.launch("*/*") }
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach a file")
                    }
                    IconButton(
                        enabled = from != null && to.isNotBlank() && working == null && prepared,
                        onClick = {
                            val sender = from ?: return@IconButton
                            if (attachments.sumOf { it.size } > MAX_ATTACHMENT_BYTES) {
                                error = "Attachments are over 20 MB. Most mail servers will refuse that."
                                return@IconButton
                            }
                            scope.launch {
                                working = "Sending…"
                                error = null
                                val result = vm.repo.send(
                                    sender,
                                    OutgoingMail(
                                        to = to,
                                        cc = cc,
                                        bcc = bcc,
                                        subject = subject,
                                        body = body,
                                        attachments = attachments.toList(),
                                        inReplyTo = inReplyTo.ifBlank { null }
                                    )
                                )
                                working = null
                                result.onSuccess {
                                    nav.popBackStack()
                                }.onFailure {
                                    error = "Could not send: ${it.message ?: it.javaClass.simpleName}"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            )
        }
    ) { padding ->
        if (senders.isEmpty()) {
            Text(
                "None of your accounts has an outgoing (SMTP) server set up. " +
                    "Edit an account and fill in the SMTP server to send mail.",
                modifier = Modifier.padding(padding).padding(24.dp)
            )
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("From", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                senders.forEach { acc ->
                    FilterChip(
                        selected = from?.id == acc.id,
                        onClick = { fromId = acc.id },
                        label = { Text(acc.email) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f)
                )
                if (!showCc) {
                    TextButton(onClick = { showCc = true }) { Text("Cc/Bcc") }
                }
            }
            if (showCc) {
                OutlinedTextField(
                    value = cc,
                    onValueChange = { cc = it },
                    label = { Text("Cc") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bcc,
                    onValueChange = { bcc = it },
                    label = { Text("Bcc") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            if (attachments.isNotEmpty()) {
                Text("Attachments", style = MaterialTheme.typography.labelLarge)
                attachments.toList().forEach { att ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(att.name, maxLines = 1)
                            Text(
                                formatSize(att.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { attachments.remove(att) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                        }
                    }
                }
            }

            working?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
