package com.personal.mymail.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    // Back button leaves selection mode first.
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Start fresh when switching account or folder.
    LaunchedEffect(filter, folder) { selected = emptySet() }

    // Forget selected messages that are no longer in the list (for example after a refresh).
    LaunchedEffect(messages) {
        val present = messages.map { it.id }.toSet()
        if (selected.any { it !in present }) selected = selected.filter { it in present }.toSet()
    }

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

    // With a single account there is no account row, so select it to get its folder chips.
    val folderAccountId = filter ?: accounts.singleOrNull()?.id
    LaunchedEffect(folderAccountId, accounts.size) {
        if (filter == null && accounts.size == 1) vm.setFilter(accounts.first().id)
    }

    val unreadCount = messages.count { !it.isRead }
    val baseTitle = if (filter == null) {
        "Inbox"
    } else {
        folders.firstOrNull { it.fullName == folder }?.displayName ?: "Inbox"
    }
    val title = if (unreadCount > 0) "$baseTitle ($unreadCount unread)" else baseTitle

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selected = messages.map { it.id }.toSet() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(enabled = !deleting, onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
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
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (accounts.isNotEmpty() && !selecting) {
                FloatingActionButton(onClick = { nav.navigate("compose") }) {
                    Icon(Icons.Default.Edit, contentDescription = "Write a message")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (refreshing || deleting) LinearProgressIndicator(Modifier.fillMaxWidth())

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
                        val isSelected = row.id in selected
                        MessageItem(
                            row = row,
                            accountLabel = label,
                            selecting = selecting,
                            selected = isSelected,
                            onClick = {
                                if (selecting) {
                                    selected = if (isSelected) selected - row.id else selected + row.id
                                } else {
                                    nav.navigate("message/${row.id}")
                                }
                            },
                            onLongClick = {
                                selected = if (isSelected) selected - row.id else selected + row.id
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (count == 1) "Delete 1 message?" else "Delete $count messages?") },
            text = { Text(DELETE_EXPLANATION) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = selected.toList()
                    selected = emptySet()
                    vm.deleteMessages(ids) { outcome -> vm.announceDelete(outcome) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    row: MessageRow,
    accountLabel: String?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val unread = !row.isRead
    val colors = MaterialTheme.colorScheme
    val background = when {
        selected -> colors.secondaryContainer
        unread -> colors.primaryContainer.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    // Unread: full strength and bold. Read: lighter and normal weight.
    val textColor = if (unread) colors.onSurface else colors.onSurface.copy(alpha = 0.6f)
    val weight = if (unread) FontWeight.Bold else FontWeight.Normal

    Row(
        Modifier.fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier.width(36.dp).padding(top = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else if (unread) {
                Box(Modifier.padding(top = 4.dp).size(12.dp).background(colors.primary, CircleShape))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.fromName,
                    fontWeight = weight,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (row.hasAttachments) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Has attachments",
                        tint = textColor,
                        modifier = Modifier.size(16.dp).padding(end = 2.dp)
                    )
                }
                Text(
                    formatDate(row.date),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = weight,
                    color = if (unread) colors.primary else textColor
                )
            }
            Text(
                row.subject,
                fontWeight = weight,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.snippet,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (unread) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (accountLabel != null) {
                Text(
                    accountLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
            }
        }
    }
    HorizontalDivider()
}

internal fun formatDate(millis: Long): String {
    if (millis <= 0L) return ""
    val pattern = if (DateUtils.isToday(millis)) "h:mm a" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}
