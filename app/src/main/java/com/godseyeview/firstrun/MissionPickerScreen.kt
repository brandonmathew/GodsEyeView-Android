package com.godseyeview.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.Kicker

/** The first-run mission picker (issue #10 content; Q24 mobile design,
 *  issue #19). Content is the original's: MISSION CONTROL · FIRST LAUNCH,
 *  "Welcome to God's Eye View", the lead line, the four mission tiles,
 *  ENTER GOD MODE, SKIP, "Don't show this again". The layout is a
 *  mobile flow designed for phones:
 *  - a fully OPAQUE full-screen Surface — MainActivity does not compose
 *    the globe shell behind it, so nothing bleeds through (beta.2's
 *    translucent-overlay defect);
 *  - every heading uses the light text token explicitly (beta.2's
 *    near-black-on-dark defect);
 *  - scrollable content with a sticky bottom ENTER bar;
 *  - radio tiles at least 72dp tall. */
@Composable
fun MissionPickerScreen(
    initialMission: Mission = Mission.EXPLORE,
    onEnter: (mission: Mission, suppress: Boolean) -> Unit,
    onSkip: (suppress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(initialMission) }
    var suppress by remember { mutableStateOf(false) }
    Surface(
        color = GevColors.BgDark,
        contentColor = GevColors.TextPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Kicker(
                        text = stringResource(R.string.firstrun_kicker),
                        color = GevColors.Accent,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSkip(suppress) }) {
                        Text(
                            text = stringResource(R.string.firstrun_skip),
                            color = GevColors.Accent,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.firstrun_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = GevColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.firstrun_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GevColors.TextSecondary,
                )
                Spacer(Modifier.height(24.dp))
                Kicker(text = stringResource(R.string.firstrun_start_heading))
                Spacer(Modifier.height(12.dp))
                MissionTile(
                    title = stringResource(R.string.firstrun_live_title),
                    sub = stringResource(R.string.firstrun_live_sub),
                    selected = selected == Mission.LIVE_CONTACTS,
                    onClick = { selected = Mission.LIVE_CONTACTS },
                )
                MissionTile(
                    title = stringResource(R.string.firstrun_space_title),
                    sub = stringResource(R.string.firstrun_space_sub),
                    selected = selected == Mission.SPACE_MISSIONS,
                    onClick = { selected = Mission.SPACE_MISSIONS },
                )
                MissionTile(
                    title = stringResource(R.string.firstrun_environmental_title),
                    sub = stringResource(R.string.firstrun_environmental_sub),
                    selected = selected == Mission.ENVIRONMENTAL,
                    onClick = { selected = Mission.ENVIRONMENTAL },
                )
                MissionTile(
                    title = stringResource(R.string.firstrun_explore_title),
                    sub = stringResource(R.string.firstrun_explore_sub),
                    selected = selected == Mission.EXPLORE,
                    onClick = { selected = Mission.EXPLORE },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.firstrun_change_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = GevColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { suppress = !suppress },
                ) {
                    Checkbox(checked = suppress, onCheckedChange = { suppress = it })
                    Text(
                        text = stringResource(R.string.firstrun_suppress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GevColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            // Sticky bottom action bar: ENTER is always reachable.
            Surface(
                color = GevColors.MenuBg,
                contentColor = GevColors.TextPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onEnter(selected, suppress) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(56.dp),
                ) {
                    Text(
                        text = stringResource(R.string.firstrun_enter),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionTile(
    title: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .heightIn(min = 72.dp)
            .background(
                if (selected) GevColors.AccentDim else GevColors.CardBg,
                RoundedCornerShape(16.dp),
            )
            .border(
                1.dp,
                if (selected) GevColors.Accent else GevColors.GlassBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GevColors.TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = GevColors.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    2.dp,
                    if (selected) GevColors.Accent else GevColors.TextDim,
                    CircleShape,
                )
                .padding(4.dp)
                .background(
                    if (selected) GevColors.Accent else GevColors.CardBg,
                    CircleShape,
                ),
        )
    }
}
