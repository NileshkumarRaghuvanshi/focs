package com.focs.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.focs.app.data.Quotes
import com.focs.app.data.Store

@Composable
fun MotivationScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var goals by remember { mutableStateOf(Store.goals(ctx)) }
    var draft by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf(Quotes.randomLine()) }

    fun save(newGoals: List<String>) {
        goals = newGoals
        Store.setGoals(ctx, newGoals)
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                "Your goals",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
            )
            Text(
                "Write one-liners about what you're working towards. Focs shows them when you tap " +
                    "\"I'm feeling lazy\" in focus mode, when you try to quit early, and on the block screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("e.g. Ship my app by December") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            save(goals + draft.trim())
                            draft = ""
                        }
                    },
                ) { Icon(Icons.Filled.Add, contentDescription = "Add goal") }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (goals.isEmpty()) item {
            Text(
                "No goals yet. Until you add some, Focs uses its built-in lines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        itemsIndexed(goals) { index, goal ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(goal, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { save(goals.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete goal")
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                "Built-in lines",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("“$sample”", style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                    TextButton(onClick = { sample = Quotes.randomLine() }) { Text("Show another") }
                }
            }
            Text(
                "${Quotes.lines.size} lines are built in and work offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
