package com.defang.launcher.ui.settings.apptier

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.defang.launcher.R
import com.defang.launcher.domain.model.AppTier
import kotlinx.coroutines.delay

@Composable
fun AppTierScreen(
    /** Package to scroll to and briefly highlight (drawer long-press shortcut, #71). */
    focusPackage: String? = null,
    viewModel: AppTierViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsState()
    val query by viewModel.query.collectAsState()
    val listState = rememberLazyListState()

    // Scroll once, after the list has loaded; saveable so rotation doesn't yank it back.
    var focusDone by rememberSaveable { mutableStateOf(focusPackage == null) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(apps) {
        if (focusDone || apps.isEmpty()) return@LaunchedEffect
        focusDone = true
        val index = apps.indexOfFirst { it.packageName == focusPackage }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        highlighted = focusPackage
        delay(1_500)
        highlighted = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.tier_config_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Text(
            text = stringResource(R.string.tier_hidden_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.tier_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.tier_search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { item ->
                AppTierRow(
                    item = item,
                    highlighted = item.packageName == highlighted,
                    onTierChange = { tier -> viewModel.setTier(item.packageName, tier) },
                    onHiddenChange = { hidden -> viewModel.setHidden(item.packageName, hidden) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
@Composable
private fun AppTierRow(
    item: AppTierItem,
    highlighted: Boolean,
    onTierChange: (AppTier) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(durationMillis = 600),
        label = "rowHighlight",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = item.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Utility radio
            RadioButton(
                selected = item.tier == AppTier.UTILITY,
                onClick = { onTierChange(AppTier.UTILITY) },
            )
            Text(
                text = stringResource(R.string.tier_utility),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Watched radio
            RadioButton(
                selected = item.tier == AppTier.WATCHED,
                onClick = { onTierChange(AppTier.WATCHED) },
            )
            Text(
                text = stringResource(R.string.tier_watched),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Hidden from the drawer — still reachable via drawer search
            Checkbox(
                checked = item.hidden,
                onCheckedChange = onHiddenChange,
            )
            Text(
                text = stringResource(R.string.tier_hidden),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
