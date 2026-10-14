package com.focs.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.focs.app.block.BlockerService
import com.focs.app.focus.FocusManager
import com.focs.app.ui.FocsTheme
import com.focs.app.ui.FocusScreen
import com.focs.app.ui.LimitsScreen
import com.focs.app.ui.MotivationScreen

class MainActivity : ComponentActivity() {

    private var tab by mutableIntStateOf(TAB_LIMITS)
    /** Bumped on every resume so screens re-read permissions and usage after visiting Settings. */
    private var refresh by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (FocusManager.isActive(this)) tab = TAB_FOCUS
        intent?.getIntExtra(EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let { tab = it }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            FocsTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == TAB_LIMITS, onClick = { tab = TAB_LIMITS },
                                icon = { Icon(Icons.Filled.Lock, null) }, label = { Text("Limits") },
                            )
                            NavigationBarItem(
                                selected = tab == TAB_FOCUS, onClick = { tab = TAB_FOCUS },
                                icon = { Icon(Icons.Filled.PlayArrow, null) }, label = { Text("Focus") },
                            )
                            NavigationBarItem(
                                selected = tab == TAB_MOTIVATION, onClick = { tab = TAB_MOTIVATION },
                                icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Goals") },
                            )
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (tab) {
                        TAB_LIMITS -> LimitsScreen(refresh, modifier)
                        TAB_FOCUS -> FocusScreen(refresh, modifier)
                        else -> MotivationScreen(modifier)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getIntExtra(EXTRA_TAB, -1).takeIf { it >= 0 }?.let { tab = it }
    }

    override fun onResume() {
        super.onResume()
        FocusManager.endIfExpired(this)
        BlockerService.startIfReady(this)
        refresh++
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_LIMITS = 0
        const val TAB_FOCUS = 1
        const val TAB_MOTIVATION = 2
    }
}
