package com.getup.iClouDroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getup.iClouDroid.data.settings.AppSettings
import com.getup.iClouDroid.data.settings.ScanFrequency
import com.getup.iClouDroid.data.settings.ScanCadence

/**
 * Settings, reachable from the account sheet.
 *
 * ## Adding a setting
 *
 * 1. Add an entry to [SettingsItem].
 * 2. Add it to a group in [SettingsCatalog.groups].
 * 3. Add one branch in [SettingsRow] and, if it needs state, one property in the ViewModel.
 *
 * Nothing else needs to change: the screen builds itself from the catalog, so groups, dividers and
 * ordering are automatic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    settings: AppSettings,
    fullScanMessage: String?,
    isFullScanRunning: Boolean,
    onStartFullScan: () -> Unit,
    onFrequencyChanged: (ScanFrequency) -> Unit,
    onCustomIntervalChanged: (Int) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
    modifier: Modifier = Modifier
) {
    val frequency by settings.scanFrequency.collectAsStateWithLifecycle()
    val customDays by settings.customIntervalDays.collectAsStateWithLifecycle()

    var showScanWarning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsCatalog.groups.forEach { group ->
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                group.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    group.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        SettingsRow(
                            item = item,
                            frequency = frequency,
                            customDays = customDays,
                            isFullScanRunning = isFullScanRunning,
                            onRequestFullScan = { showScanWarning = true },
                            onFrequencyChanged = onFrequencyChanged,
                            onCustomIntervalChanged = onCustomIntervalChanged
                        )
                    }
                }
            }

            if (frequency != ScanFrequency.OFF) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "下次自动扫描将在凌晨 " +
                        "${ScanCadence.PREFERRED_HOUR}:00 左右开始（系统可能因省电策略略有延迟）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            fullScanMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showScanWarning) {
        FullScanWarningDialog(
            onDismiss = { showScanWarning = false },
            onConfirm = {
                showScanWarning = false
                onStartFullScan()
            }
        )
    }
}

/**
 * Explains what a full scan is about to do, and why it must not be interrupted.
 *
 * The warning is not ceremony: the walk runs hundreds of sequential CloudKit requests, and killing
 * the app mid-way does not lose data — but it does mean the reconciliation is skipped for that
 * round, so photos deleted elsewhere keep showing up until the next complete run.
 */
@Composable
private fun FullScanWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始全盘扫描？") },
        text = {
            Column {
                Text("全盘扫描会重新遍历整个 iCloud 照片库，可能需要几分钟。")
                Spacer(Modifier.height(8.dp))
                Text(
                    "扫描过程中请不要强行关闭 App：中断后本次扫描不会生效，" +
                        "已删除的照片暂时仍会显示，需要等下一次完整扫描才会被清理。",
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "扫到一半退出不会损坏任何数据，下次会从断点继续。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("开始扫描") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SettingsRow(
    item: SettingsItem,
    frequency: ScanFrequency,
    customDays: Int,
    isFullScanRunning: Boolean,
    onRequestFullScan: () -> Unit,
    onFrequencyChanged: (ScanFrequency) -> Unit,
    onCustomIntervalChanged: (Int) -> Unit
) {
    when (item) {
        SettingsItem.FULL_SCAN -> ActionRow(
            title = "全盘扫描照片库",
            subtitle = if (isFullScanRunning) "正在扫描…" else "清理在其它设备上已删除的照片",
            enabled = !isFullScanRunning,
            trailing = {
                if (isFullScanRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            },
            onClick = onRequestFullScan
        )

        SettingsItem.SCAN_FREQUENCY -> DropdownRow(
            title = "自动扫描频率",
            subtitle = "关闭时常驻后台的自动扫描不会运行",
            current = frequency.label,
            options = ScanFrequency.entries.map { it to it.label },
            onSelect = onFrequencyChanged
        )

        SettingsItem.CUSTOM_INTERVAL -> DropdownRow(
            title = "自定义周期",
            subtitle = if (frequency == ScanFrequency.CUSTOM) {
                "每 $customDays 天执行一次"
            } else {
                "仅在频率选择「自定义」时生效"
            },
            enabled = frequency == ScanFrequency.CUSTOM,
            current = "$customDays 天",
            options = CUSTOM_DAY_OPTIONS.map { it to "$it 天" },
            onSelect = { onCustomIntervalChanged(it) }
        )
    }
}

/** Offered intervals. Free-form entry adds a text field for two days a year of use cases. */
private val CUSTOM_DAY_OPTIONS = listOf(1, 2, 3, 7, 14, 30, 60, 90, 180)

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun <T> DropdownRow(
    title: String,
    subtitle: String,
    current: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { expanded = true } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
        Spacer(Modifier.width(12.dp))
        Text(
            text = current,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}
