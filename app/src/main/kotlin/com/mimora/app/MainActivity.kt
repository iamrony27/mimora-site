package com.mimora.app

import android.Manifest
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mimora.app.data.preferences.ThemeMode
import com.mimora.app.design.WritingCanvasTheme
import com.mimora.app.feature.reminder.WritingReminder
import com.mimora.app.feature.security.AppIntegrity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mimora.app.navigation.MainNavigation
import com.mimora.app.ui.theme.MIMORATheme

class MainActivity : FragmentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                lifecycleScope.launch {
                    val container = (application as MimoraApplication).container
                    val enabled = container.preferencesRepository
                        .userPreferencesFlow.first().writingReminderEnabled
                    WritingReminder.schedule(this@MainActivity, enabled)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AppIntegrity.isSigningCertificateTrusted(this)) {
            AlertDialog.Builder(this)
                .setTitle("Integrity check failed")
                .setMessage("This copy of MIMORA was modified or re-signed. Install the official APK.")
                .setPositiveButton("Close") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        enableEdgeToEdge()
        // Keep diary content out of screenshots, screen recordings and the
        // recent-apps preview.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val container = (application as MimoraApplication).container

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val userPrefs by container.preferencesRepository.userPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.mimora.app.data.preferences.UserPreferences()
            )

            val isDarkTheme = when (userPrefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Re-arm the single daily alarm whenever the app starts, so a reboot
            // or an update never leaves the reminder unscheduled.
            LaunchedEffect(userPrefs.writingReminderEnabled) {
                WritingReminder.schedule(
                    this@MainActivity,
                    userPrefs.writingReminderEnabled
                )
            }

            // Keep status-bar icons readable when the user overrides the theme.
            LaunchedEffect(isDarkTheme) {
                WindowInsetsControllerCompat(window, window.decorView)
                    .isAppearanceLightStatusBars = !isDarkTheme
            }

            MIMORATheme(
                darkTheme = isDarkTheme,
                writingTheme = WritingCanvasTheme.fromIndex(userPrefs.writingThemeIndex)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        container = container,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }
    }
}
