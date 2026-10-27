package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.InstalledAppItem
import com.example.model.ShizukuConnectionState
import com.example.shizuku.ApkInstallReport
import com.example.shizuku.PostInstallPermissionResult
import com.example.shizuku.StagedApkInfo
import com.example.shizuku.StoreIdentityPreset
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.ShizukuAdbLevelNoticeCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.OnAmberWarningContainer
import com.example.ui.theme.OnCrimsonErrorContainer
import com.example.ui.theme.OnEmeraldSuccessContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkInstallerScreen(
    shizukuState: ShizukuConnectionState,
    selectedStorePreset: StoreIdentityPreset,
    customInstallerPackage: String,
    autoGrantOnInstall: Boolean,
    stagedApkInfo: StagedApkInfo?,
    isInstallingApk: Boolean,
    installReport: ApkInstallReport?,
    installedApps: List<InstalledAppItem>,
    bannerMessage: String?,
    isBannerError: Boolean,
    onSelectStorePreset: (StoreIdentityPreset) -> Unit,
    onCustomInstallerChange: (String) -> Unit,
    onToggleAutoGrant: (Boolean) -> Unit,
    onApkUriPicked: (Uri) -> Unit,
    onExecuteStagedApkInstall: () -> Unit,
    onSpoofAndGrantExistingApp: (InstalledAppItem) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    // System file picker for selecting an APK file
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onApkUriPicked(uri)
        }
    }

    val effectiveInstallerPackage = if (selectedStorePreset == StoreIdentityPreset.CUSTOM) {
        customInstallerPackage.trim().ifEmpty { "com.android.vending" }
    } else {
        selectedStorePreset.installerPackage
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("apk_installer_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تثبيت APK بهوية المتجر + المنح التلقائي",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "تزوير installerPackageName ومنح الصلاحيات الحساسة عبر Shizuku",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConnectionStatusBadge(isConnected = shizukuState.isServiceConnected)
            }
        }

        // Feedback Banner
        item {
            AnimatedVisibility(visible = bannerMessage != null) {
                bannerMessage?.let { msg ->
                    StatusFeedbackBanner(
                        message = msg,
                        isError = isBannerError,
                        onDismiss = onDismissBanner
                    )
                }
            }
        }

        // 1. Store Identity Selector Card (Spoofed Installer Configuration)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_identity_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = "هوية المتجر",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "1. تحديد هوية المتجر (Spoofed Installer)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "يُضبط في SessionParams.setInstallerPackageName و PACKAGE_SOURCE_STORE",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StoreIdentityPreset.entries.forEach { preset ->
                            val selected = selectedStorePreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectStorePreset(preset) },
                                label = {
                                    Text(
                                        text = preset.arabicName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("store_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    if (selectedStorePreset == StoreIdentityPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customInstallerPackage,
                            onValueChange = onCustomInstallerChange,
                            label = { Text("اسم حزمة المتجر المخصص (Installer Package Name)") },
                            placeholder = { Text("مثال: com.android.vending") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_installer_input")
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الحزمة المُستخدمة كـ Installer:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = effectiveInstallerPackage,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleAutoGrant(!autoGrantOnInstall) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoGrantOnInstall,
                            onCheckedChange = onToggleAutoGrant,
                            modifier = Modifier.testTag("auto_grant_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "المنح التلقائي للصلاحيات الخطيرة بعد التثبيت",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "قراءة AndroidManifest.xml واستدعاء grantRuntimePermission تلقائياً لكل صلاحية غير ممنوحة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. Primary APK Picker & Install Action Card ("زر تثبيت APK")
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.shapes.large
                    )
                    .testTag("apk_picker_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.InstallMobile,
                                contentDescription = "تثبيت APK",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "2. اختيار وتثبيت ملف APK عبر Shizuku",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "يتحقق تلقائياً من إذن Shizuku قبل بدء الجلسة والمنح",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Prominent "تثبيت APK" Button
                    Button(
                        onClick = {
                            if (shizukuState.isServiceConnected && !shizukuState.isPermissionGranted) {
                                onRequestShizukuPermission()
                            }
                            apkPickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        enabled = !isInstallingApk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pick_apk_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = stringResource(R.string.btn_install_apk)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (stagedApkInfo == null) {
                                "${stringResource(R.string.btn_install_apk)} (اختر ملف .apk من الجهاز)"
                            } else {
                                "تغيير ملف APK المختار (${stagedApkInfo.appLabel})"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Staged APK Preview & Execute Install Button
                    stagedApkInfo?.let { staged ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("staged_apk_preview")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = staged.appLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = staged.packageName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "v${staged.versionName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "الصلاحيات الحساسة المطلوبة في AndroidManifest.xml: (${staged.dangerousPermissions.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (staged.dangerousPermissions.isNotEmpty()) {
                                    Text(
                                        text = staged.dangerousPermissions.joinToString(" • ") {
                                            it.substringAfterLast('.')
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberWarning
                                    )
                                }

                                Button(
                                    onClick = onExecuteStagedApkInstall,
                                    enabled = !isInstallingApk,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .testTag("confirm_install_apk_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldSuccess,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    if (isInstallingApk) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري التثبيت بهوية المتجر ومنح الصلاحيات...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "بدء التثبيت والمنح"
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("بدء التثبيت بهوية ($effectiveInstallerPackage) ومنح الصلاحيات")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Installation Status & Post-Install Report Card ("بطاقة حالة التثبيت")
        item {
            AnimatedVisibility(visible = installReport != null) {
                installReport?.let { report ->
                    InstallationStatusReportCard(
                        report = report,
                        onOpenSystemAppSettings = { pkg ->
                            openAppDetailsSettings(context, pkg)
                        }
                    )
                }
            }
        }

        // 4. Quick Spoof + Auto-Grant for Already Installed Apps
        if (installedApps.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("existing_apps_spoof_card"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = "تطبيق سريع",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "تطبيق فوري على حزمة مثبتة مسبقاً",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "تزوير هوية المتجر ($effectiveInstallerPackage) + منح كافة الصلاحيات الحساسة دفعة واحدة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val candidateApps = installedApps
                            .filter { !it.isSystemApp }
                            .take(5)
                            .ifEmpty { installedApps.take(4) }

                        candidateApps.forEach { app ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${app.packageName} • غير ممنوحة: ${app.sensitivePermissionsUngranted}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { onSpoofAndGrantExistingApp(app) },
                                        enabled = !isInstallingApk,
                                        modifier = Modifier.testTag("spoof_existing_${app.packageName}")
                                    ) {
                                        Text("تزوير المتجر + منح")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Mandatory Technical Limitation Note
        item {
            ShizukuAdbLevelNoticeCard()
        }
    }
}

/**
 * Displays the required "بطاقة حالة التثبيت" (Installation Status Card):
 * (أ) هل تم تزوير المثبت بنجاح؟
 * (ب) ما الصلاحيات التي تم منحها فعلياً؟
 * (ج) ما الصلاحيات التي فشل منحها (مع زر "منح يدوي" يفتح إعدادات النظام كخطة بديلة).
 */
@Composable
fun InstallationStatusReportCard(
    report: ApkInstallReport,
    onOpenSystemAppSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderAccent = when {
        report.isApkInstalledSuccess && report.failedPermissions.isEmpty() -> EmeraldSuccess
        report.isApkInstalledSuccess -> AmberWarning
        else -> CrimsonError
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderAccent.copy(alpha = 0.7f), MaterialTheme.shapes.large)
            .testTag("installation_status_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (report.isApkInstalledSuccess) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.ErrorOutline
                        },
                        contentDescription = "حالة التثبيت",
                        tint = if (report.isApkInstalledSuccess) EmeraldSuccess else CrimsonError,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "بطاقة حالة التثبيت والمنح التلقائي",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${report.appName} (${report.packageName})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Section (أ): هل تم تزوير المثبت بنجاح؟
            Surface(
                color = if (report.isInstallerSpoofedSuccess) {
                    EmeraldSuccessContainer.copy(alpha = 0.65f)
                } else {
                    AmberWarningContainer.copy(alpha = 0.65f)
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("installer_spoof_status_section")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "(أ) حالة تزوير هوية المتجر (Spoofed Installer)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (report.isInstallerSpoofedSuccess) {
                                OnEmeraldSuccessContainer
                            } else {
                                OnAmberWarningContainer
                            }
                        )
                        Surface(
                            color = if (report.isInstallerSpoofedSuccess) EmeraldSuccess else AmberWarning,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (report.isInstallerSpoofedSuccess) "نجح التزوير" else "قيود نظام جزئية",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "الهوية المطلوبة: ${report.requestedInstallerPackage}" +
                            (report.actualInstallerPackageAfter?.let { " • المسجلة: $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (report.isInstallerSpoofedSuccess) {
                            OnEmeraldSuccessContainer
                        } else {
                            OnAmberWarningContainer
                        }
                    )

                    if (report.installMethodUsed.isNotBlank()) {
                        Text(
                            text = "الأسلوب المُنفّذ: ${report.installMethodUsed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (report.isInstallerSpoofedSuccess) {
                                OnEmeraldSuccessContainer
                            } else {
                                OnAmberWarningContainer
                            }
                        )
                    }

                    Text(
                        text = report.installerStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (report.isInstallerSpoofedSuccess) {
                            OnEmeraldSuccessContainer
                        } else {
                            OnAmberWarningContainer
                        }
                    )
                }
            }

            // Section (ب): ما الصلاحيات التي تم منحها فعلياً؟
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("granted_permissions_report_section")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "(ب) الصلاحيات التي تم منحها فعلياً عبر Shizuku",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = EmeraldSuccessContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${report.grantedPermissions.size} ممنوحة",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnEmeraldSuccessContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }

                if (report.grantedPermissions.isEmpty()) {
                    Text(
                        text = "لم يتم منح أي صلاحيات جديدة (إما أن التطبيق لا يطلب صلاحيات وقت تشغيل خطيرة أو لم تكتمل عملية المنح).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    report.grantedPermissions.forEach { perm ->
                        Surface(
                            color = EmeraldSuccessContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "ممنوحة",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = perm.arabicTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = perm.permissionName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = if (perm.wasAlreadyGranted) "كانت ممنوحة" else "تم المنح فوراً",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldSuccess
                                )
                            }
                        }
                    }
                }
            }

            // Section (ج): ما الصلاحيات التي فشل منحها (مع زر "منح يدوي" يفتح إعدادات النظام كخطة بديلة)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("failed_permissions_report_section")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "(ج) الصلاحيات التي تعذر منحها تلقائياً",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = if (report.failedPermissions.isEmpty()) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            CrimsonErrorContainer
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${report.failedPermissions.size} فشل",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (report.failedPermissions.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                OnCrimsonErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }

                if (report.failedPermissions.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "لا يوجد فشل",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "لم يفشل منح أي صلاحية من صلاحيات وقت التشغيل المطلوبة!",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldSuccess
                        )
                    }
                } else {
                    report.failedPermissions.forEach { failedPerm ->
                        FailedPermissionItemRow(
                            failedPerm = failedPerm,
                            onManualGrantClick = {
                                onOpenSystemAppSettings(report.packageName)
                            }
                        )
                    }
                }

                // Always offer a direct fallback button to open the app's system settings page
                OutlinedButton(
                    onClick = { onOpenSystemAppSettings(report.packageName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_app_system_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.btn_manual_grant),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${stringResource(R.string.btn_manual_grant)} (فتح إعدادات التطبيق في النظام)")
                }
            }
        }
    }
}

@Composable
private fun FailedPermissionItemRow(
    failedPerm: PostInstallPermissionResult,
    onManualGrantClick: () -> Unit
) {
    Surface(
        color = CrimsonErrorContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("failed_perm_${failedPerm.shortName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = "فشل المنح",
                        tint = CrimsonError,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = failedPerm.arabicTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnCrimsonErrorContainer
                        )
                        Text(
                            text = failedPerm.permissionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnCrimsonErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onManualGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberWarning,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier.testTag("manual_grant_btn_${failedPerm.shortName}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.btn_manual_grant),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_manual_grant))
                }
            }

            failedPerm.failureReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnCrimsonErrorContainer
                )
            }
        }
    }
}

fun openAppDetailsSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            val fallback = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Throwable) {
        }
    }
}
