package com.godseyeview.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.globe.Basemap
import com.godseyeview.core.ui.DockCell
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.glassPanel

/** The globe shell, designed mobile-first (Q24, issue #19) to replace
 *  beta.2's transplanted desktop chrome:
 *  - ONE compact top bar: small wordmark left, a single basemap/style
 *    chip with a dropdown menu right. Nothing overlaps at 360dp.
 *  - ONE compact bottom dock: five icon+label cells in a single row.
 *  - Secondary actions (Power Up, Reset Globe, Globe status, Settings)
 *    live in the "More" bottom sheet — no second floating slab.
 *  The Cesium attribution keeps its own strip: MainActivity insets the
 *  WebView above the dock, so the credit line never sits under chrome. */

/** Footprint the dock occupies from the bottom of the screen: the 72dp
 *  panel (64dp cells + padding) plus its 12dp bottom margin. MainActivity
 *  insets the globe by this plus a credit strip. */
val DOCK_HEIGHT = 84.dp

/** Clear strip reserved under the WebView for the Cesium credit line. */
val CREDIT_STRIP_HEIGHT = 22.dp

@Composable
fun GlobeTopBar(
    basemap: Basemap,
    basemapOptions: List<Basemap>,
    onBasemapSelected: (Basemap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val wordmark = stringResource(R.string.shell_wordmark)
    val split = wordmark.lastIndexOf(' ')
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    if (split > 0) {
                        append(wordmark.substring(0, split + 1))
                        withStyle(SpanStyle(color = GevColors.Accent)) {
                            append(wordmark.substring(split + 1))
                        }
                    } else {
                        append(wordmark)
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GevColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.shell_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = GevColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .clickable { menuOpen = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(basemapLabelRes(basemap)),
                    style = MaterialTheme.typography.labelMedium,
                    color = GevColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.shell_basemap_menu),
                    tint = GevColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.shell_active_style),
                            style = MaterialTheme.typography.labelSmall,
                            color = GevColors.TextSecondary,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
                HorizontalDivider()
                basemapOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(basemapLabelRes(option))) },
                        onClick = {
                            menuOpen = false
                            onBasemapSelected(option)
                        },
                        trailingIcon = if (option == basemap) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = GevColors.Accent,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

private fun basemapLabelRes(basemap: Basemap): Int = when (basemap) {
    Basemap.OSM -> R.string.basemap_osm
    Basemap.PHOTOREALISTIC -> R.string.basemap_photorealistic
    Basemap.GOOGLE_DIRECT -> R.string.basemap_google_direct
    Basemap.ESRI -> R.string.basemap_esri
}

/** The single-row mobile dock: Search · Locate · Share · Layers · More.
 *  Icon over a one-word label, 64dp tall cells, labels never wrap. */
@Composable
fun GlobeDock(
    onSearch: () -> Unit,
    onLocate: () -> Unit,
    onShare: () -> Unit,
    onLayers: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockCell(
            icon = Icons.Filled.Search,
            label = stringResource(R.string.search_button),
            onClick = onSearch,
        )
        DockCell(
            icon = Icons.Filled.MyLocation,
            label = stringResource(R.string.dock_locate),
            onClick = onLocate,
        )
        DockCell(
            icon = Icons.Filled.Share,
            label = stringResource(R.string.share_button),
            onClick = onShare,
        )
        DockCell(
            icon = Icons.Filled.Layers,
            label = stringResource(R.string.layers_button),
            onClick = onLayers,
        )
        DockCell(
            icon = Icons.Filled.MoreVert,
            label = stringResource(R.string.dock_more),
            onClick = onMore,
        )
    }
}

/** The "More" bottom sheet: the secondary actions beta.2 kept in a
 *  second floating slab (Reset Globe, Globe status, Settings), plus
 *  Power Up while provider keys are missing. Standard rows, 56dp+. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    showPowerUp: Boolean,
    powerUpSetCount: Int,
    powerUpTotal: Int,
    onPowerUp: () -> Unit,
    onResetGlobe: () -> Unit,
    onGlobeStatus: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GevColors.MenuBg,
        contentColor = GevColors.TextPrimary,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.more_title),
                style = MaterialTheme.typography.labelMedium,
                color = GevColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (showPowerUp) {
                MoreRow(
                    icon = Icons.Filled.Bolt,
                    title = stringResource(R.string.powerup_chip),
                    summary = stringResource(
                        R.string.more_powerup_summary, powerUpSetCount, powerUpTotal,
                    ),
                    accent = true,
                    onClick = onPowerUp,
                )
            }
            MoreRow(
                icon = Icons.Filled.RestartAlt,
                title = stringResource(R.string.reset_globe),
                summary = null,
                accent = false,
                onClick = onResetGlobe,
            )
            MoreRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.globe_status),
                summary = null,
                accent = false,
                onClick = onGlobeStatus,
            )
            MoreRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_button),
                summary = null,
                accent = false,
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun MoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String?,
    accent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) GevColors.Accent else GevColors.TextPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (accent) GevColors.Accent else GevColors.TextPrimary,
                maxLines = 1,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GevColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
