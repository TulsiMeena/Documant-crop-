package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.data.PreferencesViewModel
import com.example.ui.navigation.ScanovaNavGraph
import com.example.ui.theme.ScanovaTheme
import com.example.util.WelcomeNotificationHelper

class MainActivity : ComponentActivity() {

    private val preferencesViewModel: PreferencesViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        WelcomeNotificationHelper.showWelcomeNotification(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WelcomeNotificationHelper.createNotificationChannel(this)
        triggerWelcomeNotification()

        setContent {
            val appTheme by preferencesViewModel.appTheme.collectAsState()

            LaunchedEffect(Unit) {
                triggerWelcomeNotification()
            }

            ScanovaTheme(themePreference = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ScanovaNavGraph(
                        preferencesViewModel = preferencesViewModel
                    )
                }
            }
        }
    }

    private fun triggerWelcomeNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    WelcomeNotificationHelper.showWelcomeNotification(this)
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            WelcomeNotificationHelper.showWelcomeNotification(this)
        }
    }
}
