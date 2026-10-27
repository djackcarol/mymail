package com.personal.mymail.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.personal.mymail.data.MessageRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(vm: MailViewModel, nav: NavHostController) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        status?.let {
            snackbar.showSnackbar(it)
            vm.consumeStatus()
        }
    }

    // If the account being filtered on was deleted, go back to "All".
    LaunchedEffect(accounts, filter) {
        if (filter != null && accounts.none { it.id == filter }) vm.setFilter(null)
    }

    val title = if (filter == null) {
        "Inbox"
    } else {
        folders.firstOrNull { it.fullName == folder }?.displayName ?: "Inbox"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { nav.navigate("accounts") }) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = "Accounts")
                    }
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (accounts.isNotEmpty()) {
                FloatingActionButton(onClick = { nav.navigate("compose") }) {
                    Icon(Icons.Default.Edit, contentDescription = "Write a message")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (accounts.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No email accounts yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add an IMAP or POP3 account to get started.",
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Button(onClick = { nav.navigate("account/0") }) { Text("Add account") }
                }
                return@Scaffold
            }

            if (accounts.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == null,
                        onClick = { vm.setFilter(null) },
                        label = { Text("All inboxes") }
                    )
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = filter == acc.id,
                            onClick = { vm.setFilter(acc.id) },
                            label = { Text(acc.displayName.ifBlank { acc.email }) }
                        )
                    }
                }
            }

            // With a single account there is no account row, so always allow folder switching.
            val folderAccountId = filter ?: accounts.singleOrNull()?.id
            LaunchedEffect(folderAccountId, accounts.size) {
                if (filter == null && accounts.size == 1) vm.setFilter(accounts.first().id)
            }
            if (folderAccountId != null && folders.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folders.forEach { f ->
                        FilterChip(
                            selected = folder == f.fullName,
                            onClick = { vm.setFolder(f.fullName) },
                            label = { Text(f.displayName) }
                        )
                    }
                }
            }

            if (messages.isEmpty()) {
                Text(
                    if (refreshing) "Checking for mail…" else "No messages here yet. Tap refresh to check again.",
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { row ->
                        val label = if (filter == null && accounts.size > 1) {
                            accounts.firstOrNull { it.id == row.accountId }?.email
                        } else null
                        MessageItem(row, label) { nav.navigate("message/${row.id}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(row: MessageRow, accountLabel: String?, onClick: () -> Unit) {
    val weight = if (row.isRead) FontWeight.Normal else FontWeight.Bold
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.fromName,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (row.hasAttachments) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Has attachments",
                    modifier = Modifier.size(16.dp).padding(end = 2.dp)
                )
            }
            Text(formatDate(row.date), style = MaterialTheme.typography.labelSmall)
        }
        Text(row.subject, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            row.snippet,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (accountLabel != null) {
            Text(
                accountLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider()
}

internal fun formatDate(millis: Long): String {
    if (millis <= 0L) return ""
    val pattern = if (DateUtils.isToday(millis)) "h:mm a" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}
