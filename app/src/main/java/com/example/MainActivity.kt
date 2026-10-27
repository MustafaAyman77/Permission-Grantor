package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.screens.AppDetailPermissionsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsGuideScreen
import com.example.ui.theme.PermissionGrantorTheme
import com.example.viewmodel.MainNavDestination
import com.example.viewmodel.PermissionGrantorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PermissionGrantorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PermissionGrantorTheme(
                darkTheme = uiState.darkTheme,
                rtlLayout = true
            ) {
                PermissionGrantorApp(
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically re-check Shizuku connection and permission status when returning to the app
        viewModel.refreshShizukuStatus()
        viewModel.uiState.value.selectedApp?.let {
            viewModel.loadPermissionsForApp(it, silent = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGrantorApp(
    viewModel: PermissionGrantorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    ConnectionStatusBadge(
                        isConnected = uiState.shizukuState.isServiceConnected,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            viewModel.refreshShizukuStatus()
                            viewModel.loadInstalledApps()
                        },
                        modifier = Modifier.testTag("top_bar_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "تحديث الاتصال والتطبيقات"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val isHome = uiState.currentDestination == MainNavDestination.DASHBOARD
                NavigationBarItem(
                    selected = isHome,
                    onClick = { viewModel.navigateTo(MainNavDestination.DASHBOARD) },
                    icon = {
                        Icon(
                            imageVector = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_home)) },
                    modifier = Modifier.testTag("nav_tab_home")
                )

                val isAppsOrDetail = uiState.currentDestination == MainNavDestination.APPS_LIST ||
                    uiState.currentDestination == MainNavDestination.APP_PERMISSIONS_DETAIL
                NavigationBarItem(
                    selected = isAppsOrDetail,
                    onClick = {
                        val selectedApp = uiState.selectedApp
                        if (selectedApp != null) {
                            viewModel.navigateTo(MainNavDestination.APP_PERMISSIONS_DETAIL)
                        } else {
                            viewModel.navigateTo(MainNavDestination.DASHBOARD)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isAppsOrDetail) Icons.Filled.Apps else Icons.Outlined.Apps,
                            contentDescription = stringResource(R.string.nav_apps)
                        )
                    },
                    label = {
                        Text(
                            text = uiState.selectedApp?.appName?.take(12)
                                ?: stringResource(R.string.nav_apps)
                        )
                    },
                    modifier = Modifier.testTag("nav_tab_apps")
                )

                val isSettings = uiState.currentDestination == MainNavDestination.SETTINGS_GUIDE
                NavigationBarItem(
                    selected = isSettings,
                    onClick = { viewModel.navigateTo(MainNavDestination.SETTINGS_GUIDE) },
                    icon = {
                        Icon(
                            imageVector = if (isSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentDestination) {
                MainNavDestination.DASHBOARD,
                MainNavDestination.APPS_LIST -> {
                    DashboardScreen(
                        shizukuState = uiState.shizukuState,
                        allApps = uiState.allApps,
                        isLoadingApps = uiState.isLoadingApps,
                        searchQuery = uiState.searchQuery,
                        appFilterMode = uiState.appFilterMode,
                        bannerMessage = uiState.bannerMessage,
                        isBannerError = uiState.isBannerError,
                        operationLogs = uiState.operationLogs,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onFilterModeChange = viewModel::setAppFilterMode,
                        onRecheckShizuku = viewModel::refreshShizukuStatus,
                        onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                        onOpenSettingsGuide = {
                            viewModel.navigateTo(MainNavDestination.SETTINGS_GUIDE)
                        },
                        onSelectApp = viewModel::selectApp,
                        onDismissBanner = viewModel::dismissBanner
                    )
                }

                MainNavDestination.APP_PERMISSIONS_DETAIL -> {
                    val selectedApp = uiState.selectedApp
                    if (selectedApp != null) {
                        AppDetailPermissionsScreen(
                            app = selectedApp,
                            permissions = uiState.selectedAppPermissions,
                            filterTab = uiState.permissionFilterTab,
                            shizukuState = uiState.shizukuState,
                            isLoadingPermissions = uiState.isLoadingPermissions,
                            isBatchGranting = uiState.isBatchGranting,
                            bannerMessage = uiState.bannerMessage,
                            isBannerError = uiState.isBannerError,
                            onBack = { viewModel.navigateTo(MainNavDestination.DASHBOARD) },
                            onRefreshPermissions = {
                                viewModel.loadPermissionsForApp(selectedApp, silent = false)
                            },
                            onFilterTabChange = viewModel::setPermissionFilterTab,
                            onGrantSinglePermission = viewModel::grantSinglePermission,
                            onGrantAllRemaining = viewModel::grantAllRemainingPermissions,
                            onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                            onDismissBanner = viewModel::dismissBanner
                        )
                    } else {
                        viewModel.navigateTo(MainNavDestination.DASHBOARD)
                    }
                }

                MainNavDestination.SETTINGS_GUIDE -> {
                    SettingsGuideScreen(
                        shizukuState = uiState.shizukuState,
                        darkTheme = uiState.darkTheme,
                        onToggleDarkTheme = viewModel::toggleDarkTheme,
                        onRecheckShizuku = viewModel::refreshShizukuStatus,
                        onBackToDashboard = {
                            viewModel.navigateTo(MainNavDestination.DASHBOARD)
                        }
                    )
                }
            }
        }
    }
}
