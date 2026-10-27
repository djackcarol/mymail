package com.personal.mymail.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun MyMailApp(vm: MailViewModel = viewModel()) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "inbox") {
        composable("inbox") {
            InboxScreen(vm, nav)
        }
        composable("accounts") {
            AccountsScreen(vm, nav)
        }
        composable(
            "account/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            AccountEditScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(
            "message/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            MessageScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(
            "compose?account={account}&to={to}&subject={subject}",
            arguments = listOf(
                navArgument("account") { type = NavType.LongType; defaultValue = -1L },
                navArgument("to") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            ComposeScreen(
                vm = vm,
                nav = nav,
                presetAccount = entry.arguments?.getLong("account") ?: -1L,
                presetTo = entry.arguments?.getString("to") ?: "",
                presetSubject = entry.arguments?.getString("subject") ?: ""
            )
        }
    }
}
