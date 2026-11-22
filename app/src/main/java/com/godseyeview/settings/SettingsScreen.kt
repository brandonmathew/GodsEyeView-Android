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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.Kicker

/** Settings, organised the way upstream v0.3.0 organises its surfaces
 *  (Q23): Power Up (Provider Settings) first, then Map Source, Display &
 *  Quality, Tile Cache, Updates, Credits, About. Provider keys — the
 *  Cesium ion token included — are managed in the Power Up panel
 *  (issue #5), not here; this screen only reports how many are set.
 *  Replaces v1's flat settings scaffold. */
@Composable
fun SettingsScreen(
    basemapLabel: String,
    basemap: com.godseyeview.core.globe.Basemap =
        com.godseyeview.core.globe.Basemap.ESRI,
    onBasemapSelected: (com.godseyeview.core.globe.Basemap) -> Unit = {},
    qualityMode: QualityMode = QualityMode.AUTO,
    onQualitySelected: (QualityMode) -> Unit = {},
    effectiveQualityLabel: String = "",
    cacheSummary: String = "",
    cacheLimitMb: Int = 250,
    onCacheLimitSelected: (Int) -> Unit = {},
    onClearCache: () -> Unit = {},
    hasToken: Boolean = false,
    powerUpSetCount: Int = 0,
    onOpenPowerUp: () -> Unit = {},
    hasGoogleKey: Boolean = false,
    updateChannel: com.godseyeview.update.UpdateChannel =
        com.godseyeview.update.UpdateChannel.BETA,
    onUpdateChannelSelected: (com.godseyeview.update.UpdateChannel) -> Unit = {},
    updateStatus: String = "",
    onCheckUpdates: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
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
                Column(modifier = Modifier.weight(1f)) {
                    Kicker(text = stringResource(R.string.settings_kicker))
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
            }
            Spacer(Modifier.height(12.dp))

            SettingsSection(title = stringResource(R.string.settings_powerup_title)) {
                Text(
                    text = stringResource(
                        R.string.settings_powerup_summary, powerUpSetCount, POWER_UP_TOTAL,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Button(onClick = onOpenPowerUp) {
                    Text(text = stringResource(R.string.settings_powerup_open))
                }
            }

            SettingsSection(title = stringResource(R.string.settings_basemap_title)) {
                RadioOption(
                    label = stringResource(R.string.basemap_esri),
                    selected = basemap == com.godseyeview.core.globe.Basemap.ESRI,
                    onSelect = { onBasemapSelected(com.godseyeview.core.globe.Basemap.ESRI) },
                )
                RadioOption(
                    label = stringResource(R.string.basemap_osm),
                    selected = basemap == com.godseyeview.core.globe.Basemap.OSM,
                    onSelect = { onBasemapSelected(com.godseyeview.core.globe.Basemap.OSM) },
                )
                if (hasToken) {
                    RadioOption(
                        label = stringResource(R.string.basemap_photorealistic),
                        selected = basemap ==
                            com.godseyeview.core.globe.Basemap.PHOTOREALISTIC,
                        onSelect = {
                            onBasemapSelected(
                                com.godseyeview.core.globe.Basemap.PHOTOREALISTIC,
                            )
                        },
                    )
                }
                if (hasGoogleKey) {
                    // Q9 tier 3: direct Google 3D, selectable once a
                    // Google key exists in the KeyVault (Power Up, #5).
                    RadioOption(
                        label = stringResource(R.string.basemap_google_direct),
                        selected = basemap ==
                            com.godseyeview.core.globe.Basemap.GOOGLE_DIRECT,
                        onSelect = {
                            onBasemapSelected(
                                com.godseyeview.core.globe.Basemap.GOOGLE_DIRECT,
                            )
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_basemap_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_terrain_terrarium),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_quality_title)) {
                RadioOption(
                    label = stringResource(R.string.settings_quality_auto),
                    selected = qualityMode == QualityMode.AUTO,
                    onSelect = { onQualitySelected(QualityMode.AUTO) },
                )
                RadioOption(
                    label = stringResource(R.string.settings_quality_high),
                    selected = qualityMode == QualityMode.HIGH,
                    onSelect = { onQualitySelected(QualityMode.HIGH) },
                )
                RadioOption(
                    label = stringResource(R.string.settings_quality_balanced),
                    selected = qualityMode == QualityMode.BALANCED,
                    onSelect = { onQualitySelected(QualityMode.BALANCED) },
                )
                RadioOption(
                    label = stringResource(R.string.settings_quality_battery),
                    selected = qualityMode == QualityMode.BATTERY_SAVER,
                    onSelect = { onQualitySelected(QualityMode.BATTERY_SAVER) },
                )
                if (effectiveQualityLabel.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.settings_quality_effective, effectiveQualityLabel,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_cache_title)) {
                if (cacheSummary.isNotEmpty()) {
                    Text(
                        text = cacheSummary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                listOf(100, 250, 500).forEach { limit ->
                    RadioOption(
                        label = stringResource(R.string.settings_cache_limit_option, limit),
                        selected = cacheLimitMb == limit,
                        onSelect = { onCacheLimitSelected(limit) },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_cache_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Button(onClick = onClearCache) {
                    Text(text = stringResource(R.string.settings_cache_clear))
                }
            }

            SettingsSection(title = stringResource(R.string.updates_title)) {
                Text(
                    text = stringResource(R.string.updates_channel_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                RadioOption(
                    label = stringResource(R.string.updates_channel_stable),
                    selected = updateChannel ==
                        com.godseyeview.update.UpdateChannel.STABLE,
                    onSelect = {
                        onUpdateChannelSelected(
                            com.godseyeview.update.UpdateChannel.STABLE,
                        )
                    },
                )
                RadioOption(
                    label = stringResource(R.string.updates_channel_beta),
                    selected = updateChannel ==
                        com.godseyeview.update.UpdateChannel.BETA,
                    onSelect = {
                        onUpdateChannelSelected(
                            com.godseyeview.update.UpdateChannel.BETA,
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Button(onClick = onCheckUpdates) {
                    Text(text = stringResource(R.string.updates_check))
                }
                if (updateStatus.isNotEmpty()) {
                    Text(
                        text = updateStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_credits_title)) {
                Text(
                    text = stringResource(R.string.settings_credits_summary),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Button(onClick = onOpenCredits) {
                    Text(text = stringResource(R.string.settings_credits_view))
                }
            }

            SettingsSection(title = stringResource(R.string.settings_about_title)) {
                Text(
                    text = stringResource(R.string.settings_about_version, versionName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = basemapLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = GevColors.Accent,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}
