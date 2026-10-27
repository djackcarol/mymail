package com.personal.mymail.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

/** Lets the user choose which folders of an IMAP account are downloaded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(vm: MailViewModel, nav: NavHostController, accountId: Long) {
    val flow = remember(accountId) { vm.repo.allFolders(accountId) }
    val folders by flow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Pick up newly enabled folders when leaving this screen.
    DisposableEffect(Unit) {
        onDispose { vm.refresh() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders to download") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (folders.isEmpty()) {
            Text(
                "No folders yet. Go back, tap refresh on the inbox once, then open this screen again.",
                modifier = Modifier.padding(padding).padding(24.dp)
            )
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(folders, key = { it.id }) { f ->
                    ListItem(
                        headlineContent = { Text(f.displayName) },
                        supportingContent = {
                            Text(
                                when (f.role) {
                                    "INBOX" -> "Always downloaded"
                                    "SENT" -> "Sent mail"
                                    "DRAFTS" -> "Drafts"
                                    "TRASH" -> "Trash"
                                    "JUNK" -> "Junk / spam"
                                    "ARCHIVE" -> "Archive"
                                    else -> f.fullName
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = f.syncEnabled,
                                enabled = f.role != "INBOX",
                                onCheckedChange = { vm.setFolderEnabled(f, it) }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
