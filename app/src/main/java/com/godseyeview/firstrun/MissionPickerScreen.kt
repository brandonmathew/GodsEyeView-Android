package com.godseyeview.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.Kicker

/** The first-run mission picker (issue #10 content; Q24 mobile design,
 *  issue #19; no-scroll fix, beta.4). Content is the original's:
 *  MISSION CONTROL · FIRST LAUNCH, "Welcome to God's Eye View", the lead
 *  line, the four mission tiles, ENTER GOD MODE, SKIP, "Don't show this
 *  again". The layout is a mobile flow designed for phones:
 *  - a fully OPAQUE full-screen Surface — MainActivity does not compose
 *    the globe shell behind it, so nothing bleeds through (beta.2's
 *    translucent-overlay defect);
 *  - every heading uses the light text token explicitly (beta.2's
 *    near-black-on-dark defect);
 *  - NO SCROLLING (Brandon, beta.4): the whole flow — header, title,
 *    lead, the four tiles, helper line, suppress row and the ENTER bar —
 *    fits inside one screen height. [PickerLayout] picks one of three
 *    spacing tiers from the height BoxWithConstraints actually offers,
 *    and weighted spacers absorb any surplus on tall screens, so the
 *    fixed budget below is the worst case, never an overflow.
 *
 *  Fixed-height budget (default font scale, 360dp-wide phone → 320dp
 *  content width; Material 3 line heights: headlineMedium 36sp,
 *  headlineSmall 32sp, titleMedium 24sp, bodyMedium 20sp, bodySmall
 *  16sp):
 *  - Regular (≥800dp offered): content 692dp + 80dp ENTER bar = 772dp.
 *    Title wraps to 2 lines at this width, lead to 3, tile subs to 2.
 *  - Compact (640–799dp): content 564dp + 64dp bar = 628dp. Title is
 *    headlineSmall (1 line), tile subs drop to bodySmall.
 *  - Short (<640dp): content 504dp + 64dp bar = 568dp. The lead drops
 *    to bodySmall (2 lines) rather than reintroducing scroll.
 *  So the picker fits without scrolling down to ~570dp of offered
 *  height at phone widths ≥360dp. Narrower screens (<360dp wide) can
 *  add one title line (~+32dp).
 *
 *  LANDSCAPE (offered width > height; Brandon-approved two-column
 *  design, issue #10 follow-up): the portrait stack genuinely cannot
 *  fit a ~360dp-tall screen, so the layout changes shape instead of
 *  shrinking — header row (kicker + SKIP) across the top, a text
 *  column on the left (title, lead, start heading, helper line,
 *  suppress row) and the four mission tiles in a 2×2 grid on the
 *  right with ENTER GOD MODE under the grid. Still no scrolling.
 *  Budget (default font scale): header row 40dp (the SKIP TextButton)
 *  + top/bottom padding; the right column's fixed content is the grid
 *  (2 rows of 78dp tiles + gap) + the 48dp ENTER button = 212–218dp;
 *  the left column's worst case is title 64 (2 lines) + lead 80
 *  (4 lines bodyMedium at ~260dp column width) + heading 16 + helper
 *  32 (2 lines) + suppress row 48 + gaps = 268dp. Regular landscape
 *  tier total = 8 + 40 + 6 + 268 + 8 = 330dp; the compact tier (lead
 *  drops to bodySmall, gaps compress) totals 4 + 40 + 2 + 222 + 4 =
 *  272dp. So landscape fits without scrolling down to ~275dp of
 *  offered height at widths ≥~700dp; weighted spacers absorb surplus
 *  above the budgets. In the grid cells the longest caps title
 *  ("EXPLORE MANUALLY") drops from titleMedium to titleSmall — and
 *  to labelMedium in cells narrower than 160dp — rather than
 *  wrapping; below ~275dp height (extreme split-screen) the compact
 *  budget bottoms out, as portrait's does. */
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                LandscapePickerContent(
                    selected = selected,
                    onSelect = { selected = it },
                    suppress = suppress,
                    onSuppressChange = { suppress = it },
                    onEnter = { onEnter(selected, suppress) },
                    onSkip = { onSkip(suppress) },
                    compact = maxHeight < LANDSCAPE_COMPACT_MIN_HEIGHT,
                )
                return@BoxWithConstraints
            }
            val layout = when {
                maxHeight >= PickerLayout.REGULAR_MIN_HEIGHT ->
                    PickerLayout.Regular
                maxHeight >= PickerLayout.COMPACT_MIN_HEIGHT ->
                    PickerLayout.Compact
                else -> PickerLayout.Short
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .padding(top = layout.topPadding),
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
                    Spacer(Modifier.height(layout.afterHeader))
                    Text(
                        text = stringResource(R.string.firstrun_title),
                        style = if (layout == PickerLayout.Regular) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        color = GevColors.TextPrimary,
                    )
                    Spacer(Modifier.height(layout.afterTitle))
                    Text(
                        text = stringResource(R.string.firstrun_lead),
                        style = if (layout.smallLead) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = GevColors.TextSecondary,
                    )
                    // Surplus height (tall screens) is absorbed here and
                    // after the suppress row; both spacers bottom out at
                    // 0 in the fixed budget above.
                    Spacer(Modifier.weight(1f))
                    Kicker(text = stringResource(R.string.firstrun_start_heading))
                    Spacer(Modifier.height(layout.afterHeading))
                    MissionTile(
                        title = stringResource(R.string.firstrun_live_title),
                        sub = stringResource(R.string.firstrun_live_sub),
                        selected = selected == Mission.LIVE_CONTACTS,
                        onClick = { selected = Mission.LIVE_CONTACTS },
                        layout = layout,
                    )
                    Spacer(Modifier.height(layout.tileGap))
                    MissionTile(
                        title = stringResource(R.string.firstrun_space_title),
                        sub = stringResource(R.string.firstrun_space_sub),
                        selected = selected == Mission.SPACE_MISSIONS,
                        onClick = { selected = Mission.SPACE_MISSIONS },
                        layout = layout,
                    )
                    Spacer(Modifier.height(layout.tileGap))
                    MissionTile(
                        title = stringResource(R.string.firstrun_environmental_title),
                        sub = stringResource(R.string.firstrun_environmental_sub),
                        selected = selected == Mission.ENVIRONMENTAL,
                        onClick = { selected = Mission.ENVIRONMENTAL },
                        layout = layout,
                    )
                    Spacer(Modifier.height(layout.tileGap))
                    MissionTile(
                        title = stringResource(R.string.firstrun_explore_title),
                        sub = stringResource(R.string.firstrun_explore_sub),
                        selected = selected == Mission.EXPLORE,
                        onClick = { selected = Mission.EXPLORE },
                        layout = layout,
                    )
                    Spacer(Modifier.height(layout.afterTiles))
                    Text(
                        text = stringResource(R.string.firstrun_change_later),
                        style = MaterialTheme.typography.bodySmall,
                        color = GevColors.TextSecondary,
                    )
                    Spacer(Modifier.height(layout.afterHelper))
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
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(layout.bottomPadding))
                }
                // Bottom action bar: ENTER closes the flow, always visible
                // because nothing scrolls.
                Surface(
                    color = GevColors.MenuBg,
                    contentColor = GevColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onEnter(selected, suppress) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = layout.barPadding,
                            )
                            .height(layout.buttonHeight),
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
}

/** Offered height below which landscape switches to its compact tier
 *  (the regular landscape budget totals 330dp). */
private val LANDSCAPE_COMPACT_MIN_HEIGHT = 330.dp

/** The landscape two-column picker (see the budget in
 *  [MissionPickerScreen]'s doc). Same content, same behaviour — only
 *  the arrangement changes: text column left, 2×2 tile grid + ENTER
 *  right, both columns bottom-anchoring their last element with a
 *  weighted spacer so the two columns line up on tall screens. */
@Composable
private fun LandscapePickerContent(
    selected: Mission,
    onSelect: (Mission) -> Unit,
    suppress: Boolean,
    onSuppressChange: (Boolean) -> Unit,
    onEnter: () -> Unit,
    onSkip: () -> Unit,
    compact: Boolean,
) {
    val layout =
        if (compact) PickerLayout.LandscapeCompact else PickerLayout.Landscape
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = layout.topPadding, bottom = layout.bottomPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(
                text = stringResource(R.string.firstrun_kicker),
                color = GevColors.Accent,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.firstrun_skip),
                    color = GevColors.Accent,
                )
            }
        }
        Spacer(Modifier.height(layout.afterHeader))
        Row(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = stringResource(R.string.firstrun_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = GevColors.TextPrimary,
                )
                Spacer(Modifier.height(layout.afterTitle))
                Text(
                    text = stringResource(R.string.firstrun_lead),
                    style = if (layout.smallLead) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = GevColors.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Kicker(text = stringResource(R.string.firstrun_start_heading))
                Spacer(Modifier.height(layout.afterHeading))
                Text(
                    text = stringResource(R.string.firstrun_change_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = GevColors.TextSecondary,
                )
                Spacer(Modifier.height(layout.afterHelper))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onSuppressChange(!suppress) },
                ) {
                    Checkbox(
                        checked = suppress,
                        onCheckedChange = onSuppressChange,
                    )
                    Text(
                        text = stringResource(R.string.firstrun_suppress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GevColors.TextPrimary,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
            ) {
                Row {
                    MissionTile(
                        title = stringResource(R.string.firstrun_live_title),
                        sub = stringResource(R.string.firstrun_live_sub),
                        selected = selected == Mission.LIVE_CONTACTS,
                        onClick = { onSelect(Mission.LIVE_CONTACTS) },
                        layout = layout,
                        grid = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(layout.tileGap))
                    MissionTile(
                        title = stringResource(R.string.firstrun_space_title),
                        sub = stringResource(R.string.firstrun_space_sub),
                        selected = selected == Mission.SPACE_MISSIONS,
                        onClick = { onSelect(Mission.SPACE_MISSIONS) },
                        layout = layout,
                        grid = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(layout.tileGap))
                Row {
                    MissionTile(
                        title = stringResource(
                            R.string.firstrun_environmental_title,
                        ),
                        sub = stringResource(R.string.firstrun_environmental_sub),
                        selected = selected == Mission.ENVIRONMENTAL,
                        onClick = { onSelect(Mission.ENVIRONMENTAL) },
                        layout = layout,
                        grid = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(layout.tileGap))
                    MissionTile(
                        title = stringResource(R.string.firstrun_explore_title),
                        sub = stringResource(R.string.firstrun_explore_sub),
                        selected = selected == Mission.EXPLORE,
                        onClick = { onSelect(Mission.EXPLORE) },
                        layout = layout,
                        grid = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onEnter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.buttonHeight),
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

/** Spacing/size tiers for the no-scroll picker, chosen from the offered
 *  height. Tile heights keep every tile a ≥48dp touch target while its
 *  text (title 24dp + 2dp + sub) fits exactly: Regular subs are
 *  bodyMedium (2 lines = 40dp, tile 5+24+2+40+5 = 76dp), Compact/Short
 *  subs are bodySmall (2 lines = 32dp, tiles 3+24+2+32+3 = 64dp and
 *  2+24+2+32+2 = 62dp). */
private enum class PickerLayout(
    val topPadding: Dp,
    val afterHeader: Dp,
    val afterTitle: Dp,
    val afterHeading: Dp,
    val tileHeight: Dp,
    val tilePaddingV: Dp,
    val tileGap: Dp,
    val afterTiles: Dp,
    val afterHelper: Dp,
    val bottomPadding: Dp,
    val barPadding: Dp,
    val buttonHeight: Dp,
    val smallLead: Boolean,
    val smallSub: Boolean,
) {
    Regular(
        topPadding = 20.dp, afterHeader = 12.dp, afterTitle = 8.dp,
        afterHeading = 12.dp, tileHeight = 76.dp, tilePaddingV = 5.dp,
        tileGap = 10.dp, afterTiles = 12.dp, afterHelper = 8.dp,
        bottomPadding = 10.dp, barPadding = 12.dp, buttonHeight = 56.dp,
        smallLead = false, smallSub = false,
    ),
    Compact(
        topPadding = 12.dp, afterHeader = 8.dp, afterTitle = 6.dp,
        afterHeading = 8.dp, tileHeight = 64.dp, tilePaddingV = 3.dp,
        tileGap = 6.dp, afterTiles = 8.dp, afterHelper = 6.dp,
        bottomPadding = 6.dp, barPadding = 8.dp, buttonHeight = 48.dp,
        smallLead = false, smallSub = true,
    ),
    Short(
        topPadding = 8.dp, afterHeader = 4.dp, afterTitle = 4.dp,
        afterHeading = 6.dp, tileHeight = 62.dp, tilePaddingV = 2.dp,
        tileGap = 4.dp, afterTiles = 6.dp, afterHelper = 4.dp,
        bottomPadding = 4.dp, barPadding = 8.dp, buttonHeight = 48.dp,
        smallLead = true, smallSub = true,
    ),

    /** Landscape two-column tiers: [tileGap] doubles as the grid's
     *  horizontal and vertical gap, [afterTiles]/[barPadding] go unused
     *  (ENTER sits in the right column, not a bottom bar). The 78dp
     *  grid tile fits title 20 (titleSmall, 1 line) + 2 + sub 48
     *  (3 lines bodySmall) + 3dp padding top/bottom = 76dp. */
    Landscape(
        topPadding = 8.dp, afterHeader = 6.dp, afterTitle = 6.dp,
        afterHeading = 8.dp, tileHeight = 78.dp, tilePaddingV = 3.dp,
        tileGap = 8.dp, afterTiles = 0.dp, afterHelper = 6.dp,
        bottomPadding = 8.dp, barPadding = 0.dp, buttonHeight = 48.dp,
        smallLead = false, smallSub = true,
    ),
    LandscapeCompact(
        topPadding = 4.dp, afterHeader = 2.dp, afterTitle = 2.dp,
        afterHeading = 4.dp, tileHeight = 78.dp, tilePaddingV = 3.dp,
        tileGap = 6.dp, afterTiles = 0.dp, afterHelper = 4.dp,
        bottomPadding = 4.dp, barPadding = 0.dp, buttonHeight = 48.dp,
        smallLead = true, smallSub = true,
    ),
    ;

    companion object {
        /** Offered height at/above which the Regular budget (772dp) fits. */
        val REGULAR_MIN_HEIGHT = 800.dp

        /** Offered height at/above which the Compact budget (628dp) fits. */
        val COMPACT_MIN_HEIGHT = 640.dp
    }
}

@Composable
private fun MissionTile(
    title: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    layout: PickerLayout,
    grid: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(layout.tileHeight)
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
            .padding(horizontal = 16.dp, vertical = layout.tilePaddingV),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (grid) {
                // Grid cells are ~half the portrait tile width: the
                // longest caps title needs titleSmall here (and
                // labelMedium in very narrow cells) to stay on one
                // line — wrapping it would break the fixed tile height.
                BoxWithConstraints {
                    Text(
                        text = title,
                        style = if (maxWidth >= 160.dp) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        color = GevColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GevColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = subStyle(layout),
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

@Composable
private fun subStyle(layout: PickerLayout): TextStyle =
    if (layout.smallSub) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
