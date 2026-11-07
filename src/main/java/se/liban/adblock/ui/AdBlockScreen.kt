package se.liban.adblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.liban.adblock.ui.theme.SystemlessAdBlockTheme
import se.liban.adblock.ui.theme.ThemeMode
import se.liban.adblock.ui.theme.adBlockTopBarColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockScreen(
    state: MainUiState,
    onEnable: () -> Unit,
    onRevert: () -> Unit,
    onMode: (BlockMode) -> Unit,
    onOpenSettings: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val clock = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Systemless AdBlock") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = adBlockTopBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (state.mode == BlockMode.VPN) {
                    "VPN mode works like AdBlock. A local VPN filters DNS on this phone. Traffic is not sent to a VPN server."
                } else {
                    "Hosts mode blocks ads in every app with a systemless hosts file. No VPN."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ModePicker(
                mode = state.mode,
                enabled = !state.working,
                onSelect = onMode
            )
            StatusCard(state)
            StatsCard(
                blockedDomains = numberFormat.format(state.blockedDomains),
                lastUpdated = state.lastUpdatedEpochMs?.let { clock.format(Instant.ofEpochMilli(it)) }
                    ?: "Never",
                targetLabel = state.targetLabel,
                blocklistLabel = state.blocklistLabel
            )
            if (state.working) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = state.stage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onEnable,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable / Update AdBlock")
            }
            OutlinedButton(
                onClick = onRevert,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Revert to Default")
            }
            Text(
                text = if (state.mode == BlockMode.VPN) {
                    "Turn the VPN off with Revert to Default. Browsing stays on this phone."
                } else {
                    "Persistent hosts blocking needs Magisk’s Systemless hosts switch, or the systemless_adblock module."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusRow(
                label = "Root Status",
                value = if (state.rootGranted) "Granted" else "Denied",
                color = if (state.rootGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            StatusRow(
                label = "Current Status",
                value = if (state.protected) "Protected" else "Disabled",
                color = if (state.protected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun StatsCard(
    blockedDomains: String,
    lastUpdated: String,
    targetLabel: String,
    blocklistLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatRow("Blocklist", blocklistLabel)
            StatRow("Blocked domains", blockedDomains)
            StatRow("Last updated", lastUpdated)
            StatRow("Target", targetLabel)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ModePicker(
    mode: BlockMode,
    enabled: Boolean,
    onSelect: (BlockMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Blocking mode", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton(
                label = "Systemless hosts",
                selected = mode == BlockMode.HOSTS,
                enabled = enabled,
                onClick = { onSelect(BlockMode.HOSTS) },
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                label = "VPN like AdBlock",
                selected = mode == BlockMode.VPN,
                enabled = enabled,
                onClick = { onSelect(BlockMode.VPN) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdBlockScreenPreview() {
    SystemlessAdBlockTheme {
        AdBlockScreen(
            state = MainUiState(
                rootGranted = true,
                protected = true,
                blockedDomains = 72525,
                lastUpdatedEpochMs = 1_760_000_000_000L,
                targetLabel = "Magisk systemless hosts"
            ),
            onEnable = {},
            onRevert = {},
            onMode = {},
            onOpenSettings = {}
        )
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun AdBlockScreenDarkPreview() {
    SystemlessAdBlockTheme(ThemeMode.DARK) {
        AdBlockScreen(
            state = MainUiState(
                rootGranted = false,
                protected = false,
                blockedDomains = 0,
                targetLabel = "Not applied yet"
            ),
            onEnable = {},
            onRevert = {},
            onMode = {},
            onOpenSettings = {}
        )
    }
}
