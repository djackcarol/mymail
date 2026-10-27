package com.personal.mymail.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun MyMailApp(
    openMessageId: Long = -1L,
    onOpenHandled: () -> Unit = {},
    vm: MailViewModel = viewModel()
) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "inbox") {
        composable("inbox") {
            InboxScreen(vm, nav)
        }
        composable("accounts") {
            AccountsScreen(vm, nav)
        }
        composable("settings") {
            SettingsScreen(nav)
        }
        composable(
            "account/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            AccountEditScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(
            "folders/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            FoldersScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(
            "message/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            MessageScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(
            "compose?mode={mode}&msg={msg}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "new" },
                navArgument("msg") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            ComposeScreen(
                vm = vm,
                nav = nav,
                mode = entry.arguments?.getString("mode") ?: "new",
                messageId = entry.arguments?.getLong("msg") ?: -1L
            )
        }
    }

    // Opens the message when the user taps a new-mail notification.
    LaunchedEffect(openMessageId) {
        if (openMessageId > 0L) nav.navigate("message/$openMessageId")
        if (openMessageId != -1L) onOpenHandled()
    }
}
