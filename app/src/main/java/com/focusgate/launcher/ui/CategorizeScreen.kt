package com.focusgate.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusgate.launcher.apps.InstalledApp
import com.focusgate.launcher.schedule.Category

/**
 * One-time app tagging. Each app arrives pre-selected with a best-guess category
 * (from [com.focusgate.launcher.schedule.AppCategorizer]); the user only adjusts.
 */
@Composable
fun CategorizeScreen(
    apps: List<Pair<InstalledApp, Category>>,
    onSave: (Map<String, Category>) -> Unit,
) {
    val selections = remember {
        mutableStateMapOf<String, Category>().apply {
            apps.forEach { (app, cat) -> put(app.packageName, cat) }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "Sort your apps",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
            Text(
                "Pick a category for each app. Your daily schedule allows or blocks apps by these categories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(apps, key = { it.first.packageName }) { (app, _) ->
                    AppCategoryRow(
                        label = app.label,
                        selected = selections[app.packageName] ?: Category.OTHER,
                        onSelect = { selections[app.packageName] = it },
                    )
                }
            }
            Button(
                onClick = { onSave(selections.toMap()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) { Text("Save categories") }
        }
    }
}

@Composable
private fun AppCategoryRow(label: String, selected: Category, onSelect: (Category) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Box {
            AssistChip(onClick = { open = true }, label = { Text(selected.label) })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Category.entries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c); open = false })
                }
            }
        }
    }
}
