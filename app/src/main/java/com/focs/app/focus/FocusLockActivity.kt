package com.focs.app.focus

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focs.app.data.Quotes
import com.focs.app.data.Store
import com.focs.app.ui.FancyTimer
import com.focs.app.ui.FocusBackdrop
import kotlinx.coroutines.delay

/**
 * The focus timer shown on top of the lock screen. Focs opens it whenever the screen
 * turns off during a session, so waking the phone shows your countdown first.
 */
class FocusLockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        val start = Store.focusStart(this)
        val end = Store.focusEnd(this)
        if (end <= System.currentTimeMillis()) {
            finish()
            return
        }

        setContent {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            val line = remember { Quotes.nudge(this).first }
            LaunchedEffect(Unit) {
                while (now < end) {
                    delay(1000)
                    now = System.currentTimeMillis()
                }
                finish()
            }
            FocusBackdrop {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FancyTimer(end - now, end - start)
                    Spacer(Modifier.height(40.dp))
                    Text(
                        "“$line”",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(48.dp))
                    OutlinedButton(
                        onClick = ::unlock,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) { Text("Unlock phone") }
                }
            }
        }
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= 26) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            })
        } else {
            finish()
        }
    }
}
