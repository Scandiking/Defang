package com.defang.launcher.ui.settings.awareness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defang.launcher.R

// One library entry: the tidbit and, for the awareness tracks, its citation.
private data class LibraryEntry(val text: String, val source: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwarenessLibraryScreen(onBack: () -> Unit) {
    val resources = LocalContext.current.resources
    val sections = remember {
        fun track(tidbitsRes: Int, sourcesRes: Int): List<LibraryEntry> {
            val tidbits = resources.getStringArray(tidbitsRes)
            val sources = resources.getStringArray(sourcesRes)
            return tidbits.mapIndexed { i, text ->
                LibraryEntry(text, sources.getOrNull(i))
            }
        }
        listOf(
            R.string.library_track_general to
                track(R.array.tidbits_general, R.array.tidbits_general_sources),
            R.string.library_track_social to
                track(R.array.tidbits_social, R.array.tidbits_social_sources),
            R.string.library_track_adult to
                track(R.array.tidbits_adult, R.array.tidbits_adult_sources),
            R.string.library_tasks_header to arrayOf(
                R.array.offline_prompts_movement,
                R.array.offline_prompts_space,
                R.array.offline_prompts_sensory,
                R.array.offline_prompts_social,
                R.array.offline_prompts_creative,
            ).flatMap { arrayRes ->
                resources.getStringArray(arrayRes).map { LibraryEntry(it, source = null) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_awareness_library)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            sections.forEach { (headerRes, entries) ->
                item {
                    Text(
                        text = stringResource(headerRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = 16.dp, end = 16.dp, top = 16.dp
                        ),
                    )
                }
                items(entries) { entry ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            entry.source?.let { source ->
                                Text(
                                    text = source,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
