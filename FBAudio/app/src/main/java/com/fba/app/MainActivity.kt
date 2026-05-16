package com.fba.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fba.app.ui.DeepLink
import com.fba.app.ui.FBAApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        val deepLink = extractDeepLink(intent)
        setContent {
            FBAApp(deepLink = deepLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = extractDeepLink(intent)
        if (deepLink != null) {
            setContent {
                FBAApp(deepLink = deepLink)
            }
        }
    }

    private fun extractDeepLink(intent: Intent?): DeepLink? {
        val uri = intent?.data ?: return null

        // Custom scheme: fbaudio://talk/01, fbaudio://series/X04, fbaudio://speaker/Name
        if (uri.scheme == "fbaudio") {
            val id = uri.pathSegments.firstOrNull() ?: return null
            return when (uri.host) {
                "talk" -> DeepLink.Talk(id)
                "series" -> DeepLink.Series(id)
                "speaker" -> DeepLink.Speaker(id)
                else -> null
            }
        }

        // HTTPS deep links: freebuddhistaudio.com/audio/details?num=01
        if (uri.host?.contains("freebuddhistaudio.com") != true) return null
        val path = uri.path ?: return null
        return when {
            path.startsWith("/audio/details") ->
                uri.getQueryParameter("num")?.let { DeepLink.Talk(it) }
            path.startsWith("/series/details") ->
                uri.getQueryParameter("num")?.let { DeepLink.Series(it) }
            path.startsWith("/browse") ->
                uri.getQueryParameter("s")?.let { DeepLink.Speaker(it) }
            else -> null
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001,
                )
            }
        }
    }
}
