package com.focs.app.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focs.app.data.Quotes
import com.focs.app.data.UsageTracker
import com.focs.app.ui.FocsTheme

/** The wall you hit when an app is out of time. Its only way out is the home screen. */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_LIMIT
        val appName = UsageTracker.appLabel(this, pkg)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            FocsTheme {
                val (line, isGoal) = remember { Quotes.nudge(this) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (reason == REASON_FOCUS) "You're in focus mode" else "Time's up for today",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (reason == REASON_FOCUS) "$appName is blocked until your focus session ends."
                            else "You've used all your time on $appName. It opens again tomorrow.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(40.dp))
                        if (isGoal) {
                            Text("Your goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            "“$line”",
                            style = MaterialTheme.typography.titleLarge,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = ::goHome, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to what matters")
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_REASON = "reason"
        const val REASON_LIMIT = "limit"
        const val REASON_FOCUS = "focus"
    }
}
