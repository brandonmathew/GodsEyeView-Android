package com.godseyeview.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.globe.Basemap
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.GevTopBar
import com.godseyeview.update.UpdateChannel

/** Settings (Q24 mobile design, issue #19): a standard mobile settings
 *  hub — rows with title + summary + chevron — with the deep sections
 *  (Map Source, Quality, Tile Cache, Updates, About) as their own
 *  screens. Content and organisation are the original's (Power Up first,
 *  then Map Source, Display & Quality, Tile Cache, Updates, Credits,
 *  About); provider keys — the Cesium ion token included — are managed
 *  in the Power Up panel (issue #5), not here; the hub only reports how
 *  many are set. All callbacks/plumbing are unchanged from beta.2. */
private enum class SettingsPage { HUB, MAP, QUALITY, CACHE, UPDATES, ABOUT }

@Composable
fun SettingsScreen(
    basemapLabel: String,
    basemap: Basemap = Basemap.ESRI,
    onBasemapSelected: (Basemap) -> Unit = {},
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
    updateChannel: UpdateChannel = UpdateChannel.BETA,
    onUpdateChannelSelected: (UpdateChannel) -> Unit = {},
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
    var page by remember { mutableStateOf(SettingsPage.HUB) }

    Surface(
        color = GevColors.BgDark,
        contentColor = GevColors.TextPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (page) {
                SettingsPage.HUB -> {
                    GevTopBar(
                        title = stringResource(R.string.settings_title),
                        subtitle = stringResource(R.string.settings_kicker),
                        onBack = onBack,
                        backContentDescription = stringResource(R.string.settings_back),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SettingsRow(
                            title = stringResource(R.string.settings_powerup_title),
                            summary = stringResource(
                                R.string.settings_powerup_summary,
                                powerUpSetCount, POWER_UP_TOTAL,
                            ),
                            onClick = onOpenPowerUp,
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_basemap_title),
                            summary = basemapLabel,
                            onClick = { page = SettingsPage.MAP },
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_quality_title),
                            summary = stringResource(
                                when (qualityMode) {
                                    QualityMode.AUTO -> R.string.settings_quality_auto
                                    QualityMode.HIGH -> R.string.settings_quality_high
                                    QualityMode.BALANCED ->
                                        R.string.settings_quality_balanced
                                    QualityMode.BATTERY_SAVER ->
                                        R.string.settings_quality_battery
                                },
                            ),
                            onClick = { page = SettingsPage.QUALITY },
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_cache_title),
                            summary = cacheSummary,
                            onClick = { page = SettingsPage.CACHE },
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = stringResource(R.string.updates_title),
                            summary = stringResource(
                                if (updateChannel == UpdateChannel.STABLE) {
                                    R.string.updates_channel_stable
                                } else {
                                    R.string.updates_channel_beta
                                },
                            ),
                            onClick = { page = SettingsPage.UPDATES },
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_credits_title),
                            summary = stringResource(R.string.settings_credits_summary),
                            onClick = onOpenCredits,
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_about_title),
                            summary = stringResource(
                                R.string.settings_about_version, versionName,
                            ),
                            onClick = { page = SettingsPage.ABOUT },
                        )
                    }
                }

                SettingsPage.MAP -> {
                    GevTopBar(
                        title = stringResource(R.string.settings_basemap_title),
                        onBack = { page = SettingsPage.HUB },
                        backContentDescription = stringResource(R.string.settings_back),
                    )
                    SettingsBody {
                        RadioOption(
                            label = stringResource(R.string.basemap_esri),
                            selected = basemap == Basemap.ESRI,
                            onSelect = { onBasemapSelected(Basemap.ESRI) },
                        )
                        RadioOption(
                            label = stringResource(R.string.basemap_osm),
                            selected = basemap == Basemap.OSM,
                            onSelect = { onBasemapSelected(Basemap.OSM) },
                        )
                        if (hasToken) {
                            RadioOption(
                                label = stringResource(R.string.basemap_photorealistic),
                                selected = basemap == Basemap.PHOTOREALISTIC,
                                onSelect = { onBasemapSelected(Basemap.PHOTOREALISTIC) },
                            )
                        }
                        if (hasGoogleKey) {
                            // Q9 tier 3: direct Google 3D, selectable once
                            // a Google key exists in the KeyVault (#5).
                            RadioOption(
                                label = stringResource(R.string.basemap_google_direct),
                                selected = basemap == Basemap.GOOGLE_DIRECT,
                                onSelect = { onBasemapSelected(Basemap.GOOGLE_DIRECT) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_basemap_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GevColors.TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_terrain_terrarium),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GevColors.TextSecondary,
                        )
                    }
                }

                SettingsPage.QUALITY -> {
                    GevTopBar(
                        title = stringResource(R.string.settings_quality_title),
                        onBack = { page = SettingsPage.HUB },
                        backContentDescription = stringResource(R.string.settings_back),
                    )
                    SettingsBody {
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
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_quality_effective,
                                    effectiveQualityLabel,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GevColors.TextSecondary,
                            )
                        }
                    }
                }

                SettingsPage.CACHE -> {
                    GevTopBar(
                        title = stringResource(R.string.settings_cache_title),
                        onBack = { page = SettingsPage.HUB },
                        backContentDescription = stringResource(R.string.settings_back),
                    )
                    SettingsBody {
                        if (cacheSummary.isNotEmpty()) {
                            Text(
                                text = cacheSummary,
                                style = MaterialTheme.typography.bodyLarge,
                                color = GevColors.TextPrimary,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        listOf(100, 250, 500).forEach { limit ->
                            RadioOption(
                                label = stringResource(
                                    R.string.settings_cache_limit_option, limit,
                                ),
                                selected = cacheLimitMb == limit,
                                onSelect = { onCacheLimitSelected(limit) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_cache_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GevColors.TextSecondary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onClearCache,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text(text = stringResource(R.string.settings_cache_clear))
                        }
                    }
                }

                SettingsPage.UPDATES -> {
                    GevTopBar(
                        title = stringResource(R.string.updates_title),
                        onBack = { page = SettingsPage.HUB },
                        backContentDescription = stringResource(R.string.settings_back),
                    )
                    SettingsBody {
                        Text(
                            text = stringResource(R.string.updates_channel_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = GevColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        RadioOption(
                            label = stringResource(R.string.updates_channel_stable),
                            selected = updateChannel == UpdateChannel.STABLE,
                            onSelect = { onUpdateChannelSelected(UpdateChannel.STABLE) },
                        )
                        RadioOption(
                            label = stringResource(R.string.updates_channel_beta),
                            selected = updateChannel == UpdateChannel.BETA,
                            onSelect = { onUpdateChannelSelected(UpdateChannel.BETA) },
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onCheckUpdates,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text(text = stringResource(R.string.updates_check))
                        }
                        if (updateStatus.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = updateStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GevColors.TextSecondary,
                            )
                        }
                    }
                }

                SettingsPage.ABOUT -> {
                    GevTopBar(
                        title = stringResource(R.string.settings_about_title),
                        onBack = { page = SettingsPage.HUB },
                        backContentDescription = stringResource(R.string.settings_back),
                    )
                    SettingsBody {
                        Text(
                            text = stringResource(R.string.shell_wordmark),
                            style = MaterialTheme.typography.titleMedium,
                            color = GevColors.TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.shell_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = GevColors.TextSecondary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(
                                R.string.settings_about_version, versionName,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = GevColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = basemapLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GevColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** One hub row: title + one-line summary + chevron, 64dp tall. */
@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = GevColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GevColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = GevColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = GevColors.GlassBorder,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** Scrollable body for a settings sub-screen. */
@Composable
private fun SettingsBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
    ) {
        content()
    }
}

/** A full-width radio row: the whole 52dp row is the tap target. */
@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = GevColors.TextPrimary,
        )
    }
}
