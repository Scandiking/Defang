package com.defang.launcher.ui.launcher

import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.defang.launcher.data.local.db.entity.AppFolderEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    apps: List<AppInfo>,
    query: String,
    ownPackageName: String,
    onQueryChange: (String) -> Unit,
    onAppTap: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onRename: (String, String) -> Unit,
    getInstallSource: suspend (String) -> String,
    onClose: () -> Unit,
    searchOnOpen: Boolean = false,
    letterRailScale: Float = 1f,
    letterRailXOffsetDp: Int = 4,
    foldersEnabled: Boolean = false,
    folders: List<AppFolderEntity> = emptyList(),
    onMoveToFolder: (String, Long?) -> Unit = { _, _ -> },
    onMoveToNewFolder: (String, String) -> Unit = { _, _ -> },
    onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    onSetFolderIndicator: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteFolder: (Long) -> Unit = {},
) {
    var searchActive by remember { mutableStateOf(searchOnOpen) }

    // In search-only mode back always closes the drawer; otherwise collapse search first.
    if (searchOnOpen) {
        BackHandler { onClose() }
    } else {
        BackHandler(enabled = searchActive) { searchActive = false }
        BackHandler(enabled = !searchActive) { onClose() }
    }

    // Swipe down closes the drawer when the app list is at its top. We observe
    // pointer events on the Initial pass, so we see the drag even though the
    // list consumes it for scrolling — no consumption conflict.
    val listState = rememberLazyListState()
    val closeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    // Gestures starting in this strip on the right edge belong to the letter
    // rail, not to the swipe-down-to-close handler. Widened by the rail's own
    // x-offset and scale so an enlarged or shifted-in rail stays fully covered.
    val railGuardPx = with(LocalDensity.current) {
        (40.dp * letterRailScale + letterRailXOffsetDp.dp).toPx()
    }

    // Personal/Work split — a tab per profile instead of a "(Work)" label on
    // every row. The Work tab only exists once there's something to put in it
    // (the setting is off by default, so most users never see it at all).
    val personalApps = remember(apps) { apps.filter { it.userHandle == Process.myUserHandle() } }
    val workApps = remember(apps) { apps.filter { it.userHandle != Process.myUserHandle() } }
    var workTabSelected by remember { mutableStateOf(false) }
    val showingWorkTab = workTabSelected && workApps.isNotEmpty()

    // In search-only mode: one merged list, work entries labelled with "(work)"
    // so they're distinguishable from same-named personal apps without tabs.
    val mergedApps = remember(personalApps, workApps) {
        (personalApps + workApps.map { it.copy(label = "${it.label} (work)") })
            .sortedBy { it.label.lowercase() }
    }
    val tabApps = when {
        searchOnOpen -> mergedApps
        showingWorkTab -> workApps
        else -> personalApps
    }

    // In search-only mode: show nothing until the user has typed at least one character.
    // Auto-open when the query narrows the list to exactly one app.
    val displayedApps = if (searchOnOpen && query.isEmpty()) emptyList() else tabApps

    // Folders (issue #41, opt-in) only shape the browsed personal list — a
    // search always spans every app, and search-only mode has no list to shape.
    val useFolders = foldersEnabled && !searchOnOpen && !showingWorkTab && query.isBlank()
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val openFolder = if (useFolders) folders.firstOrNull { it.id == openFolderId } else null
    BackHandler(enabled = openFolder != null && !searchActive) { openFolderId = null }
    LaunchedEffect(openFolder?.id) { listState.scrollToItem(0) }

    val entries: List<DrawerEntry> = remember(tabApps, folders, useFolders, openFolder) {
        when {
            !useFolders -> tabApps.map { DrawerEntry.App(it) }
            openFolder != null -> tabApps
                .filter { it.folderId == openFolder.id }
                .map { DrawerEntry.App(it) }
            else -> {
                // An app pointing at a folder that no longer exists counts as top level
                val folderIds = folders.map { it.id }.toSet()
                (tabApps.filter { it.folderId !in folderIds }.map { DrawerEntry.App(it) } +
                    folders.map { DrawerEntry.Folder(it) })
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    // Personal apps only — work-profile apps share their config row with a
    // same-package personal twin, so they can't carry their own folder.
    var folderPickerFor by remember { mutableStateOf<AppInfo?>(null) }
    val moveToFolderFor: (AppInfo) -> (() -> Unit)? = { app ->
        if (foldersEnabled && app.userHandle == Process.myUserHandle()) {
            { folderPickerFor = app }
        } else {
            null
        }
    }
    LaunchedEffect(displayedApps) {
        if (searchOnOpen && query.isNotEmpty() && displayedApps.size == 1) {
            onAppTap(displayedApps[0])
        }
    }

    // First list index for each initial letter, in list order ('#' for digits etc.)
    val letterIndex = remember(entries) {
        val map = LinkedHashMap<Char, Int>()
        entries.forEachIndexed { i, entry ->
            val first = entry.label.firstOrNull()?.uppercaseChar() ?: '#'
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
                    // Scaffold padding already clears the status bar; the default
                    // SearchBar insets would add it a second time.
                    windowInsets = WindowInsets(0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    LazyColumn {
                        items(displayedApps) { app ->
                            AppRow(
                                app = app,
                                canUninstall = app.packageName != ownPackageName,
                                onTap = { onAppTap(app) },
                                onAppInfo = onAppInfo,
                                onUninstall = onUninstall,
                                onRename = onRename,
                                getInstallSource = getInstallSource,
                                onMoveToFolder = moveToFolderFor(app),
                            )
                        }
                    }
                }

                if (!searchOnOpen && workApps.isNotEmpty()) {
                    TabRow(selectedTabIndex = if (showingWorkTab) 1 else 0) {
                        Tab(
                            selected = !showingWorkTab,
                            onClick = { workTabSelected = false },
                            text = { Text(stringResource(R.string.launcher_tab_personal)) },
                        )
                        Tab(
                            selected = showingWorkTab,
                            onClick = { workTabSelected = true },
                            text = { Text(stringResource(R.string.launcher_tab_work)) },
                        )
                    }
                }

                if (openFolder != null) {
                    // Plain title, no chevron — tapping it (or back) returns to the list.
                    // Smaller and dimmed so it reads as a label, not one more app.
                    Text(
                        text = openFolder.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openFolderId = null }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            when (entry) {
                                is DrawerEntry.App -> AppRow(
                                    app = entry.app,
                                    canUninstall = entry.app.packageName != ownPackageName,
                                    onTap = { onAppTap(entry.app) },
                                    onAppInfo = onAppInfo,
                                    onUninstall = onUninstall,
                                    onRename = onRename,
                                    getInstallSource = getInstallSource,
                                    onMoveToFolder = moveToFolderFor(entry.app),
                                )
                                is DrawerEntry.Folder -> FolderRow(
                                    folder = entry.folder,
                                    onOpen = { openFolderId = entry.folder.id },
                                    onRename = { onRenameFolder(entry.folder.id, it) },
                                    onSetIndicator = { onSetFolderIndicator(entry.folder.id, it) },
                                    onDelete = { onDeleteFolder(entry.folder.id) },
                                )
                            }
                        }
                    }
                    LetterRail(
                        letterIndex = letterIndex,
                        listState = listState,
                        scale = letterRailScale,
                        xOffsetDp = letterRailXOffsetDp,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }

    folderPickerFor?.let { app ->
        FolderPickerDialog(
            folders = folders,
            currentFolderId = app.folderId,
            onPick = { folderId ->
                onMoveToFolder(app.packageName, folderId)
                folderPickerFor = null
            },
            onCreate = { name ->
                onMoveToNewFolder(app.packageName, name)
                folderPickerFor = null
            },
            onDismiss = { folderPickerFor = null },
        )
    }
}

/** One row of the browsed drawer list: an app, or (folders on) a folder. */
private sealed interface DrawerEntry {
    val label: String

    data class App(val app: AppInfo) : DrawerEntry {
        override val label: String get() = app.label
    }

    data class Folder(val folder: AppFolderEntity) : DrawerEntry {
        override val label: String get() = folder.name
    }
}

/**
 * A folder row. Looks like an app row by default — the trailing arrow is a
 * per-folder opt-in from the long-press menu, so a folder of feeds can stay
 * as unremarkable as everything else while a folder of tools can be marked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: AppFolderEntity,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onSetIndicator: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (folder.showIndicator) {
                Text(
                    text = stringResource(R.string.folder_indicator),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_menu_rename)) },
                onClick = {
                    menuOpen = false
                    renameDialogOpen = true
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (folder.showIndicator) R.string.folder_menu_hide_arrow
                            else R.string.folder_menu_show_arrow
                        )
                    )
                },
                onClick = {
                    menuOpen = false
                    onSetIndicator(!folder.showIndicator)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_menu_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
    if (renameDialogOpen) {
        var text by remember { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text(stringResource(R.string.folder_rename_title)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(text)
                        renameDialogOpen = false
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text(stringResource(R.string.rename_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }) {
                    Text(stringResource(R.string.rename_dialog_cancel))
                }
            },
        )
    }
}

/** "Move to folder" from an app's long-press menu: pick one, go top level, or start a new one. */
@Composable
private fun FolderPickerDialog(
    folders: List<AppFolderEntity>,
    currentFolderId: Long?,
    onPick: (Long?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val choices = listOf<Pair<Long?, String>>(
                    null to stringResource(R.string.folder_picker_none)
                ) + folders.map { it.id to it.name }
                choices.forEach { (id, name) ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (id == currentFolderId) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(stringResource(R.string.folder_picker_new_hint)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.folder_picker_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rename_dialog_cancel))
            }
        },
    )
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
    scale: Float = 1f,
    xOffsetDp: Int = 4,
) {
    if (letterIndex.isEmpty()) return
    val letters = remember(letterIndex) { letterIndex.keys.toList() }
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    Column(
        modifier = modifier
            .padding(end = xOffsetDp.dp)
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
                fontSize = (11 * scale).sp,
                lineHeight = (13 * scale).sp,
                fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                color = if (letter == activeLetter)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(
                    horizontal = (8 * scale).dp,
                    vertical = (1 * scale).dp,
                ),
            )
        }
    }
}

/**
 * A drawer row. Tap launches; long-press opens a Pixel/One UI-style popup with
 * App info, Rename, and Uninstall. `canUninstall` is false for Defang's own entry.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    canUninstall: Boolean,
    onTap: () -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onRename: (String, String) -> Unit,
    getInstallSource: suspend (String) -> String,
    /** Non-null only while folders are on and the app can carry one. */
    onMoveToFolder: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    Box {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_menu_info)) },
                onClick = {
                    menuOpen = false
                    onAppInfo(app)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_menu_rename)) },
                onClick = {
                    menuOpen = false
                    renameDialogOpen = true
                },
            )
            if (onMoveToFolder != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_menu_move_to_folder)) },
                    onClick = {
                        menuOpen = false
                        onMoveToFolder()
                    },
                )
            }
            if (canUninstall) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_menu_uninstall)) },
                    onClick = {
                        menuOpen = false
                        onUninstall(app)
                    },
                )
            }
        }
    }
    if (renameDialogOpen) {
        RenameDialog(
            currentValue = app.customLabel ?: "",
            placeholder = app.rawLabel,
            packageName = app.packageName,
            getInstallSource = getInstallSource,
            onConfirm = { newLabel ->
                onRename(app.packageName, newLabel)
                renameDialogOpen = false
            },
            onDismiss = { renameDialogOpen = false },
        )
    }
}

/**
 * Shared by the long-press "Rename" action and the duplicate-name nudge.
 * Cosmetic only — a blank save clears back to [placeholder], the system label.
 * Shows where the app was installed from (Play Store / F-Droid / preinstalled)
 * plus its package name — the closest thing Android exposes to a "developer
 * name", and often the only way to tell apart two same-named, icon-less apps
 * (a stock "Galleri" vs. a third-party one, which "Meldinger" is Samsung's
 * vs. Google's). Only surfaced here, in a moment the user already asked for
 * it — not as permanent text on every drawer row.
 */
@Composable
fun RenameDialog(
    currentValue: String,
    placeholder: String,
    packageName: String,
    getInstallSource: suspend (String) -> String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentValue) }
    val installSource by produceState(initialValue = "", packageName) {
        value = getInstallSource(packageName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_dialog_title)) },
        text = {
            Column {
                Text(
                    text = if (installSource.isEmpty()) {
                        packageName
                    } else {
                        stringResource(R.string.rename_dialog_source, installSource, packageName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.rename_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rename_dialog_cancel))
            }
        },
    )
}
