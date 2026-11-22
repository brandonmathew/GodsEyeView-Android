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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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

/** The first-run mission picker, modelled on upstream v0.3.0's
 *  WelcomeView.vue (issue #10, Q20/Q23): MISSION CONTROL · FIRST LAUNCH
 *  kicker, "Welcome to God's Eye View", the original's lead line, the
 *  mission tiles, ENTER GOD MODE, SKIP, and "Don't show this again".
 *  Phone adaptation: upstream's three steps (mode / start / tips) are
 *  folded into one scrollable card — the mode step's Analyst mode is
 *  "coming soon" upstream and GEV Stories / social links are web
 *  surfaces, so neither is carried. The four mission tiles are the
 *  start step, with Live Contacts added per the v2 plan (Q20). */
@Composable
fun MissionPickerScreen(
    initialMission: Mission = Mission.EXPLORE,
    onEnter: (mission: Mission, suppress: Boolean) -> Unit,
    onSkip: (suppress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(initialMission) }
    var suppress by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GevColors.BgDark.copy(alpha = 0.94f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.firstrun_kicker),
                    style = MaterialTheme.typography.labelMedium,
                    color = GevColors.Accent,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSkip(suppress) }) {
                    Text(stringResource(R.string.firstrun_skip))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.firstrun_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.firstrun_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.firstrun_start_heading),
                style = MaterialTheme.typography.labelMedium,
                color = GevColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.firstrun_change_later),
                style = MaterialTheme.typography.bodySmall,
                color = GevColors.TextDim,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { suppress = !suppress },
            ) {
                Checkbox(checked = suppress, onCheckedChange = { suppress = it })
                Text(
                    text = stringResource(R.string.firstrun_suppress),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onEnter(selected, suppress) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.firstrun_enter))
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
            .background(
                if (selected) GevColors.AccentDim else GevColors.Glass,
                RoundedCornerShape(16.dp),
            )
            .border(
                1.dp,
                if (selected) GevColors.Accent else GevColors.GlassBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    2.dp,
                    if (selected) GevColors.Accent else GevColors.TextDim,
                    CircleShape,
                )
                .padding(4.dp)
                .background(
                    if (selected) GevColors.Accent else GevColors.Glass,
                    CircleShape,
                ),
        )
    }
}
