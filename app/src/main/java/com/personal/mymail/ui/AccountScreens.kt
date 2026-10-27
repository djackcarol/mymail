package com.personal.mymail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.personal.mymail.data.Account
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Account list
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: MailViewModel, nav: NavHostController) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("account/0") }) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Text("No accounts yet. Tap + to add one.", modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(accounts, key = { it.id }) { acc ->
                    ListItem(
                        headlineContent = { Text(acc.displayName.ifBlank { acc.email }) },
                        supportingContent = { Text("${acc.email} · ${acc.protocol}") },
                        trailingContent = {
                            IconButton(onClick = { toDelete = acc }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove account")
                            }
                        },
                        modifier = Modifier.clickable { nav.navigate("account/${acc.id}") }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    toDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove account?") },
            text = {
                Text(
                    "${acc.email} and its downloaded messages will be removed from this phone. " +
                        "Nothing is deleted on the mail server."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAccount(acc)
                    toDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Add / edit account
// ---------------------------------------------------------------------------

private data class Preset(
    val name: String,
    val imapHost: String,
    val pop3Host: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String
)

private val presets = listOf(
    Preset("Gmail", "imap.gmail.com", "pop.gmail.com", "smtp.gmail.com", 465, "SSL"),
    Preset("Yahoo", "imap.mail.yahoo.com", "pop.mail.yahoo.com", "smtp.mail.yahoo.com", 465, "SSL"),
    Preset("iCloud", "imap.mail.me.com", "", "smtp.mail.me.com", 587, "STARTTLS")
)

private fun defaultIncomingPort(protocol: String, security: String): Int = when {
    protocol == "IMAP" && security == "SSL" -> 993
    protocol == "IMAP" -> 143
    security == "SSL" -> 995
    else -> 110
}

private fun defaultSmtpPort(security: String): Int = when (security) {
    "SSL" -> 465
    "STARTTLS" -> 587
    else -> 25
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(vm: MailViewModel, nav: NavHostController, accountId: Long) {
    val scope = rememberCoroutineScope()

    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf("IMAP") }
    var inHost by rememberSaveable { mutableStateOf("") }
    var inPort by rememberSaveable { mutableStateOf("993") }
    var inSecurity by rememberSaveable { mutableStateOf("SSL") }
    var username by rememberSaveable { mutableStateOf("") }
    var smtpHost by rememberSaveable { mutableStateOf("") }
    var smtpPort by rememberSaveable { mutableStateOf("465") }
    var smtpSecurity by rememberSaveable { mutableStateOf("SSL") }
    var presetName by rememberSaveable { mutableStateOf("") }

    var loaded by rememberSaveable { mutableStateOf(accountId == 0L) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) {
        if (accountId != 0L && !loaded) {
            vm.repo.getAccount(accountId)?.let { a ->
                displayName = a.displayName
                email = a.email
                protocol = a.protocol
                inHost = a.incomingHost
                inPort = a.incomingPort.toString()
                inSecurity = a.incomingSecurity
                username = a.username
                smtpHost = a.smtpHost
                smtpPort = a.smtpPort.toString()
                smtpSecurity = a.smtpSecurity
            }
            loaded = true
        }
    }

    fun applyPreset(p: Preset, proto: String) {
        presetName = p.name
        protocol = proto
        inHost = if (proto == "IMAP") p.imapHost else p.pop3Host
        inSecurity = "SSL"
        inPort = defaultIncomingPort(proto, "SSL").toString()
        smtpHost = p.smtpHost
        smtpPort = p.smtpPort.toString()
        smtpSecurity = p.smtpSecurity
    }

    fun buildAccount() = Account(
        id = accountId,
        displayName = displayName.trim().ifBlank { email.trim() },
        email = email.trim(),
        protocol = protocol,
        incomingHost = inHost.trim(),
        incomingPort = inPort.toIntOrNull() ?: 0,
        incomingSecurity = inSecurity,
        username = username.trim().ifBlank { email.trim() },
        smtpHost = smtpHost.trim(),
        smtpPort = smtpPort.toIntOrNull() ?: 0,
        smtpSecurity = smtpSecurity
    )

    val valid = email.isNotBlank() &&
        inHost.isNotBlank() &&
        inPort.toIntOrNull() != null &&
        (accountId != 0L || password.isNotEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (accountId == 0L) "Add account" else "Edit account") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quick setup", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { p ->
                    FilterChip(
                        selected = presetName == p.name,
                        onClick = { applyPreset(p, protocol) },
                        label = { Text(p.name) }
                    )
                }
                FilterChip(
                    selected = presetName.isEmpty(),
                    onClick = { presetName = "" },
                    label = { Text("Other") }
                )
            }
            Text(
                "Gmail, Yahoo and iCloud need an app password (created in your account's " +
                    "security settings), not your normal password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Name for this account") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (accountId == 0L) "Password" else "Password (leave blank to keep)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Incoming mail", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                options = listOf("IMAP", "POP3"),
                selected = protocol
            ) { choice ->
                val preset = presets.firstOrNull { it.name == presetName }
                if (preset != null) {
                    applyPreset(preset, choice)
                } else {
                    protocol = choice
                    inPort = defaultIncomingPort(choice, inSecurity).toString()
                }
            }
            OutlinedTextField(
                value = inHost,
                onValueChange = { inHost = it },
                label = { Text("Server") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceRow(
                options = listOf("SSL", "STARTTLS", "NONE"),
                selected = inSecurity
            ) { choice ->
                inSecurity = choice
                inPort = defaultIncomingPort(protocol, choice).toString()
            }
            OutlinedTextField(
                value = inPort,
                onValueChange = { inPort = it.filter(Char::isDigit) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (blank = email address)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Outgoing mail (SMTP)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Leave the server blank if you only want to read mail with this account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = smtpHost,
                onValueChange = { smtpHost = it },
                label = { Text("SMTP server") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceRow(
                options = listOf("SSL", "STARTTLS", "NONE"),
                selected = smtpSecurity
            ) { choice ->
                smtpSecurity = choice
                smtpPort = defaultSmtpPort(choice).toString()
            }
            OutlinedTextField(
                value = smtpPort,
                onValueChange = { smtpPort = it.filter(Char::isDigit) },
                label = { Text("SMTP port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            message?.let {
                Text(
                    it,
                    color = if (messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = valid && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = "Testing…"
                            messageIsError = false
                            val result = vm.repo.testConnection(buildAccount(), password)
                            busy = false
                            result.onSuccess {
                                message = "Connected and signed in."
                                messageIsError = false
                            }.onFailure {
                                message = "Could not connect: ${it.message ?: it.javaClass.simpleName}"
                                messageIsError = true
                            }
                        }
                    }
                ) { Text("Test connection") }

                Button(
                    enabled = valid && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            vm.repo.saveAccount(buildAccount(), password)
                            busy = false
                            vm.refresh()
                            nav.popBackStack()
                        }
                    }
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}
