package com.universalconverter.pro.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.BuildConfig
import com.universalconverter.pro.core.convert.QualityPreset
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.AccentControl
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.PureBlackControl
import com.universalconverter.pro.ui.components.ThemeModeControl
import com.universalconverter.pro.ui.components.UCPrimaryButton
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.components.UCWordmark
import com.universalconverter.pro.ui.formatBytes
import com.universalconverter.pro.ui.theme.UC
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val palette = UC.palette
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val storage by viewModel.storageBytes.collectAsStateWithLifecycle()
    val billing by viewModel.billingState.collectAsStateWithLifecycle()
    val adState by viewModel.adState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val adFreeUntil = settings.adFreeUntilEpochMs
    var confirmHistory by remember { mutableStateOf(false) }
    var confirmVault by remember { mutableStateOf(false) }
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UCTopBar(
                title = "Settings",
                subtitle = "Theme, storage, defaults and privacy",
                onBack = onBack,
                backIcon = Icons.Rounded.ArrowBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("APPEARANCE")
                HairlineCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingIcon(Icons.Rounded.Palette)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Theme", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                                Text("Follow the system or choose a fixed palette.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ThemeModeControl(
                            selected = settings.themeMode,
                            onSelect = viewModel::setTheme,
                        )
                        Spacer(Modifier.height(14.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))
                        Text("Accent colour", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                        Text("Tint buttons, badges and highlights.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        Spacer(Modifier.height(10.dp))
                        AccentControl(
                            selected = settings.accentTheme,
                            onSelect = viewModel::setAccentTheme,
                        )
                        Spacer(Modifier.height(10.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))
                        PureBlackControl(
                            enabled = settings.pureBlack,
                            onToggle = viewModel::setPureBlack,
                        )
                    }
                }

                SectionLabel("DEFAULTS")
                HairlineCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Default quality recipe", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                        Text("New drafts start with this recipe. You can change it per batch.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityPreset.entries.forEach { preset ->
                                ThemePill(preset.title, settings.defaultPreset == preset) { viewModel.setDefaultPreset(preset) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        SettingToggle(
                            title = "Copy results to Downloads",
                            subtitle = "A copy is written to Downloads/Universal Converter on Android 10+.",
                            checked = settings.autoSaveToDownloads,
                            onChange = viewModel::setAutoSave,
                        )
                    }
                }

                SectionLabel("WORKFLOW")
                HairlineCard {
                    Column {
                        SettingToggle(
                            title = "Open queue after converting",
                            subtitle = "Jump straight to live progress and results.",
                            checked = settings.autoOpenQueue,
                            onChange = viewModel::setAutoOpenQueue,
                            modifier = Modifier.padding(16.dp),
                        )
                        Divider()
                        SettingToggle(
                            title = "Keep screen awake",
                            subtitle = "Only while a batch is actively converting.",
                            checked = settings.keepScreenAwake,
                            onChange = viewModel::setKeepScreenAwake,
                            modifier = Modifier.padding(16.dp),
                        )
                        Divider()
                        SettingsLink(
                            Icons.Rounded.Notifications,
                            "Notifications",
                            if (notificationsGranted) "Enabled for conversion progress and results."
                            else "Off. Tap to allow progress notifications in system settings.",
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                runCatching { context.startActivity(intent) }
                            },
                        )
                    }
                }

                SectionLabel("STORAGE")
                HairlineCard(onClick = onOpenLibrary) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.Storage)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${library.size} items · ${formatBytes(storage)}", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                            Text("Everything converted is stored in the app's private vault.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = palette.muted)
                    }
                }
                HairlineCard {
                    Column {
                        SettingsLink(
                            Icons.Rounded.DeleteSweep,
                            "Clear conversion history",
                            "Remove the record but keep your converted files.",
                            onClick = { confirmHistory = true },
                        )
                        Divider()
                        SettingsLink(
                            Icons.Rounded.DeleteForever,
                            "Clear private vault",
                            "Delete every converted file stored inside the app.",
                            onClick = { confirmVault = true },
                        )
                    }
                }

                SectionLabel("PRIVACY")
                HairlineCard(background = palette.ink) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(palette.paper.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.CloudOff, null, tint = palette.ember, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(10.dp))
                            Text("Conversions stay on-device", style = MaterialTheme.typography.titleSmall, color = palette.paper)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Files are read and converted entirely on this phone and are never uploaded. Optional ads and the one-time Remove Ads purchase use Google services, so the app requests network access for those only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.paper.copy(alpha = 0.66f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrustPill(Icons.Rounded.Lock, "On-device")
                            TrustPill(Icons.Rounded.Fingerprint, "No accounts")
                            TrustPill(Icons.Rounded.Verified, "Verified output")
                        }
                    }
                }

                SectionLabel("ADS & SUPPORT")
                HairlineCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingIcon(Icons.Rounded.Campaign)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (settings.removeAdsPurchased) "Ads removed" else "Remove ads",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.ink,
                                )
                                Text(
                                    if (settings.removeAdsPurchased) "Thank you for supporting the studio."
                                    else "One-time purchase. Hides banners and interstitials.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.muted,
                                )
                            }
                            if (settings.removeAdsPurchased) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = palette.sage, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (!settings.removeAdsPurchased) {
                            Spacer(Modifier.height(12.dp))
                            UCPrimaryButton(
                                "Remove ads · ${billing.price}",
                                onClick = { activity?.let(viewModel::purchaseRemoveAds) },
                                enabled = billing.available && !billing.busy && activity != null,
                                loading = billing.busy,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.Campaign,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = viewModel::restorePurchases) {
                                    Text("Restore purchase", style = MaterialTheme.typography.labelMedium, color = palette.ember)
                                }
                                if (adState.privacyOptionsAvailable) {
                                    TextButton(onClick = {
                                        activity?.let { host ->
                                            viewModel.showAdPrivacyOptions(host) { ok ->
                                                if (!ok) viewModel.showMessage("Ad privacy options are unavailable right now.")
                                            }
                                        }
                                    }) {
                                        Text("Ad privacy", style = MaterialTheme.typography.labelMedium, color = palette.muted)
                                    }
                                }
                            }
                            if (adFreeUntil > System.currentTimeMillis()) {
                                Text(
                                    "Rewarded ad-free until ${formatAdFreeDate(adFreeUntil)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.sage,
                                )
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.PrivacyTip, null, tint = palette.muted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (adState.rewardedReady) "Or watch a short ad for 24 hours ad-free."
                                        else if (adState.canRequestAds) "Loading reward ad…"
                                        else "Reward ad unavailable right now.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.muted,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            activity?.let { host ->
                                                viewModel.showRewardedAd(host) { earned ->
                                                    if (earned) {
                                                        viewModel.grantAdFree(System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
                                                        viewModel.showMessage("24 hours ad-free unlocked.")
                                                    } else {
                                                        viewModel.showMessage("No reward earned. Try again shortly.")
                                                    }
                                                }
                                            }
                                        },
                                        enabled = adState.rewardedReady && activity != null,
                                    ) {
                                        Text("Watch", style = MaterialTheme.typography.labelMedium, color = palette.ember)
                                    }
                                }
                            }
                            billing.message?.let { message ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (billing.owned) palette.sage else palette.muted,
                                )
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No banners, no interstitials and no rewarded prompts. Purchases restore automatically through Google Play.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                            )
                            TextButton(onClick = viewModel::restorePurchases) {
                                Text("Restore purchase", style = MaterialTheme.typography.labelMedium, color = palette.ember)
                            }
                        }
                        Text(
                            "Ads and purchases are handled by Google Play. Conversion files are never uploaded.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.2.sp),
                            color = palette.muted.copy(alpha = 0.8f),
                        )
                    }
                }

                SectionLabel("REFERENCE")
                HairlineCard {
                    Column {
                        SettingsLink(Icons.Rounded.Speed, "Format guide", "Every read/write capability, honestly listed", onOpenCapabilities)
                        Divider()
                        SettingsLink(Icons.Rounded.AutoAwesome, "Device self-check", "Run local codec and conversion checks", onOpenDiagnostics)
                        Divider()
                        SettingsLink(Icons.Rounded.Description, "Open-source licenses", "Filee, AndroidX, PDFBox, fonts and more", onOpenLicenses)
                    }
                }

                SectionLabel("ABOUT")
                HairlineCard {
                    Column {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            UCWordmark(compact = true)
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                                Text("Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                            }
                        }
                        Divider()
                        SettingsLink(
                            Icons.Rounded.RestartAlt,
                            "Replay welcome tour",
                            "See the first-run introduction and theme picker again.",
                            onClick = viewModel::resetOnboarding,
                        )
                    }
                }
                Text(
                    "Format taxonomy and conversion ideas adapt Filee by KnifeLemon and contributors (MIT). Native Kotlin engine, no server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }

    if (confirmHistory) {
        ConfirmDialog(
            title = "Clear conversion history?",
            body = "The record will be removed. Converted files stay in your vault.",
            confirmLabel = "Clear history",
            onConfirm = {
                confirmHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmHistory = false },
        )
    }
    if (confirmVault) {
        ConfirmDialog(
            title = "Clear the private vault?",
            body = "Every converted file inside the app will be deleted. Copies already saved to Downloads are not touched.",
            confirmLabel = "Delete files",
            onConfirm = {
                confirmVault = false
                viewModel.clearVault()
            },
            onDismiss = { confirmVault = false },
        )
    }
}

private fun formatAdFreeDate(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = UC.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = palette.ink) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = palette.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = palette.muted)
            }
        },
        containerColor = palette.ivory,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontSize = 12.sp),
        color = UC.palette.muted,
    )
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    val palette = UC.palette
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(palette.parchment),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = palette.ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = UC.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = UC.palette
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) palette.paper else palette.muted,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.ink else palette.paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TrustPill(icon: ImageVector, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(UC.palette.paper.copy(alpha = 0.07f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = UC.palette.ember, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = UC.palette.paper.copy(alpha = 0.76f))
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = UC.palette
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = palette.muted)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(UC.palette.line.copy(alpha = 0.7f)))
}
