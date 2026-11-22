package com.godseyeview.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.GevTopBar

/** POWER UP — Provider Settings (issue #5 content; Q24 mobile design,
 *  issue #19). The rows are the original's, in the original's order, with
 *  the original's "unlocks" wording and tier tags. The layout is a
 *  mobile flow: a top app bar, a scrollable list of provider cards, and
 *  a sticky bottom bar carrying the status line and SAVE KEYS, so Save
 *  is always reachable. Android differences from upstream: keys are
 *  written through [KeyVault] / [TokenStore] (AES-GCM, Android Keystore)
 *  instead of a server config file, there is no server restart, and the
 *  ChatGPT OAuth toggle is absent — Codex OAuth voice is dropped on
 *  Android (Q13). The optional Overpass server URL (Q16) rides at the
 *  bottom as a non-key card: it is a plain preference in [SettingsStore]. */
private data class ProviderRow(
    val id: String,
    val titleRes: Int,
    val unlocksRes: Int,
    val envVar: String,
    val tierRes: Int,
    val getUrl: String,
    val globeSide: Boolean,
    val vaultKey: ProviderKey?,
)

private val PROVIDER_ROWS = listOf(
    ProviderRow(
        id = "google", titleRes = R.string.powerup_google_title,
        unlocksRes = R.string.powerup_google_unlocks,
        envVar = "GOOGLE_MAPS_API_KEY", tierRes = R.string.powerup_tier_metered,
        getUrl = "https://developers.google.com/maps/documentation/tile/get-api-key",
        globeSide = true, vaultKey = ProviderKey.GOOGLE,
    ),
    ProviderRow(
        id = "openai", titleRes = R.string.powerup_openai_title,
        unlocksRes = R.string.powerup_openai_unlocks,
        envVar = "OPENAI_API_KEY", tierRes = R.string.powerup_tier_metered,
        getUrl = "https://platform.openai.com/api-keys",
        globeSide = false, vaultKey = ProviderKey.OPENAI,
    ),
    ProviderRow(
        id = "gemini", titleRes = R.string.powerup_gemini_title,
        unlocksRes = R.string.powerup_gemini_unlocks,
        envVar = "GEMINI_API_KEY", tierRes = R.string.powerup_tier_metered,
        getUrl = "https://aistudio.google.com/apikey",
        globeSide = false, vaultKey = ProviderKey.GEMINI,
    ),
    ProviderRow(
        id = "aisstream", titleRes = R.string.powerup_aisstream_title,
        unlocksRes = R.string.powerup_aisstream_unlocks,
        envVar = "AISSTREAM_API_KEY", tierRes = R.string.powerup_tier_free,
        getUrl = "https://aisstream.io",
        globeSide = false, vaultKey = ProviderKey.AISSTREAM,
    ),
    ProviderRow(
        id = "firms", titleRes = R.string.powerup_firms_title,
        unlocksRes = R.string.powerup_firms_unlocks,
        envVar = "FIRMS_MAP_KEY", tierRes = R.string.powerup_tier_free,
        getUrl = "https://firms.modaps.eosdis.nasa.gov/api/map_key/",
        globeSide = false, vaultKey = ProviderKey.FIRMS,
    ),
    ProviderRow(
        id = "tomtom", titleRes = R.string.powerup_tomtom_title,
        unlocksRes = R.string.powerup_tomtom_unlocks,
        envVar = "TOMTOM_API_KEY", tierRes = R.string.powerup_tier_free,
        getUrl = "https://my.tomtom.com/keys",
        globeSide = false, vaultKey = ProviderKey.TOMTOM,
    ),
    ProviderRow(
        id = "ion", titleRes = R.string.powerup_ion_title,
        unlocksRes = R.string.powerup_ion_unlocks,
        envVar = "CESIUM_ION_TOKEN", tierRes = R.string.powerup_tier_free,
        getUrl = "https://ion.cesium.com/tokens",
        globeSide = true, vaultKey = null, // TokenStore, not KeyVault
    ),
    ProviderRow(
        id = "mapillary", titleRes = R.string.powerup_mapillary_title,
        unlocksRes = R.string.powerup_mapillary_unlocks,
        envVar = "MAPILLARY_CLIENT_TOKEN", tierRes = R.string.powerup_tier_free,
        getUrl = "https://www.mapillary.com/dashboard/developers",
        globeSide = true, vaultKey = ProviderKey.MAPILLARY,
    ),
)

/** How many of the panel's provider keys are currently stored. Drives the
 *  POWER UP row in the globe's More sheet (shown while keys are missing). */
fun powerUpSetCount(context: android.content.Context): Int {
    val vault = KeyVault(context)
    var count = if (TokenStore(context).hasToken()) 1 else 0
    count += ProviderKey.entries.count { vault.has(it) }
    return count
}

const val POWER_UP_TOTAL = 8

@Composable
fun PowerUpScreen(
    onBack: () -> Unit,
    onIonTokenChanged: (String?) -> Unit = {},
    onGoogleKeyChanged: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyVault = remember { KeyVault(context) }
    val tokenStore = remember { TokenStore(context) }
    val settingsStore = remember { SettingsStore(context) }

    fun rowIsSet(row: ProviderRow): Boolean =
        if (row.vaultKey != null) keyVault.has(row.vaultKey) else tokenStore.hasToken()

    var setSnapshot by remember { mutableStateOf(PROVIDER_ROWS.map { rowIsSet(it) }) }
    val drafts = remember { mutableStateMapOf<String, String>() }
    var overpassDraft by remember { mutableStateOf(settingsStore.loadOverpassUrl() ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    val savedText = stringResource(R.string.powerup_saved)
    val removedText = stringResource(R.string.powerup_removed)

    fun refreshSnapshot() {
        setSnapshot = PROVIDER_ROWS.map { rowIsSet(it) }
    }

    fun saveAll() {
        PROVIDER_ROWS.forEach { row ->
            val value = drafts[row.id]?.trim().orEmpty()
            if (value.isEmpty()) return@forEach
            if (row.vaultKey != null) {
                keyVault.save(row.vaultKey, value)
                if (row.vaultKey == ProviderKey.GOOGLE) {
                    onGoogleKeyChanged(value)
                }
            } else {
                tokenStore.saveToken(value)
                onIonTokenChanged(value)
            }
            drafts.remove(row.id)
        }
        settingsStore.saveOverpassUrl(overpassDraft.trim().ifEmpty { null })
        refreshSnapshot()
        status = savedText
    }

    Surface(
        color = GevColors.BgDark,
        contentColor = GevColors.TextPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GevTopBar(
                title = stringResource(R.string.powerup_title),
                subtitle = stringResource(R.string.powerup_kicker),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.powerup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GevColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))

                PROVIDER_ROWS.forEachIndexed { index, row ->
                    val isSet = setSnapshot.getOrElse(index) { false }
                    ProviderKeyCard(
                        row = row,
                        isSet = isSet,
                        draft = drafts[row.id] ?: "",
                        onDraftChange = { drafts[row.id] = it },
                        onGetKey = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(row.getUrl)),
                            )
                        },
                        onRemove = {
                            if (row.vaultKey != null) {
                                keyVault.clear(row.vaultKey)
                                if (row.vaultKey == ProviderKey.GOOGLE) {
                                    onGoogleKeyChanged(null)
                                }
                            } else {
                                tokenStore.clearToken()
                                onIonTokenChanged(null)
                            }
                            drafts.remove(row.id)
                            refreshSnapshot()
                            status = removedText
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Q16: optional own Overpass server. Not a key — a plain
                // URL preference; blank means reduced public-Overpass
                // behaviour.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GevColors.CardBg, RoundedCornerShape(16.dp))
                        .border(1.dp, GevColors.GlassBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.powerup_overpass_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = GevColors.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.powerup_overpass_unlocks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GevColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = overpassDraft,
                        onValueChange = { overpassDraft = it },
                        label = {
                            Text(stringResource(R.string.powerup_overpass_field_label))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            // Sticky bottom bar: status line + SAVE KEYS, always reachable.
            Surface(
                color = GevColors.MenuBg,
                contentColor = GevColors.TextPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = status ?: stringResource(R.string.powerup_status_default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GevColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { saveAll() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text(stringResource(R.string.powerup_save)) }
                }
            }
        }
    }
}

@Composable
private fun ProviderKeyCard(
    row: ProviderRow,
    isSet: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onGetKey: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GevColors.CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, GevColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isSet) GevColors.Accent else GevColors.TextDim,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(row.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = GevColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(row.tierRes),
                style = MaterialTheme.typography.labelSmall,
                color = GevColors.Accent,
            )
        }
        if (row.globeSide) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.powerup_globe_side),
                style = MaterialTheme.typography.labelSmall,
                color = GevColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(row.unlocksRes),
            style = MaterialTheme.typography.bodyMedium,
            color = GevColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = {
                Text(
                    if (isSet) {
                        stringResource(R.string.powerup_field_saved, row.envVar)
                    } else {
                        stringResource(R.string.powerup_field_paste, row.envVar)
                    },
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSet) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.powerup_remove))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onGetKey) {
                Text(
                    stringResource(
                        if (isSet) R.string.powerup_manage else R.string.powerup_get_key,
                    ),
                )
            }
        }
    }
}
