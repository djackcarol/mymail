package com.personal.mymail

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import com.personal.mymail.sync.Notifier
import com.personal.mymail.sync.SyncScheduler
import com.personal.mymail.ui.MyMailApp

class MainActivity : ComponentActivity() {

    /** Set when the user taps a notification; -1 means nothing to open. */
    private val messageToOpen = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Notifier.ensureChannel(this)
        SyncScheduler.apply(this)
        messageToOpen.longValue = intent?.getLongExtra(Notifier.EXTRA_MESSAGE_ID, -1L) ?: -1L

        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(Unit) {
                if (!Notifier.canNotify(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            MaterialTheme(colorScheme = colors) {
                MyMailApp(
                    openMessageId = messageToOpen.longValue,
                    onOpenHandled = { messageToOpen.longValue = -1L }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        messageToOpen.longValue = intent.getLongExtra(Notifier.EXTRA_MESSAGE_ID, -1L)
    }
}
