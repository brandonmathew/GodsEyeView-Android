package com.godseyeview.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R

/** Plan Q32: every provider, dataset and library the core uses, with its
 *  terms. (The small on-globe credit line the providers require is added
 *  by the globe page itself.) Surface wrap for the content color - #27. */
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.credits_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
            }
            Spacer(Modifier.height(12.dp))
            CreditEntry(R.string.credit_cesiumjs_name, R.string.credit_cesiumjs_terms)
            CreditEntry(R.string.credit_ion_name, R.string.credit_ion_terms)
            CreditEntry(R.string.credit_world_terrain_name, R.string.credit_world_terrain_terms)
            CreditEntry(R.string.credit_photorealistic_name, R.string.credit_photorealistic_terms)
            CreditEntry(R.string.credit_esri_name, R.string.credit_esri_terms)
            CreditEntry(R.string.credit_osm_name, R.string.credit_osm_terms)
            CreditEntry(R.string.credit_terrarium_name, R.string.credit_terrarium_terms)
            CreditEntry(R.string.credit_geocoding_name, R.string.credit_geocoding_terms)
            CreditEntry(R.string.credit_app_name, R.string.credit_app_terms)
        }
    }
}

@Composable
private fun CreditEntry(nameRes: Int, termsRes: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(nameRes),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(termsRes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
