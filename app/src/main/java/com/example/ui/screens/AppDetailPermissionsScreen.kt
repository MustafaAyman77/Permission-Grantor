package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.InstalledAppItem
import com.example.model.PermissionFilterTab
import com.example.model.PermissionStatusItem
import com.example.model.ShizukuConnectionState
import com.example.ui.components.AppIconImage
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.PermissionStatusBadge
import com.example.ui.components.ShizukuAdbLevelNoticeCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.OnCrimsonErrorContainer
import com.example.ui.theme.OnEmeraldSuccessContainer
import kotlinx.coroutines.delay

@Composable
fun AppDetailPermissionsScreen(
    app: InstalledAppItem,
    permissions: List<PermissionStatusItem>,
    filterTab: PermissionFilterTab,
    shizukuState: ShizukuConnectionState,
    isLoadingPermissions: Boolean,
    isBatchGranting: Boolean,
    bannerMessage: String?,
    isBannerError: Boolean,
    onBack: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onFilterTabChange: (PermissionFilterTab) -> Unit,
    onGrantSinglePermission: (String) -> Unit,
    onGrantAllRemaining: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val ungrantedCount = permissions.count { !it.isGranted }
    val grantedCount = permissions.count { it.isGranted }

    val displayedPermissions = remember(permissions, filterTab) {
        when (filterTab) {
            PermissionFilterTab.UNGRANTED -> permissions.filter { !it.isGranted }
            PermissionFilterTab.ALL -> permissions
            PermissionFilterTab.GRANTED -> permissions.filter { it.isGranted }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_permissions_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Navigation & Connection Status Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_to_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة لقائمة التطبيقات"
                        )
                    }
                    Text(
                        text = "صلاحيات التطبيق الهدف",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConnectionStatusBadge(isConnected = shizukuState.isServiceConnected)
                    IconButton(
                        onClick = onRefreshPermissions,
                        modifier = Modifier.testTag("refresh_app_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "تحديث حالة الصلاحيات"
                        )
                    }
                }
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

        // Selected App Summary Header Card
        item {
            TargetAppHeaderCard(
                app = app,
                totalPermissions = permissions.size,
                grantedCount = grantedCount,
                ungrantedCount = ungrantedCount,
                shizukuState = shizukuState,
                isBatchGranting = isBatchGranting,
                onGrantAllRemaining = onGrantAllRemaining,
                onRequestShizukuPermission = onRequestShizukuPermission
            )
        }

        // Filter Tabs (Ungranted / All / Granted)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionFilterTab.entries.forEach { tab ->
                    val count = when (tab) {
                        PermissionFilterTab.UNGRANTED -> ungrantedCount
                        PermissionFilterTab.ALL -> permissions.size
                        PermissionFilterTab.GRANTED -> grantedCount
                    }
                    val selected = filterTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterTabChange(tab) },
                        label = {
                            Text(
                                text = "${tab.arabicLabel} ($count)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("perm_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        // Permissions Cards
        if (isLoadingPermissions) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            text = "جاري فحص حالة الصلاحيات عبر IPackageManager...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (displayedPermissions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (filterTab == PermissionFilterTab.UNGRANTED && permissions.isNotEmpty()) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Security
                            },
                            contentDescription = "لا توجد صلاحيات في هذا القسم",
                            tint = if (filterTab == PermissionFilterTab.UNGRANTED && permissions.isNotEmpty()) {
                                EmeraldSuccess
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = when {
                                permissions.isEmpty() ->
                                    "هذا التطبيق لا يطلب أي صلاحيات وقت تشغيل حساسة"
                                filterTab == PermissionFilterTab.UNGRANTED ->
                                    "رائع! جميع الصلاحيات الحساسة لهذا التطبيق ممنوحة بالكامل"
                                else ->
                                    "لا توجد صلاحيات ممنوحة بعد لهذا التطبيق"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (filterTab == PermissionFilterTab.UNGRANTED && permissions.isNotEmpty()) {
                            Button(
                                onClick = { onFilterTabChange(PermissionFilterTab.ALL) },
                                modifier = Modifier.testTag("show_all_permissions_button")
                            ) {
                                Text("عرض جميع الصلاحيات الممنوحة (${permissions.size})")
                            }
                        }
                    }
                }
            }
        } else {
            items(
                items = displayedPermissions,
                key = { it.permissionName }
            ) { item ->
                PermissionItemCard(
                    item = item,
                    onGrantClick = { onGrantSinglePermission(item.permissionName) }
                )
            }
        }

        // Technical Note at bottom of detail screen
        item {
            ShizukuAdbLevelNoticeCard()
        }
    }
}

@Composable
private fun TargetAppHeaderCard(
    app: InstalledAppItem,
    totalPermissions: Int,
    grantedCount: Int,
    ungrantedCount: Int,
    shizukuState: ShizukuConnectionState,
    isBatchGranting: Boolean,
    onGrantAllRemaining: () -> Unit,
    onRequestShizukuPermission: () -> Unit
) {
    val progress = if (totalPermissions > 0) {
        grantedCount.toFloat() / totalPermissions.toFloat()
    } else {
        1f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("target_app_header_card"),
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
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIconImage(
                    drawable = app.appIcon,
                    contentDescription = app.appName,
                    size = 60.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "الإصدار ${app.versionName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
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
                                style = MaterialTheme.typography.bodySmall,
                                color = if (app.isSideloaded) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (totalPermissions > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تقدم منح الصلاحيات الحساسة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$grantedCount من $totalPermissions ممنوحة",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ungrantedCount == 0) EmeraldSuccess else AmberWarning
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (ungrantedCount == 0) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            if (shizukuState.isServiceConnected && !shizukuState.isPermissionGranted) {
                Button(
                    onClick = onRequestShizukuPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_request_shizuku_permission_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = "طلب إذن Shizuku",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("منح تطبيق Permission Grantor إذن Shizuku أولاً")
                }
            }

            if (ungrantedCount > 0) {
                Button(
                    onClick = onGrantAllRemaining,
                    enabled = !isBatchGranting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_all_permissions_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isBatchGranting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = "منح الكل",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.btn_grant_all_via_shizuku)} ($ungrantedCount)"
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    item: PermissionStatusItem,
    onGrantClick: () -> Unit
) {
    var celebrateGlow by remember { mutableStateOf(false) }

    LaunchedEffect(item.justGrantedCount) {
        if (item.justGrantedCount > 0) {
            celebrateGlow = true
            delay(1400)
            celebrateGlow = false
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (celebrateGlow) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardCelebrateScale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            celebrateGlow -> EmeraldSuccess
            item.isGranted -> EmeraldSuccess.copy(alpha = 0.4f)
            item.errorMessage != null -> CrimsonError.copy(alpha = 0.7f)
            else -> AmberWarning.copy(alpha = 0.35f)
        },
        label = "permCardBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .border(
                width = if (celebrateGlow) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .testTag("permission_card_${item.shortName}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isGranted) {
                                    EmeraldSuccessContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.arabicTitle,
                            tint = if (item.isGranted) {
                                EmeraldSuccess
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.arabicTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.permissionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                PermissionStatusBadge(isGranted = item.isGranted)
            }

            Text(
                text = item.arabicDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Protected hint if applicable
            item.isProtectedNote?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberWarning
                )
            }

            // Celebratory animated banner on instant grant
            AnimatedVisibility(
                visible = celebrateGlow,
                enter = fadeIn() + scaleIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
                Surface(
                    color = EmeraldSuccessContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "نجاح المنح",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "تم منح الصلاحية فوراً عبر IPackageManager دون فتح إعدادات النظام!",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnEmeraldSuccessContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Error explanation if SecurityException or RemoteException occurred
            item.errorMessage?.let { err ->
                Surface(
                    color = CrimsonErrorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "تفاصيل الخطأ",
                            tint = CrimsonError,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnCrimsonErrorContainer
                        )
                    }
                }
            }

            // 4. Instant Grant Button ("منح الصلاحية عبر Shizuku") for every ungranted permission
            if (!item.isGranted) {
                Button(
                    onClick = onGrantClick,
                    enabled = !item.isGranting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_button_${item.shortName}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (item.isGranting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "منح فوري",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_grant_via_shizuku),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
