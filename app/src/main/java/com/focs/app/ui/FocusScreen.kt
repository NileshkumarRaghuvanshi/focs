package com.focs.app.ui

import android.content.Intent
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focs.app.data.Quotes
import com.focs.app.data.Store
import com.focs.app.focus.FocusManager
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(refresh: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionVersion by remember { mutableIntStateOf(0) }
    val end = remember(refresh, sessionVersion) { Store.focusEnd(ctx) }
    val start = remember(refresh, sessionVersion) { Store.focusStart(ctx) }
    val active = end > now

    LaunchedEffect(active) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    if (active) {
        ActiveFocus(start, end, now, modifier) {
            FocusManager.stop(ctx)
            sessionVersion++
        }
    } else {
        StartFocus(refresh, modifier) { minutes ->
            FocusManager.start(ctx, minutes)
            now = System.currentTimeMillis()
            sessionVersion++
        }
    }
}

@Composable
private fun StartFocus(refresh: Int, modifier: Modifier, onStart: (Int) -> Unit) {
    val ctx = LocalContext.current
    val hasDnd = remember(refresh) { FocusManager.hasDndAccess(ctx) }
    var minutes by remember { mutableFloatStateOf(45f) }
    val m = minutes.toInt()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Focus mode",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
        )
        Text(
            "Silences every notification and blocks all your limited apps until the timer ends. " +
                "Calls from starred contacts, and anyone who calls twice within 15 minutes, still ring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        if (!hasDnd) {
            PermissionCard(
                "Allow Do Not Disturb access",
                "Find Focs in the list and allow it, so focus mode can silence notifications.",
                "Allow",
            ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }

        Column(Modifier.padding(16.dp)) {
            Text("$m minutes", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 45, 60, 90).forEach { preset ->
                    FilterChip(
                        selected = m == preset,
                        onClick = { minutes = preset.toFloat() },
                        label = { Text("$preset") },
                    )
                }
            }
            Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..240f)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onStart(m) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Start focusing", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text("Who counts as important?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Anyone you star in your Contacts app. Star the people who should always get through.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            }) { Text("Open Contacts") }
        }
    }
}

@Composable
private fun ActiveFocus(start: Long, end: Long, now: Long, modifier: Modifier, onQuit: () -> Unit) {
    val ctx = LocalContext.current
    val remaining = (end - now).coerceAtLeast(0)
    val total = (end - start).coerceAtLeast(1)
    var nudge by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var askingToQuit by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { remaining / total.toFloat() },
                modifier = Modifier.size(260.dp),
                strokeWidth = 10.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatClock(remaining), fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text("left", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(32.dp))

        val shown = nudge
        if (shown == null) {
            Button(onClick = { nudge = Quotes.nudge(ctx) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("I'm feeling lazy", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (shown.second) {
                        Text("Your goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "“${shown.first}”",
                        style = MaterialTheme.typography.titleLarge,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { nudge = Quotes.nudge(ctx) }) { Text("Another one") }
                    TextButton(onClick = { nudge = null }) { Text("OK, back to work") }
                }
            }
        }
        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = { askingToQuit = true }) { Text("End early") }
    }

    if (askingToQuit) QuitDialog(onStay = { askingToQuit = false }, onQuit = { askingToQuit = false; onQuit() })
}

/** Ending early is allowed, but only after 10 seconds of looking at your own goal. */
@Composable
private fun QuitDialog(onStay: () -> Unit, onQuit: () -> Unit) {
    val ctx = LocalContext.current
    val goal = remember { Store.goals(ctx).randomOrNull() ?: Quotes.randomLine() }
    var wait by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) {
        while (wait > 0) {
            delay(1000)
            wait--
        }
    }
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("Quit this session?") },
        text = {
            Column {
                Text("Remember why you started:")
                Spacer(Modifier.height(8.dp))
                Text("“$goal”", style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
            }
        },
        confirmButton = { Button(onClick = onStay) { Text("Keep going") } },
        dismissButton = {
            TextButton(onClick = onQuit, enabled = wait == 0) {
                Text(if (wait > 0) "Quit ($wait)" else "Quit anyway")
            }
        },
    )
}

private fun formatClock(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
