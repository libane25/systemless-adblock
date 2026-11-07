package se.liban.adblock.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.liban.adblock.data.AppSettings
import se.liban.adblock.data.BlocklistCatalog
import se.liban.adblock.data.UpdateFrequency
import se.liban.adblock.ui.theme.ThemeMode
import se.liban.adblock.ui.theme.adBlockTopBarColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    settings: AppSettings,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSettings: (AppSettings) -> Unit,
    onOpenLicenses: () -> Unit
) {
    val blocklist = BlocklistCatalog.require(settings.blocklistId)
    val version = rememberVersionName()
    val clock = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }
    val lastUpdated = state.lastUpdatedEpochMs?.let { clock.format(Instant.ofEpochMilli(it)) } ?: "Never"
    SettingsScaffold(title = "Settings", onBack = onBack) {
        SettingsCard(title = "Appearance") {
            Text(
                text = "Choose how Systemless AdBlock looks. System default follows this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ThemeChoice(
                label = "Light",
                subtitle = "White background and dark text",
                selected = settings.themeMode == ThemeMode.LIGHT,
                onSelect = { onTheme(ThemeMode.LIGHT) }
            )
            ThemeChoice(
                label = "Dark",
                subtitle = "Dark background and light text",
                selected = settings.themeMode == ThemeMode.DARK,
                onSelect = { onTheme(ThemeMode.DARK) }
            )
            ThemeChoice(
                label = "System default",
                subtitle = "Match the Android system appearance",
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onSelect = { onTheme(ThemeMode.SYSTEM) }
            )
        }
        SettingsCard(title = "Blocklist provider") {
            Text(
                text = "One list is active and downloads the next time you update protection. VPN mode also blocks subdomains of each name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var provider = ""
            BlocklistCatalog.all.forEach { list ->
                if (list.provider != provider) {
                    SectionLabel(list.provider)
                    provider = list.provider
                }
                ThemeChoice(
                    label = list.name,
                    subtitle = list.detail,
                    selected = settings.blocklistId == list.id,
                    onSelect = { onSettings(settings.copy(blocklistId = list.id)) }
                )
            }
        }
        SettingsCard(title = "Blocklist settings") {
            SettingSwitch(
                title = "Automatic updates",
                subtitle = "Refresh the blocklist on a schedule when you open the app.",
                checked = settings.automaticUpdates,
                onCheckedChange = { onSettings(settings.copy(automaticUpdates = it)) }
            )
            Text(
                text = "Update frequency",
                style = MaterialTheme.typography.titleSmall,
                color = if (settings.automaticUpdates) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            UpdateFrequency.entries.forEach { frequency ->
                ThemeChoice(
                    label = frequency.label,
                    subtitle = null,
                    selected = settings.updateFrequency == frequency,
                    enabled = settings.automaticUpdates,
                    onSelect = { onSettings(settings.copy(updateFrequency = frequency)) }
                )
            }
            SettingSwitch(
                title = "Update over Wi-Fi only",
                subtitle = "Download the blocklist only on Wi-Fi or Ethernet.",
                checked = settings.wifiOnly,
                onCheckedChange = { onSettings(settings.copy(wifiOnly = it)) }
            )
            SettingSwitch(
                title = "Background updates",
                subtitle = "Also refresh the blocklist while the app is closed. Requires automatic updates.",
                checked = settings.backgroundUpdates,
                enabled = settings.automaticUpdates,
                onCheckedChange = { onSettings(settings.copy(backgroundUpdates = it)) }
            )
        }
        SettingsCard(title = "Protection settings") {
            SectionLabel("VPN mode")
            SettingSwitch(
                title = "Unmetered VPN",
                subtitle = "The local VPN does not count as a metered connection.",
                checked = settings.vpnUnmetered,
                onCheckedChange = { onSettings(settings.copy(vpnUnmetered = it)) }
            )
            SettingSwitch(
                title = "Restart VPN after updates",
                subtitle = "Reload a running VPN after the blocklist is updated.",
                checked = settings.restartVpnAfterUpdate,
                onCheckedChange = { onSettings(settings.copy(restartVpnAfterUpdate = it)) }
            )
            SectionLabel("Hosts mode")
            SettingSwitch(
                title = "Reboot reminder",
                subtitle = "Say when a reboot is needed before the hosts file goes live.",
                checked = settings.hostsRebootReminder,
                onCheckedChange = { onSettings(settings.copy(hostsRebootReminder = it)) }
            )
            SectionLabel("Protection behavior")
            SettingSwitch(
                title = "Resume VPN when the app opens",
                subtitle = "Start VPN protection again if it was on the last time you used the app.",
                checked = settings.resumeVpnOnLaunch,
                onCheckedChange = { onSettings(settings.copy(resumeVpnOnLaunch = it)) }
            )
        }
        SettingsCard(title = "About") {
            AboutRow(label = "App version", value = version)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AboutRow(
                label = "Blocklist information",
                value = "${blocklist.label}\n${blocklist.detail}\n${state.blockedDomains} domains\nLast updated: $lastUpdated"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AboutRow(
                label = "Open-source licenses",
                value = "Kotlin, AndroidX, OkHttp, and libsu. Blocklists stay with their publishers.",
                onClick = onOpenLicenses
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AboutRow(
                label = "Project information",
                value = "Systemless AdBlock blocks ads and trackers on this phone. " +
                    "VPN mode filters DNS locally. Hosts mode applies a systemless hosts file. " +
                    "Browsing is not sent to a remote VPN server."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Open-source licenses", onBack = onBack) {
        SettingsCard(title = "Libraries") {
            licenses.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = entry.license,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit
) {
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val rowColor = if (selected && enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected && enabled) titleColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
}

private data class LicenseEntry(val name: String, val license: String, val note: String)

private val licenses = listOf(
    LicenseEntry("Kotlin", "Apache License 2.0", "Language and standard library"),
    LicenseEntry("Kotlin coroutines", "Apache License 2.0", "Background work inside the app"),
    LicenseEntry("Jetpack Compose", "Apache License 2.0", "Screens, buttons, and cards"),
    LicenseEntry("AndroidX", "Apache License 2.0", "Activity, lifecycle, and app theme"),
    LicenseEntry("OkHttp", "Apache License 2.0", "Blocklist downloads"),
    LicenseEntry("libsu", "Apache License 2.0", "Root access for hosts mode"),
    LicenseEntry(
        "Blocklist feeds",
        "Publisher licenses",
        "HaGeZi, StevenBlack, OISD, AdGuard, 1Hosts, URLhaus, Phishing Army, and Dandelion Sprout"
    )
)
