package com.focs.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.focs.app.block.BlockerService
import com.focs.app.data.AppInfo
import com.focs.app.data.Store
import com.focs.app.data.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val app: AppInfo, val usedMs: Long, val limitMin: Int?, val pendingMin: Int?)

@Composable
fun LimitsScreen(refresh: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    var query by remember { mutableStateOf("") }

    val hasUsage = remember(refresh) { UsageTracker.hasAccess(ctx) }
    val hasBlocker = remember(refresh) { BlockerService.isEnabled(ctx) }

    val rows by produceState<List<AppEntry>?>(null, refresh, version) {
        value = withContext(Dispatchers.IO) { loadRows(ctx) }
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                "App limits",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
            )
            Text(
                "When an app's time runs out, Focs closes it and won't let it open again until tomorrow. " +
                    "Making a limit stricter works now; loosening it only starts tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (!hasUsage) item {
            PermissionCard(
                "Allow usage access",
                "Needed to see how long you've spent in each app today.",
                "Allow",
            ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        if (!hasBlocker) item {
            PermissionCard(
                "Turn on the Focs app blocker",
                "In Accessibility, open Focs app blocker and switch it on. This is what closes apps when time's up.",
                "Open",
            ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search apps") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        val list = rows
        if (list == null) {
            item {
                Row(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator() }
            }
        } else {
            val filtered = list.filter { query.isBlank() || it.app.label.contains(query, ignoreCase = true) }
            items(filtered, key = { it.app.packageName }) { row -> AppRow(row) { editing = row } }
        }
    }

    editing?.let { row ->
        LimitDialog(row, onDismiss = { editing = null }) { minutes ->
            Store.setLimit(ctx, row.app.packageName, minutes)
            editing = null
            version++
        }
    }
}

private fun loadRows(ctx: Context): List<AppEntry> {
    val usage = UsageTracker.usageTodayMs(ctx)
    val limits = Store.limits(ctx)
    return UsageTracker.launchableApps(ctx)
        .map { AppEntry(it, usage[it.packageName] ?: 0L, limits[it.packageName], Store.pendingLimit(ctx, it.packageName)) }
        // Limited apps first, then the apps you use most.
        .sortedWith(compareBy<AppEntry> { it.limitMin == null }.thenByDescending { it.usedMs })
}

@Composable
private fun AppRow(row: AppEntry, onClick: () -> Unit) {
    val icon = remember(row.app.packageName) { row.app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, null, Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(row.app.label, style = MaterialTheme.typography.bodyLarge)
            val status = buildString {
                append("${formatMinutes(row.usedMs)} today")
                row.limitMin?.let { append(" · limit ${it}m") }
                row.pendingMin?.let { append(if (it == 0) " · removed from tomorrow" else " · ${it}m from tomorrow") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.limitMin?.let { limit ->
                Spacer(Modifier.height(6.dp))
                val fraction = (row.usedMs / (limit * 60_000f)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (fraction >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LimitDialog(row: AppEntry, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var minutes by remember { mutableFloatStateOf((row.limitMin ?: 30).toFloat()) }
    val m = (minutes / 5).toInt() * 5
    val loosening = row.limitMin != null && (m == 0 || m > row.limitMin)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.app.label) },
        text = {
            Column {
                Text(if (m == 0) "No limit" else "$m minutes a day", style = MaterialTheme.typography.titleLarge)
                Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 0f..180f)
                Text(
                    "Used ${formatMinutes(row.usedMs)} today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loosening) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This loosens your limit, so it starts tomorrow. Today's ${row.limitMin} minutes still apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(m) }) { Text(if (loosening) "Apply tomorrow" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
