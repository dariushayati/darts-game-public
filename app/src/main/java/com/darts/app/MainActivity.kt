package com.darts.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@SuppressLint("SourceLockedOrientationActivity")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Phones stay portrait; tablets and TVs are free to rotate / stay landscape.
        if (!isTelevision() && resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        showStatusBar()
        ThemePreference.mode = AppSettingsStore(this).loadThemeMode()
        setContent {
            DartsTheme {
                DartsApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showStatusBar()
    }

    private fun showStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = false
        }
    }
}
