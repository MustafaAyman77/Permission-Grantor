package com.example.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppFilterMode
import com.example.model.GrantOperationLog
import com.example.model.InstalledAppItem
import com.example.model.PermissionFilterTab
import com.example.model.PermissionStatusItem
import com.example.model.SensitivePermissionsCatalog
import com.example.model.ShizukuConnectionState
import com.example.shizuku.ApkInstallReport
import com.example.shizuku.ShizukuApkInstaller
import com.example.shizuku.ShizukuPackageManager
import com.example.shizuku.StagedApkInfo
import com.example.shizuku.StoreIdentityPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainNavDestination {
    DASHBOARD,
    APK_INSTALLER,
    APPS_LIST,
    APP_PERMISSIONS_DETAIL,
    SETTINGS_GUIDE
}

data class PermissionGrantorUiState(
    val currentDestination: MainNavDestination = MainNavDestination.DASHBOARD,
    val shizukuState: ShizukuConnectionState = ShizukuConnectionState(),
    val isRequestingShizukuPermission: Boolean = false,
    val isLoadingApps: Boolean = true,
    val allApps: List<InstalledAppItem> = emptyList(),
    val searchQuery: String = "",
    val appFilterMode: AppFilterMode = AppFilterMode.SIDELOADED_AND_USER,
    val selectedApp: InstalledAppItem? = null,
    val selectedAppPermissions: List<PermissionStatusItem> = emptyList(),
    val permissionFilterTab: PermissionFilterTab = PermissionFilterTab.UNGRANTED,
    val isLoadingPermissions: Boolean = false,
    val isBatchGranting: Boolean = false,
    // APK Installer & Spoofed Store Identity State
    val selectedStorePreset: StoreIdentityPreset = StoreIdentityPreset.GOOGLE_PLAY,
    val customInstallerPackage: String = "com.android.vending",
    val autoGrantOnInstall: Boolean = true,
    val stagedApkInfo: StagedApkInfo? = null,
    val isInstallingApk: Boolean = false,
    val latestInstallReport: ApkInstallReport? = null,
    val bannerMessage: String? = null,
    val isBannerError: Boolean = false,
    val operationLogs: List<GrantOperationLog> = emptyList(),
    val darkTheme: Boolean = true
) {
    val effectiveInstallerPackage: String
        get() = if (selectedStorePreset == StoreIdentityPreset.CUSTOM) {
            customInstallerPackage.trim().ifEmpty { "com.android.vending" }
        } else {
            selectedStorePreset.installerPackage
        }
}

class PermissionGrantorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private val PLAY_STORE_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.feedback"
        )
    }

    private val _uiState = MutableStateFlow(PermissionGrantorUiState())
    val uiState: StateFlow<PermissionGrantorUiState> = _uiState.asStateFlow()

    /**
     * Stores a pending action (grant or APK install) if the user initiated an operation
     * before granting Shizuku permission to this app. Executed only after user approves.
     */
    private var pendingActionAfterShizukuAuth: (() -> Unit)? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuStatus()
        _uiState.value.selectedApp?.let { loadPermissionsForApp(it, silent = true) }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshShizukuStatus()
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                _uiState.update { it.copy(isRequestingShizukuPermission = false) }
                refreshShizukuStatus()
                if (granted) {
                    showBanner("تم منح إذن استخدام Shizuku بنجاح! جاري استكمال العملية...", isError = false)
                    val pending = pendingActionAfterShizukuAuth
                    pendingActionAfterShizukuAuth = null
                    pending?.invoke()
                } else {
                    pendingActionAfterShizukuAuth = null
                    showBanner(
                        "تم رفض إذن الوصول إلى Shizuku. يرجى الموافقة على الإذن لتتمكن من تثبيت APK ومنح الصلاحيات.",
                        isError = true
                    )
                }
            }
        }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (_: Throwable) {
            // Safe guard if initialized in unit test environment
        }
        refreshShizukuStatus()
        loadInstalledApps()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (_: Throwable) {
        }
    }

    /**
     * Checks whether Shizuku app is installed, whether its version is >= 11,
     * whether the Shizuku service binder is alive, and whether our app has permission.
     */
    fun refreshShizukuStatus() {
        val context = getApplication<Application>()
        val pm = context.packageManager

        var isInstalled = false
        var versionName: String? = null
        var versionCode = 0L

        try {
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    ShizukuPackageManager.SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ShizukuPackageManager.SHIZUKU_PACKAGE_NAME, 0)
            }
            isInstalled = true
            versionName = pkgInfo.versionName
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            isInstalled = false
        } catch (_: Throwable) {
            isInstalled = false
        }

        var binderAlive = false
        var serverVersion = -1
        var isAtLeast11 = false
        var permGranted = false
        var serverUid = -1

        try {
            binderAlive = Shizuku.pingBinder()
            if (binderAlive) {
                isInstalled = true
                if (!Shizuku.isPreV11()) {
                    serverVersion = Shizuku.getVersion()
                    isAtLeast11 = serverVersion >= ShizukuPackageManager.MIN_SHIZUKU_VERSION
                    serverUid = Shizuku.getUid()
                    permGranted = if (Shizuku.shouldShowRequestPermissionRationale()) {
                        false
                    } else {
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    }
                } else {
                    serverVersion = 10
                    isAtLeast11 = false
                }
            }
        } catch (_: Throwable) {
            binderAlive = false
        }

        val newState = ShizukuConnectionState(
            isShizukuInstalled = isInstalled,
            installedVersionName = versionName,
            installedVersionCode = versionCode,
            isBinderAlive = binderAlive,
            serverApiVersion = serverVersion,
            isVersionAtLeast11 = isAtLeast11,
            isPermissionGranted = permGranted,
            serverUid = serverUid,
            lastCheckedTimestamp = System.currentTimeMillis()
        )

        _uiState.update { it.copy(shizukuState = newState) }
    }

    /**
     * Requests Shizuku runtime authorization via standard `Shizuku.requestPermission()`.
     */
    fun requestShizukuPermission(onGrantedAction: (() -> Unit)? = null) {
        refreshShizukuStatus()
        val state = _uiState.value.shizukuState

        if (!state.isBinderAlive) {
            showBanner(
                "خدمة Shizuku غير مفعّلة حالياً. يرجى تشغيل خدمة Shizuku أولاً (راجع تبويب الإعدادات).",
                isError = true
            )
            return
        }

        if (!state.isVersionAtLeast11) {
            showBanner(
                "إصدار Shizuku الحالي قديم جداً (أقل من الإصدار 11). يرجى تحديث تطبيق Shizuku.",
                isError = true
            )
            return
        }

        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshShizukuStatus()
                onGrantedAction?.invoke()
                return
            }

            pendingActionAfterShizukuAuth = onGrantedAction
            _uiState.update { it.copy(isRequestingShizukuPermission = true) }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (e: SecurityException) {
            _uiState.update { it.copy(isRequestingShizukuPermission = false) }
            showBanner(ShizukuPackageManager.formatShizukuException(e), isError = true)
        } catch (e: Throwable) {
            _uiState.update { it.copy(isRequestingShizukuPermission = false) }
            showBanner("تعذر طلب إذن Shizuku: ${e.localizedMessage}", isError = true)
        }
    }

    /**
     * Called when the user selects an APK file via the system file picker.
     * 1. Parses the APK's AndroidManifest.xml (package name, version, requested dangerous permissions).
     * 2. Verifies Shizuku connection & requests `Shizuku.requestPermission()` if not granted yet.
     */
    fun onApkFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentDestination = MainNavDestination.APK_INSTALLER,
                    isInstallingApk = true,
                    bannerMessage = null
                )
            }
            refreshShizukuStatus()

            val context = getApplication<Application>()
            val inspectResult = withContext(Dispatchers.IO) {
                ShizukuApkInstaller.inspectSelectedApkUri(context, uri)
            }

            inspectResult.fold(
                onSuccess = { staged ->
                    _uiState.update {
                        it.copy(
                            stagedApkInfo = staged,
                            isInstallingApk = false
                        )
                    }
                    // Verify Shizuku status and request permission if needed
                    val shizukuState = _uiState.value.shizukuState
                    if (!shizukuState.isBinderAlive) {
                        showBanner(
                            "تم قراءة ملف APK (${staged.appLabel}) بنجاح، ولكن خدمة Shizuku غير مفعّلة حالياً. شغّل Shizuku ثم اضغط على بدء التثبيت.",
                            isError = true
                        )
                    } else if (!shizukuState.isPermissionGranted) {
                        showBanner(
                            "تم تجهيز ملف APK (${staged.appLabel}). يرجى الموافقة على إذن Shizuku أولاً لتنفيذ التثبيت بهوية المتجر.",
                            isError = false
                        )
                        requestShizukuPermission {
                            installStagedApkWithSpoofAndGrant()
                        }
                    } else {
                        // Shizuku is ready & authorized: automatically start the spoofed install + auto-grant!
                        installStagedApkWithSpoofAndGrant()
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isInstallingApk = false) }
                    showBanner(
                        "تعذر قراءة ملف APK المختار: ${err.localizedMessage ?: "ملف غير صالح"}",
                        isError = true
                    )
                }
            )
        }
    }

    /**
     * Installs the staged APK with the spoofed store identity (`com.android.vending` or custom)
     * and automatically grants all requested dangerous runtime permissions via Shizuku.
     */
    fun installStagedApkWithSpoofAndGrant() {
        val staged = _uiState.value.stagedApkInfo ?: run {
            showBanner("يرجى اختيار ملف APK أولاً.", isError = true)
            return
        }

        refreshShizukuStatus()
        val shizukuState = _uiState.value.shizukuState

        if (!shizukuState.isBinderAlive) {
            showBanner(
                "خدمة Shizuku غير مفعّلة حالياً. يرجى تفعيل Shizuku عبر ADB اللاسلكي أو USB قبل التثبيت.",
                isError = true
            )
            return
        }

        if (!shizukuState.isPermissionGranted) {
            requestShizukuPermission {
                installStagedApkWithSpoofAndGrant()
            }
            return
        }

        val installerPkg = _uiState.value.effectiveInstallerPackage
        val autoGrant = _uiState.value.autoGrantOnInstall

        viewModelScope.launch {
            _uiState.update { it.copy(isInstallingApk = true, bannerMessage = null) }
            val context = getApplication<Application>()

            val report = withContext(Dispatchers.IO) {
                ShizukuApkInstaller.installApkAndGrantPermissions(
                    context = context,
                    stagedApk = staged,
                    installerPackageName = installerPkg,
                    autoGrantPermissions = autoGrant
                )
            }

            _uiState.update {
                it.copy(
                    isInstallingApk = false,
                    latestInstallReport = report,
                    bannerMessage = if (report.isApkInstalledSuccess) {
                        "تم تثبيت ${report.appName} بهوية المتجر ومنح ${report.grantedPermissions.size} صلاحية!"
                    } else {
                        report.overallErrorMessage ?: report.installerStatusMessage
                    },
                    isBannerError = !report.isApkInstalledSuccess
                )
            }

            loadInstalledApps()
        }
    }

    /**
     * Spoofs the installer package name (`com.android.vending` or chosen store) AND grants all
     * dangerous runtime permissions for an already-installed application via Shizuku.
     */
    fun spoofAndGrantExistingApp(app: InstalledAppItem) {
        refreshShizukuStatus()
        val shizukuState = _uiState.value.shizukuState

        if (!shizukuState.isBinderAlive) {
            showBanner(
                "خدمة Shizuku غير مفعّلة حالياً. يرجى تشغيل Shizuku عبر ADB اللاسلكي أو USB أولاً.",
                isError = true
            )
            return
        }

        if (!shizukuState.isPermissionGranted) {
            requestShizukuPermission {
                spoofAndGrantExistingApp(app)
            }
            return
        }

        val installerPkg = _uiState.value.effectiveInstallerPackage

        viewModelScope.launch {
            _uiState.update { it.copy(isInstallingApk = true, bannerMessage = null) }
            val context = getApplication<Application>()

            val report = withContext(Dispatchers.IO) {
                ShizukuApkInstaller.spoofInstallerAndGrantForExistingApp(
                    context = context,
                    packageName = app.packageName,
                    appName = app.appName,
                    installerPackageName = installerPkg
                )
            }

            _uiState.update {
                it.copy(
                    isInstallingApk = false,
                    latestInstallReport = report,
                    bannerMessage = "اكتمل تحديث هوية المتجر ومنح (${report.grantedPermissions.size}) صلاحية لتطبيق ${app.appName}",
                    isBannerError = report.overallErrorMessage != null
                )
            }

            loadInstalledApps()
        }
    }

    fun selectStorePreset(preset: StoreIdentityPreset) {
        _uiState.update {
            it.copy(
                selectedStorePreset = preset,
                customInstallerPackage = if (preset != StoreIdentityPreset.CUSTOM) {
                    preset.installerPackage
                } else {
                    it.customInstallerPackage
                }
            )
        }
    }

    fun updateCustomInstallerPackage(pkg: String) {
        _uiState.update { it.copy(customInstallerPackage = pkg) }
    }

    fun setAutoGrantOnInstall(enabled: Boolean) {
        _uiState.update { it.copy(autoGrantOnInstall = enabled) }
    }

    /**
     * Queries installed packages via PackageManager (using QUERY_ALL_PACKAGES)
     * and calculates sensitive permission counts for each app.
     */
    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val context = getApplication<Application>()
            val pm = context.packageManager
            val myPkg = context.packageName

            val items = withContext(Dispatchers.IO) {
                val packages: List<PackageInfo> = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                    }
                } catch (_: Throwable) {
                    emptyList()
                }

                packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    if (pkg.packageName == myPkg) return@mapNotNull null

                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                    val installerPkg = getInstallerPackage(pm, pkg.packageName)
                    val isSideloaded = !isSystem && (installerPkg == null || installerPkg !in PLAY_STORE_INSTALLERS)

                    val installerLabel = when {
                        isSystem -> "تطبيق نظام"
                        installerPkg in PLAY_STORE_INSTALLERS -> "متجر Google Play"
                        installerPkg == "org.fdroid.fdroid" -> "متجر F-Droid"
                        installerPkg == "com.aurora.store" -> "متجر Aurora Store"
                        installerPkg?.contains("packageinstaller", ignoreCase = true) == true -> "مثبت خارجياً (APK)"
                        installerPkg == null -> "مثبت خارجياً (خارج المتجر)"
                        else -> "مصدر ($installerPkg)"
                    }

                    val requestedPerms = pkg.requestedPermissions?.toList() ?: emptyList()
                    val sensitivePerms = filterSensitivePermissions(pm, requestedPerms)

                    var grantedCount = 0
                    for (perm in sensitivePerms) {
                        val isGranted = ShizukuPackageManager.isPermissionGranted(
                            context = context,
                            packageName = pkg.packageName,
                            permissionName = perm
                        )
                        if (isGranted) grantedCount++
                    }

                    val totalSensitive = sensitivePerms.size
                    val ungrantedCount = (totalSensitive - grantedCount).coerceAtLeast(0)

                    val label = try {
                        pm.getApplicationLabel(appInfo).toString().ifBlank { pkg.packageName }
                    } catch (_: Throwable) {
                        pkg.packageName
                    }

                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (_: Throwable) {
                        null
                    }

                    InstalledAppItem(
                        packageName = pkg.packageName,
                        appName = label,
                        versionName = pkg.versionName ?: "1.0",
                        isSystemApp = isSystem,
                        isSideloaded = isSideloaded,
                        installerLabel = installerLabel,
                        appIcon = icon,
                        requestedPermissions = sensitivePerms,
                        sensitivePermissionsTotal = totalSensitive,
                        sensitivePermissionsGranted = grantedCount,
                        sensitivePermissionsUngranted = ungrantedCount
                    )
                }.sortedWith(
                    compareByDescending<InstalledAppItem> { it.isSideloaded }
                        .thenByDescending { it.sensitivePermissionsUngranted }
                        .thenByDescending { it.sensitivePermissionsTotal }
                        .thenBy { it.appName.lowercase() }
                )
            }

            _uiState.update { state ->
                val updatedSelected = state.selectedApp?.let { current ->
                    items.find { it.packageName == current.packageName } ?: current
                }
                state.copy(
                    isLoadingApps = false,
                    allApps = items,
                    selectedApp = updatedSelected
                )
            }
        }
    }

    /**
     * Selects an app and loads its sensitive permissions status via Shizuku IPackageManager.
     */
    fun selectApp(app: InstalledAppItem) {
        _uiState.update {
            it.copy(
                selectedApp = app,
                currentDestination = MainNavDestination.APP_PERMISSIONS_DETAIL,
                permissionFilterTab = if (app.sensitivePermissionsUngranted > 0) {
                    PermissionFilterTab.UNGRANTED
                } else {
                    PermissionFilterTab.ALL
                },
                bannerMessage = null
            )
        }
        loadPermissionsForApp(app, silent = false)
    }

    fun loadPermissionsForApp(app: InstalledAppItem, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingPermissions = true) }
            }
            refreshShizukuStatus()

            val context = getApplication<Application>()
            val pm = context.packageManager
            val previousItems = _uiState.value.selectedAppPermissions.associateBy { it.permissionName }

            val statusList = withContext(Dispatchers.IO) {
                val sensitivePerms = app.requestedPermissions
                sensitivePerms.map { permName ->
                    val granted = ShizukuPackageManager.isPermissionGranted(
                        context = context,
                        packageName = app.packageName,
                        permissionName = permName
                    )
                    val systemDesc = try {
                        val pInfo = pm.getPermissionInfo(permName, 0)
                        pInfo.loadDescription(pm)?.toString()
                    } catch (_: Throwable) {
                        null
                    }
                    val meta = SensitivePermissionsCatalog.resolveMeta(permName, systemDesc)
                    val prev = previousItems[permName]

                    PermissionStatusItem(
                        permissionName = permName,
                        shortName = permName.substringAfterLast('.'),
                        arabicTitle = meta.arabicTitle,
                        arabicDescription = meta.arabicDescription,
                        categoryLabel = meta.categoryLabel,
                        icon = meta.icon,
                        isGranted = granted,
                        isGranting = false,
                        justGrantedCount = prev?.justGrantedCount ?: 0,
                        isProtectedNote = meta.protectedHint,
                        errorMessage = null
                    )
                }.sortedWith(
                    compareBy<PermissionStatusItem> { it.isGranted }
                        .thenBy { it.shortName }
                )
            }

            val grantedCount = statusList.count { it.isGranted }
            val ungrantedCount = statusList.size - grantedCount

            _uiState.update { state ->
                val updatedApp = state.selectedApp?.copy(
                    sensitivePermissionsTotal = statusList.size,
                    sensitivePermissionsGranted = grantedCount,
                    sensitivePermissionsUngranted = ungrantedCount
                )
                val updatedAllApps = state.allApps.map { item ->
                    if (item.packageName == app.packageName && updatedApp != null) updatedApp else item
                }
                state.copy(
                    isLoadingPermissions = false,
                    selectedApp = updatedApp,
                    selectedAppPermissions = statusList,
                    allApps = updatedAllApps
                )
            }
        }
    }

    /**
     * Grants a single runtime permission immediately via Shizuku's IPackageManager.
     */
    fun grantSinglePermission(permissionName: String) {
        val targetApp = _uiState.value.selectedApp ?: return
        refreshShizukuStatus()
        val shizukuState = _uiState.value.shizukuState

        if (!shizukuState.isBinderAlive) {
            val errMsg = "خدمة Shizuku غير مفعّلة. يرجى تشغيل خدمة Shizuku عبر ADB اللاسلكي أو USB أولاً."
            updatePermissionItemError(permissionName, errMsg)
            showBanner(errMsg, isError = true)
            appendLog(targetApp, permissionName, false, errMsg)
            return
        }

        if (!shizukuState.isVersionAtLeast11) {
            val errMsg = "إصدار Shizuku أقل من 11. يرجى تحديث تطبيق Shizuku."
            updatePermissionItemError(permissionName, errMsg)
            showBanner(errMsg, isError = true)
            return
        }

        if (!shizukuState.isPermissionGranted) {
            showBanner("بانتظار موافقتك على إذن Shizuku لتنفيذ منح الصلاحية...", isError = false)
            requestShizukuPermission {
                executeGrantPermissionInternal(targetApp, permissionName)
            }
            return
        }

        executeGrantPermissionInternal(targetApp, permissionName)
    }

    private fun executeGrantPermissionInternal(targetApp: InstalledAppItem, permissionName: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    selectedAppPermissions = state.selectedAppPermissions.map { item ->
                        if (item.permissionName == permissionName) {
                            item.copy(isGranting = true, errorMessage = null)
                        } else {
                            item
                        }
                    }
                )
            }

            val userId = ShizukuPackageManager.currentUserId()
            val result: Result<Unit> = withContext(Dispatchers.IO) {
                try {
                    ShizukuPackageManager.grantPermissionViaShizuku(
                        packageName = targetApp.packageName,
                        permissionName = permissionName,
                        userId = userId
                    )
                    Result.success(Unit)
                } catch (e: SecurityException) {
                    Result.failure(e)
                } catch (e: RemoteException) {
                    Result.failure(e)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = {
                    val shortPerm = permissionName.substringAfterLast('.')
                    val msg = "تم منح صلاحية $shortPerm لتطبيق ${targetApp.appName} عبر Shizuku بنجاح!"
                    _uiState.update { state ->
                        val updatedPerms = state.selectedAppPermissions.map { item ->
                            if (item.permissionName == permissionName) {
                                item.copy(
                                    isGranted = true,
                                    isGranting = false,
                                    justGrantedCount = item.justGrantedCount + 1,
                                    errorMessage = null
                                )
                            } else {
                                item
                            }
                        }
                        val grantedCount = updatedPerms.count { it.isGranted }
                        val ungrantedCount = updatedPerms.size - grantedCount
                        val updatedApp = state.selectedApp?.copy(
                            sensitivePermissionsGranted = grantedCount,
                            sensitivePermissionsUngranted = ungrantedCount
                        )
                        val updatedAllApps = state.allApps.map { app ->
                            if (app.packageName == targetApp.packageName && updatedApp != null) updatedApp else app
                        }
                        state.copy(
                            selectedApp = updatedApp,
                            selectedAppPermissions = updatedPerms,
                            allApps = updatedAllApps,
                            bannerMessage = msg,
                            isBannerError = false
                        )
                    }
                    appendLog(targetApp, permissionName, true, msg)
                },
                onFailure = { throwable ->
                    val readableError = ShizukuPackageManager.formatShizukuException(throwable, permissionName)
                    updatePermissionItemError(permissionName, readableError)
                    showBanner(readableError, isError = true)
                    appendLog(targetApp, permissionName, false, readableError)
                }
            )
        }
    }

    /**
     * Grants all remaining ungranted sensitive permissions for the selected app in one tap via Shizuku.
     */
    fun grantAllRemainingPermissions() {
        val targetApp = _uiState.value.selectedApp ?: return
        val ungranted = _uiState.value.selectedAppPermissions.filter { !it.isGranted }
        if (ungranted.isEmpty()) {
            showBanner("جميع الصلاحيات الحساسة ممنوحة بالفعل لهذا التطبيق!", isError = false)
            return
        }

        refreshShizukuStatus()
        val shizukuState = _uiState.value.shizukuState

        if (!shizukuState.isBinderAlive) {
            val errMsg = "خدمة Shizuku غير مفعّلة. يرجى تشغيل خدمة Shizuku أولاً."
            showBanner(errMsg, isError = true)
            return
        }

        if (!shizukuState.isPermissionGranted) {
            showBanner("يرجى الموافقة على إذن Shizuku أولاً لمنح الصلاحيات المتبقية...", isError = false)
            requestShizukuPermission {
                grantAllRemainingPermissions()
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBatchGranting = true, bannerMessage = null) }
            val userId = ShizukuPackageManager.currentUserId()
            var successCount = 0
            var failCount = 0
            var lastErrorText: String? = null

            for (permItem in ungranted) {
                try {
                    withContext(Dispatchers.IO) {
                        ShizukuPackageManager.grantPermissionViaShizuku(
                            packageName = targetApp.packageName,
                            permissionName = permItem.permissionName,
                            userId = userId
                        )
                    }
                    successCount++
                    _uiState.update { state ->
                        state.copy(
                            selectedAppPermissions = state.selectedAppPermissions.map { item ->
                                if (item.permissionName == permItem.permissionName) {
                                    item.copy(
                                        isGranted = true,
                                        isGranting = false,
                                        justGrantedCount = item.justGrantedCount + 1,
                                        errorMessage = null
                                    )
                                } else {
                                    item
                                }
                            }
                        )
                    }
                    appendLog(
                        targetApp,
                        permItem.permissionName,
                        true,
                        "تم المنح بنجاح ضمن المنح الجماعي"
                    )
                } catch (e: Throwable) {
                    failCount++
                    val errText = ShizukuPackageManager.formatShizukuException(e, permItem.permissionName)
                    lastErrorText = errText
                    updatePermissionItemError(permItem.permissionName, errText)
                    appendLog(targetApp, permItem.permissionName, false, errText)
                }
            }

            _uiState.update { state ->
                val grantedTotal = state.selectedAppPermissions.count { it.isGranted }
                val ungrantedTotal = state.selectedAppPermissions.size - grantedTotal
                val updatedApp = state.selectedApp?.copy(
                    sensitivePermissionsGranted = grantedTotal,
                    sensitivePermissionsUngranted = ungrantedTotal
                )
                val updatedAllApps = state.allApps.map { app ->
                    if (app.packageName == targetApp.packageName && updatedApp != null) updatedApp else app
                }
                val summaryMsg = when {
                    failCount == 0 -> "تم منح $successCount صلاحية بنجاح عبر Shizuku!"
                    successCount > 0 -> "تم منح $successCount صلاحية، وتعذر منح $failCount صلاحية محمية."
                    else -> lastErrorText ?: "تعذر منح الصلاحيات عبر Shizuku."
                }
                state.copy(
                    isBatchGranting = false,
                    selectedApp = updatedApp,
                    allApps = updatedAllApps,
                    bannerMessage = summaryMsg,
                    isBannerError = successCount == 0 && failCount > 0
                )
            }
        }
    }

    fun navigateTo(destination: MainNavDestination) {
        _uiState.update { it.copy(currentDestination = destination, bannerMessage = null) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setAppFilterMode(mode: AppFilterMode) {
        _uiState.update { it.copy(appFilterMode = mode) }
    }

    fun setPermissionFilterTab(tab: PermissionFilterTab) {
        _uiState.update { it.copy(permissionFilterTab = tab) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(darkTheme = !it.darkTheme) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    private fun showBanner(message: String, isError: Boolean) {
        _uiState.update { it.copy(bannerMessage = message, isBannerError = isError) }
    }

    private fun updatePermissionItemError(permissionName: String, errorMsg: String) {
        _uiState.update { state ->
            state.copy(
                selectedAppPermissions = state.selectedAppPermissions.map { item ->
                    if (item.permissionName == permissionName) {
                        item.copy(isGranting = false, errorMessage = errorMsg)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun appendLog(
        app: InstalledAppItem,
        permissionName: String,
        success: Boolean,
        message: String
    ) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = GrantOperationLog(
            id = System.nanoTime(),
            appName = app.appName,
            packageName = app.packageName,
            permissionShortName = permissionName.substringAfterLast('.'),
            success = success,
            message = message,
            timeFormatted = timeFmt
        )
        _uiState.update { state ->
            state.copy(operationLogs = (listOf(entry) + state.operationLogs).take(30))
        }
    }

    private fun filterSensitivePermissions(
        pm: PackageManager,
        requestedPermissions: List<String>
    ): List<String> {
        return requestedPermissions.filter { permName ->
            if (permName in SensitivePermissionsCatalog.KNOWN_SENSITIVE_PERMISSIONS) {
                return@filter true
            }
            try {
                val pInfo = pm.getPermissionInfo(permName, 0)
                val baseProtection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.protection
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                }
                baseProtection == PermissionInfo.PROTECTION_DANGEROUS
            } catch (_: Throwable) {
                false
            }
        }.distinct()
    }

    private fun getInstallerPackage(pm: PackageManager, packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(packageName)
                info.installingPackageName ?: info.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
