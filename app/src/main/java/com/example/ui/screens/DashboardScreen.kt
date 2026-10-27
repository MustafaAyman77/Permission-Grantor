package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppFilterMode
import com.example.model.GrantOperationLog
import com.example.model.InstalledAppItem
import com.example.model.ShizukuConnectionState
import com.example.shizuku.ApkInstallReport
import com.example.ui.components.AppIconImage
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.ShizukuAdbLevelNoticeCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.OnAmberWarningContainer
import com.example.ui.theme.OnEmeraldSuccessContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    shizukuState: ShizukuConnectionState,
    allApps: List<InstalledAppItem>,
    isLoadingApps: Boolean,
    searchQuery: String,
    appFilterMode: AppFilterMode,
    latestInstallReport: ApkInstallReport?,
    bannerMessage: String?,
    isBannerError: Boolean,
    operationLogs: List<GrantOperationLog>,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (AppFilterMode) -> Unit,
    onRecheckShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenApkInstaller: () -> Unit,
    onApkUriPicked: (Uri) -> Unit,
    onOpenSettingsGuide: () -> Unit,
    onSelectApp: (InstalledAppItem) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val directApkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onApkUriPicked(uri)
        }
    }

    val filteredApps = remember(allApps, searchQuery, appFilterMode) {
        allApps.filter { app ->
            val matchesMode = when (appFilterMode) {
                AppFilterMode.SIDELOADED_AND_USER -> !app.isSystemApp
                AppFilterMode.NEEDS_PERMISSIONS -> app.sensitivePermissionsUngranted > 0
                AppFilterMode.ALL_APPS -> true
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                app.appName.contains(q, ignoreCase = true) ||
                app.packageName.contains(q, ignoreCase = true)
            matchesMode && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feedback Banner if present
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

        // 1. Welcome & Shizuku Verification Hero Card
        item {
            ShizukuWelcomeVerificationCard(
                shizukuState = shizukuState,
                onRecheckShizuku = onRecheckShizuku,
                onRequestShizukuPermission = onRequestShizukuPermission,
                onOpenSettingsGuide = onOpenSettingsGuide
            )
        }

        // 2. Featured APK Spoofed Installer & Auto-Grant Card ("زر تثبيت APK")
        item {
            SpoofedInstallerQuickCard(
                onPickApkClick = {
                    if (shizukuState.isServiceConnected && !shizukuState.isPermissionGranted) {
                        onRequestShizukuPermission()
                    }
                    directApkPickerLauncher.launch(
                        arrayOf(
                            "application/vnd.android.package-archive",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onOpenInstallerScreen = onOpenApkInstaller
            )
        }

        // 3. Latest Installation Status Report Card if available
        if (latestInstallReport != null) {
            item {
                InstallationStatusReportCard(
                    report = latestInstallReport,
                    onOpenSystemAppSettings = { pkg ->
                        openAppDetailsSettings(context, pkg)
                    }
                )
            }
        }

        // 4. Mandatory Technical Notice Card (ADB level vs Root & Protected Permissions)
        item {
            ShizukuAdbLevelNoticeCard()
        }

        // 5. Recent Activity Log (if any permissions were granted in this session)
        if (operationLogs.isNotEmpty()) {
            item {
                RecentGrantLogsCard(logs = operationLogs.take(4))
            }
        }

        // 6. Target Application Selection Header & Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = "قائمة التطبيقات",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "اختر التطبيق الهدف لمنحه الصلاحيات",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${filteredApps.size} تطبيق",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_search_input"),
                    placeholder = {
                        Text("ابحث باسم التطبيق أو الحزمة (Package Name)...")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "بحث"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "مسح البحث"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppFilterMode.entries.forEach { mode ->
                        val selected = appFilterMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterModeChange(mode) },
                            label = {
                                Text(
                                    text = mode.arabicLabel,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_chip_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 7. Installed Applications List
        if (isLoadingApps) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "جاري فحص التطبيقات المثبتة وصلاحياتها الحساسة...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (filteredApps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "لا توجد تطبيقات مطابقة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "لا توجد تطبيقات مطابقة لفلتر البحث الحالي",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "جرّب التبديل إلى فلتر \"جميع التطبيقات (مع النظام)\" أو مسح نص البحث.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                onSearchQueryChange("")
                                onFilterModeChange(AppFilterMode.ALL_APPS)
                            },
                            modifier = Modifier.testTag("reset_app_filters_button")
                        ) {
                            Text("عرض جميع التطبيقات المثبتة")
                        }
                    }
                }
            }
        } else {
            items(
                items = filteredApps,
                key = { it.packageName }
            ) { appItem ->
                InstalledAppCard(
                    app = appItem,
                    onClick = { onSelectApp(appItem) }
                )
            }
        }
    }
}

@Composable
private fun SpoofedInstallerQuickCard(
    onPickApkClick: () -> Unit,
    onOpenInstallerScreen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                MaterialTheme.shapes.large
            )
            .testTag("dashboard_apk_installer_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = "تثبيت بهوية المتجر",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "التثبيت بهوية المتجر + المنح التلقائي",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "تثبيت ملفات APK بهوية com.android.vending (أو F-Droid / Aurora) ومنح جميع صلاحياتها الحساسة تلقائياً عبر Shizuku",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickApkClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_install_apk_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.InstallMobile,
                        contentDescription = stringResource(R.string.btn_install_apk),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_install_apk))
                }

                OutlinedButton(
                    onClick = onOpenInstallerScreen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_installer_options_button")
                ) {
                    Text("خيارات المتجر والتقرير")
                }
            }
        }
    }
}

@Composable
private fun ShizukuWelcomeVerificationCard(
    shizukuState: ShizukuConnectionState,
    onRecheckShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenSettingsGuide: () -> Unit
) {
    val borderAccent = when {
        shizukuState.isReadyToGrant -> EmeraldSuccess.copy(alpha = 0.6f)
        shizukuState.isServiceConnected -> AmberWarning.copy(alpha = 0.6f)
        else -> CrimsonError.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderAccent, MaterialTheme.shapes.large)
            .testTag("shizuku_verification_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero Banner with Overlay Status Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_shizuku_hero),
                    contentDescription = "شعار اتصال خدمة Shizuku",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    ConnectionStatusBadge(isConnected = shizukuState.isServiceConnected)

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = shizukuState.connectionModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = shizukuState.statusHeadline,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = shizukuState.statusDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 4-point Diagnostic Checklist
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticRow(
                            label = "تثبيت تطبيق Shizuku",
                            valueText = if (shizukuState.isShizukuInstalled) {
                                "مثبت (${shizukuState.installedVersionName ?: "نشط"})"
                            } else {
                                "غير مثبت"
                            },
                            isPassed = shizukuState.isShizukuInstalled
                        )
                        DiagnosticRow(
                            label = "إصدار خدمة Shizuku (المطلوب 11+)",
                            valueText = if (shizukuState.serverApiVersion > 0) {
                                "الإصدار v${shizukuState.serverApiVersion}"
                            } else {
                                "بانتظار تشغيل الخدمة"
                            },
                            isPassed = shizukuState.isVersionAtLeast11
                        )
                        DiagnosticRow(
                            label = "حالة خدمة Shizuku الحالية",
                            valueText = if (shizukuState.isBinderAlive) "تعمل (متصل)" else "متوقفة (غير متصل)",
                            isPassed = shizukuState.isBinderAlive
                        )
                        DiagnosticRow(
                            label = "إذن استخدام Shizuku للتطبيق",
                            valueText = if (shizukuState.isPermissionGranted) "ممنوح ومفعّل" else "غير ممنوح بعد",
                            isPassed = shizukuState.isPermissionGranted
                        )
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (shizukuState.isServiceConnected && !shizukuState.isPermissionGranted) {
                        Button(
                            onClick = onRequestShizukuPermission,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("request_shizuku_permission_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "طلب الإذن",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طلب إذن استخدام Shizuku")
                        }
                    } else if (!shizukuState.isServiceConnected) {
                        Button(
                            onClick = onOpenSettingsGuide,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_guide_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HelpOutline,
                                contentDescription = "خطوات التفعيل",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خطوات تفعيل Shizuku")
                        }
                    }

                    OutlinedButton(
                        onClick = onRecheckShizuku,
                        modifier = Modifier
                            .weight(if (shizukuState.isReadyToGrant) 1f else 0.75f)
                            .testTag("recheck_shizuku_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "إعادة التحقق",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة فحص الاتصال")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    valueText: String,
    isPassed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isPassed) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = label,
                tint = if (isPassed) EmeraldSuccess else AmberWarning,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPassed) EmeraldSuccess else AmberWarning
        )
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledAppItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("app_item_${app.packageName}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                drawable = app.appIcon,
                contentDescription = app.appName,
                size = 52.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = if (app.isSideloaded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = app.installerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.isSideloaded) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.sensitivePermissionsUngranted > 0) {
                        Surface(
                            color = AmberWarningContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${app.sensitivePermissionsUngranted} صلاحية غير ممنوحة",
                                style = MaterialTheme.typography.labelLarge,
                                color = OnAmberWarningContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    } else if (app.sensitivePermissionsTotal > 0) {
                        Surface(
                            color = EmeraldSuccessContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "جميع الصلاحيات ممنوحة (${app.sensitivePermissionsTotal})",
                                style = MaterialTheme.typography.labelLarge,
                                color = OnEmeraldSuccessContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "لا يطلب صلاحيات حساسة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "عرض صلاحيات التطبيق",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun RecentGrantLogsCard(
    logs: List<GrantOperationLog>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_grant_logs_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "سجل العمليات",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "سجل عمليات المنح الأخيرة عبر Shizuku",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            logs.forEach { log ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (log.success) EmeraldSuccess else CrimsonError)
                        )
                        Text(
                            text = "${log.appName}: ${log.permissionShortName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = log.timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
