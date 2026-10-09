package com.machine.newsapp

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.edit
import com.machine.newsapp.data.FeedKind
import com.machine.newsapp.notifications.Notifications
import com.machine.newsapp.ui.FeedViewModel
import com.machine.newsapp.ui.NewsRoot
import com.machine.newsapp.ui.NewsTheme

class MainActivity : ComponentActivity() {
    private var targetTab by mutableStateOf<String?>(null)
    private var notificationsEnabled by mutableStateOf(false)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = Notifications.allowed(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        val container = (application as NewsApplication).container
        setContent {
            NewsTheme {
                val vm: FeedViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(container.repository, container.tokenStore) as T
                })
                NewsRoot(vm, targetTab, { targetTab = null }, notificationsEnabled, ::enableNotifications)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        notificationsEnabled = Notifications.allowed(this)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    private fun handleIntent(intent: Intent) {
        targetTab = intent.getStringExtra(Notifications.EXTRA_TAB)?.takeIf { route -> FeedKind.entries.any { it.route == route } }
        // Don't replay an already-consumed notification when the Activity is recreated.
        intent.removeExtra(Notifications.EXTRA_TAB)
    }
    private fun enableNotifications() {
        val prefs = getSharedPreferences("notifications", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && (!prefs.getBoolean("asked", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            prefs.edit { putBoolean("asked", true) }
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }
}
