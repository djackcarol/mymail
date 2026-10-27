package com.personal.mymail.ui

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.personal.mymail.data.MailMessage
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Reading a message
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(vm: MailViewModel, nav: NavHostController, messageId: Long) {
    var message by remember { mutableStateOf<MailMessage?>(null) }

    LaunchedEffect(messageId) {
        message = vm.repo.getMessage(messageId)
        vm.repo.markRead(messageId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    message?.let { m ->
                        IconButton(onClick = {
                            val subject = if (m.subject.startsWith("Re:", ignoreCase = true)) m.subject
                            else "Re: ${m.subject}"
                            nav.navigate(
                                "compose?account=${m.accountId}" +
                                    "&to=${Uri.encode(m.fromAddress)}" +
                                    "&subject=${Uri.encode(subject)}"
                            )
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val m = message
        if (m == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
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
                        Text(
                            formatFullDate(m.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(m.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun formatFullDate(millis: Long): String {
    if (millis <= 0L) return ""
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(millis))
}

// ---------------------------------------------------------------------------
// Writing a message
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    vm: MailViewModel,
    nav: NavHostController,
    presetAccount: Long,
    presetTo: String,
    presetSubject: String
) {
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val senders = accounts.filter { it.smtpHost.isNotBlank() }

    var fromId by rememberSaveable { mutableStateOf(presetAccount) }
    var to by rememberSaveable { mutableStateOf(presetTo) }
    var subject by rememberSaveable { mutableStateOf(presetSubject) }
    var body by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val from = senders.firstOrNull { it.id == fromId } ?: senders.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = from != null && to.isNotBlank() && !sending,
                        onClick = {
                            val sender = from ?: return@IconButton
                            scope.launch {
                                sending = true
                                error = null
                                val result = vm.repo.send(sender, to, subject, body)
                                sending = false
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
        } else {
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
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To (separate several with commas)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
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
                if (sending) Text("Sending…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
