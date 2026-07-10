package com.defang.launcher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    onClose: () -> Unit,
) {
    var searchActive by remember { mutableStateOf(false) }

    // Back press: close search first, then close drawer
    BackHandler(enabled = searchActive) { searchActive = false }
    BackHandler(enabled = !searchActive) { onClose() }

    // Swipe down closes the drawer when the app list is at its top. We observe
    // pointer events on the Initial pass, so we see the drag even though the
    // list consumes it for scrolling — no consumption conflict.
    val listState = rememberLazyListState()
    val closeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        // Only a drag that starts with the list at the top can close
                        val startedAtTop = !listState.canScrollBackward
                        var prevY = down.position.y
                        var pulledDown = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            pulledDown += change.position.y - prevY
                            prevY = change.position.y
                            if (pulledDown < 0f) pulledDown = 0f // moved up — start over
                            if (pulledDown > closeThresholdPx &&
                                startedAtTop && !searchActive
                            ) {
                                onClose()
                                break
                            }
                        }
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                    LazyColumn {
                        items(apps) { app ->
                            AppRow(app = app, onTap = { onAppTap(app.packageName) })
                        }
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(apps) { app ->
                        AppRow(app = app, onTap = { onAppTap(app.packageName) })
                    }
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
