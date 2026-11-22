package com.godseyeview.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R

/** Search (Q24 mobile design, issue #19): a full-screen mobile search
 *  flow — top app bar, one field for places and coordinates, results as
 *  full-width rows with 56dp+ touch targets. The search logic (bundled
 *  gazetteer, Photon, Nominatim, coordinate parsing) is unchanged
 *  plumbing from beta.1. Layer/flight search and typed chat join this
 *  field in later stages. */
@Composable
fun SearchScreen(
    onResultSelected: (PlaceResult) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    val searchingText = stringResource(R.string.search_searching)
    val noneText = stringResource(R.string.search_none)

    Surface(
        color = com.godseyeview.core.ui.GevColors.BgDark,
        contentColor = com.godseyeview.core.ui.GevColors.TextPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            com.godseyeview.core.ui.GevTopBar(
                title = stringResource(R.string.search_title),
                subtitle = stringResource(R.string.search_kicker),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (query.isBlank()) return@Button
                    status = searchingText
                    results = emptyList()
                    PlaceSearch.search(query) { found ->
                        results = found
                        status = if (found.isEmpty()) noneText else ""
                    }
                },
                enabled = query.isNotBlank(),
            ) { Text(stringResource(R.string.search_go)) }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(text = status, style = MaterialTheme.typography.bodyMedium)
            }
            results.forEach { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onResultSelected(result) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = com.godseyeview.core.ui.GevColors.TextPrimary,
                    )
                    if (result.detail.isNotEmpty()) {
                        Text(
                            text = result.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.godseyeview.core.ui.GevColors.TextSecondary,
                        )
                    }
                }
            }
            }
        }
    }
}
