package com.defang.launcher.ui.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defang.launcher.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAppTap: (String) -> Unit,
    onSettingsTap: () -> Unit,
) {
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = { Text(stringResource(R.string.launcher_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Search results inside the expanded bar
                LazyColumn {
                    items(apps) { app ->
                        AppRow(app = app, onTap = { onAppTap(app.packageName) })
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(apps) { app ->
                    AppRow(app = app, onTap = { onAppTap(app.packageName) })
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppInfo, onTap: () -> Unit) {
    Text(
        text = app.label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
