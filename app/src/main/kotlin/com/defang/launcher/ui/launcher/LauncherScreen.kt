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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defang.launcher.R
import kotlinx.coroutines.launch

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
    // Gestures starting in this strip on the right edge belong to the letter
    // rail, not to the swipe-down-to-close handler.
    val railGuardPx = with(LocalDensity.current) { 40.dp.toPx() }

    // First list index for each initial letter, in list order ('#' for digits etc.)
    val letterIndex = remember(apps) {
        val map = LinkedHashMap<Char, Int>()
        apps.forEachIndexed { i, app ->
            val first = app.label.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (first.isLetter()) first else '#'
            if (key !in map) map[key] = i
        }
        map
    }

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
                        // Only a drag that starts with the list at the top can close,
                        // and never one that starts on the letter rail
                        val startedAtTop = !listState.canScrollBackward &&
                            down.position.x < size.width - railGuardPx
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

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(apps) { app ->
                            AppRow(app = app, onTap = { onAppTap(app.packageName) })
                        }
                    }
                    LetterRail(
                        letterIndex = letterIndex,
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/**
 * Thumb-friendly alphabet index on the right edge of the drawer. Tapping or
 * dragging jumps the list to the first app starting with that letter.
 */
@Composable
private fun LetterRail(
    letterIndex: Map<Char, Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (letterIndex.isEmpty()) return
    val letters = remember(letterIndex) { letterIndex.keys.toList() }
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    Column(
        modifier = modifier
            .padding(end = 4.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()

                    fun jumpTo(y: Float) {
                        val slot = (y / size.height * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val letter = letters[slot]
                        if (letter != activeLetter) {
                            activeLetter = letter
                            scope.launch {
                                listState.scrollToItem(letterIndex.getValue(letter))
                            }
                        }
                    }

                    jumpTo(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        jumpTo(change.position.y)
                    }
                    activeLetter = null
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                color = if (letter == activeLetter)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
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
