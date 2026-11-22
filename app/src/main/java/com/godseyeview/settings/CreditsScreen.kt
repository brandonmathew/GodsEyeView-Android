package com.godseyeview.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.GevTopBar

/** Credits (plan Q19/Q32): every provider, dataset and library the core
 *  uses, with its terms — including the two non-commercial datasets that
 *  ship like the original (TeleGeography cables CC BY-NC-SA, Bhote Koshi
 *  imagery CC BY-NC). Presentation follows the Q23 design language; the
 *  content is the beta.1 content, completed with the dataset entries. */
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = GevColors.BgDark,
        contentColor = GevColors.TextPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GevTopBar(
                title = stringResource(R.string.credits_title),
                subtitle = stringResource(R.string.credits_kicker),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
            CreditEntry(R.string.credit_cesiumjs_name, R.string.credit_cesiumjs_terms)
            CreditEntry(R.string.credit_ion_name, R.string.credit_ion_terms)
            CreditEntry(R.string.credit_world_terrain_name, R.string.credit_world_terrain_terms)
            CreditEntry(R.string.credit_photorealistic_name, R.string.credit_photorealistic_terms)
            CreditEntry(R.string.credit_esri_name, R.string.credit_esri_terms)
            CreditEntry(R.string.credit_osm_name, R.string.credit_osm_terms)
            CreditEntry(R.string.credit_terrarium_name, R.string.credit_terrarium_terms)
            CreditEntry(R.string.credit_geocoding_name, R.string.credit_geocoding_terms)
            CreditEntry(R.string.credit_cables_name, R.string.credit_cables_terms)
            CreditEntry(R.string.credit_bhote_name, R.string.credit_bhote_terms)
            CreditEntry(R.string.credit_datacenters_name, R.string.credit_datacenters_terms)
            CreditEntry(R.string.credit_app_name, R.string.credit_app_terms)
            }
        }
    }
}

@Composable
private fun CreditEntry(nameRes: Int, termsRes: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(nameRes),
            style = MaterialTheme.typography.titleMedium,
            color = GevColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(termsRes),
            style = MaterialTheme.typography.bodyMedium,
            color = GevColors.TextSecondary,
        )
    }
}
