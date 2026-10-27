package com.example.shizuku

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import com.example.model.SensitivePermissionsCatalog
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit

/**
 * Preset store identities for spoofing installerPackageName during installation.
 */
enum class StoreIdentityPreset(
    val arabicName: String,
    val installerPackage: String,
    val description: String
) {
    GOOGLE_PLAY(
        arabicName = "متجر Google Play الرسمي",
        installerPackage = "com.android.vending",
        description = "الهوية الأكثر توافقاً لفتح الميزات المحجوبة والإعدادات المقيدة (Android 13+)"
    ),
    FDROID(
        arabicName = "متجر F-Droid الحر",
        installerPackage = "org.fdroid.fdroid",
        description = "هوية متجر التطبيقات مفتوحة المصدر F-Droid"
    ),
    AURORA_STORE(
        arabicName = "متجر Aurora Store",
        installerPackage = "com.aurora.store",
        description = "هوية عميل المتجر البديل Aurora Store"
    ),
    PACKAGE_INSTALLER(
        arabicName = "مُثبّت حزم النظام الرسمي",
        installerPackage = "com.google.android.packageinstaller",
        description = "مُثبّت الحزم الافتراضي المدمج في النظام"
    ),
    CUSTOM(
        arabicName = "حزمة متجر مخصصة",
        installerPackage = "",
        description = "أدخل أي اسم حزمة متجر مخصص يدوياً"
    )
}

/**
 * Preview details extracted from a selected APK file before installation.
 */
data class StagedApkInfo(
    val uriString: String,
    val stagedFilePath: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val requestedPermissions: List<String>,
    val dangerousPermissions: List<String>
)

/**
 * Individual permission grant result inside the post-install report.
 */
data class PostInstallPermissionResult(
    val permissionName: String,
    val shortName: String,
    val arabicTitle: String,
    val isGranted: Boolean,
    val wasAlreadyGranted: Boolean,
    val failureReason: String? = null
)

/**
 * Comprehensive result of the 2-stage Spoofed Installer + Auto Permission Grant operation.
 */
data class ApkInstallReport(
    val packageName: String,
    val appName: String,
    val requestedInstallerPackage: String,
    val actualInstallerPackageAfter: String?,
    val isApkInstalledSuccess: Boolean,
    val isInstallerSpoofedSuccess: Boolean,
    val installMethodUsed: String,
    val installerStatusMessage: String,
    val grantedPermissions: List<PostInstallPermissionResult>,
    val failedPermissions: List<PostInstallPermissionResult>,
    val overallErrorMessage: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Handles two-part APK installation via Shizuku:
 * 1. Spoofing installerPackageName (`com.android.vending` or custom store) using PackageInstaller
 *    SessionParams (`setInstallerPackageName` + `setPackageSource(PACKAGE_SOURCE_STORE)` on Android 13+)
 *    and/or `pm install -i <installer>` via Shizuku process + `IPackageManager.setInstallerPackageName`.
 * 2. Reading the installed package's `AndroidManifest.xml` permissions (`GET_PERMISSIONS`) and
 *    automatically granting all Dangerous Runtime Permissions via `IPackageManager.grantRuntimePermission`
 *    wrapped with `ShizukuBinderWrapper`.
 */
object ShizukuApkInstaller {

    /**
     * Copies the user-selected APK [Uri] into a temporary local file and parses its
     * `AndroidManifest.xml` metadata & permissions via `PackageManager.getPackageArchiveInfo`.
     */
    fun inspectSelectedApkUri(context: Context, uri: Uri): Result<StagedApkInfo> {
        return try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val stagedFile = File(cacheDir, "staged_shizuku_install.apk")
            if (stagedFile.exists()) {
                stagedFile.delete()
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(stagedFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(IllegalStateException("تعذر فتح ملف APK المختار للقراءة."))

            val pm = context.packageManager
            val archiveInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    stagedFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(stagedFile.absolutePath, PackageManager.GET_PERMISSIONS)
            } ?: return Result.failure(
                IllegalArgumentException("الملف المختار ليس حزمة APK صالحة أو أنه تالف.")
            )

            val appInfo = archiveInfo.applicationInfo
            appInfo?.sourceDir = stagedFile.absolutePath
            appInfo?.publicSourceDir = stagedFile.absolutePath

            val appLabel = try {
                appInfo?.let { pm.getApplicationLabel(it).toString() }
                    ?.takeIf { it.isNotBlank() }
                    ?: archiveInfo.packageName
            } catch (_: Throwable) {
                archiveInfo.packageName
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }

            val requestedPerms = archiveInfo.requestedPermissions?.toList() ?: emptyList()
            val dangerousPerms = filterDangerousPermissions(pm, requestedPerms)

            Result.success(
                StagedApkInfo(
                    uriString = uri.toString(),
                    stagedFilePath = stagedFile.absolutePath,
                    fileSizeBytes = stagedFile.length(),
                    packageName = archiveInfo.packageName,
                    appLabel = appLabel,
                    versionName = archiveInfo.versionName ?: "1.0",
                    versionCode = versionCode,
                    requestedPermissions = requestedPerms,
                    dangerousPermissions = dangerousPerms
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Executes the complete 2-stage flow on a staged APK file:
     * 1. Installs the APK with a spoofed installer (`SessionParams.setInstallerPackageName` +
     *    `setPackageSource(PACKAGE_SOURCE_STORE)` on Android 13+, falling back to `pm install -i <installer>`
     *    via `Shizuku.newProcess` if signature/session restrictions block direct session creation).
     * 2. Reads the installed package's `GET_PERMISSIONS` and grants every ungranted dangerous
     *    permission via `IPackageManager.grantRuntimePermission` using `ShizukuBinderWrapper`.
     */
    fun installApkAndGrantPermissions(
        context: Context,
        stagedApk: StagedApkInfo,
        installerPackageName: String,
        autoGrantPermissions: Boolean = true
    ): ApkInstallReport {
        val targetInstaller = installerPackageName.trim().ifEmpty { "com.android.vending" }
        val apkFile = File(stagedApk.stagedFilePath)

        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkInstallReport(
                packageName = stagedApk.packageName,
                appName = stagedApk.appLabel,
                requestedInstallerPackage = targetInstaller,
                actualInstallerPackageAfter = null,
                isApkInstalledSuccess = false,
                isInstallerSpoofedSuccess = false,
                installMethodUsed = "لم يبدأ",
                installerStatusMessage = "ملف APK المؤقت غير موجود. يرجى إعادة اختيار ملف APK.",
                grantedPermissions = emptyList(),
                failedPermissions = emptyList(),
                overallErrorMessage = "ملف APK غير موجود."
            )
        }

        // Verify Shizuku binder & permission first
        if (!Shizuku.pingBinder()) {
            val msg = "خدمة Shizuku غير مفعّلة حالياً. يرجى تشغيل Shizuku عبر ADB اللاسلكي أو USB أولاً."
            return ApkInstallReport(
                packageName = stagedApk.packageName,
                appName = stagedApk.appLabel,
                requestedInstallerPackage = targetInstaller,
                actualInstallerPackageAfter = null,
                isApkInstalledSuccess = false,
                isInstallerSpoofedSuccess = false,
                installMethodUsed = "فشل الاتصال بـ Shizuku",
                installerStatusMessage = msg,
                grantedPermissions = emptyList(),
                failedPermissions = emptyList(),
                overallErrorMessage = msg
            )
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            val msg = "لم يحصل التطبيق على إذن استخدام Shizuku بعد."
            return ApkInstallReport(
                packageName = stagedApk.packageName,
                appName = stagedApk.appLabel,
                requestedInstallerPackage = targetInstaller,
                actualInstallerPackageAfter = null,
                isApkInstalledSuccess = false,
                isInstallerSpoofedSuccess = false,
                installMethodUsed = "بانتظار إذن Shizuku",
                installerStatusMessage = msg,
                grantedPermissions = emptyList(),
                failedPermissions = emptyList(),
                overallErrorMessage = msg
            )
        }

        // Step 1A: Prepare PackageInstaller.SessionParams with setInstallerPackageName & setPackageSource
        var installSuccess = false
        var methodUsed = ""
        var installStatusMsg = ""
        var sessionErrorDetail: String? = null

        val sessionParamsResult = tryCreateSpoofedSessionAndWrite(
            apkFile = apkFile,
            targetInstaller = targetInstaller
        )

        if (sessionParamsResult.isSuccess) {
            val sessionId = sessionParamsResult.getOrThrow()
            // Commit session via Shizuku `pm install-commit <sessionId>` so it completes synchronously
            val commitRes = runShizukuShellCommand(
                command = arrayOf("pm", "install-commit", sessionId.toString()),
                stdinFile = null
            )
            if (commitRes.isSuccess) {
                installSuccess = true
                methodUsed = "PackageInstaller SessionParams (setInstallerPackageName + setPackageSource)"
                installStatusMsg =
                    "تم تثبيت الحزمة وتعيين الهوية ($targetInstaller) عبر جلسة PackageInstaller بنجاح."
            } else {
                sessionErrorDetail = commitRes.exceptionOrNull()?.message
            }
        } else {
            sessionErrorDetail = sessionParamsResult.exceptionOrNull()?.let {
                ShizukuPackageManager.formatShizukuException(it)
            }
        }

        // Step 1B: Fallback to `pm install -r -i <targetInstaller> -S <size>` via Shizuku.newProcess
        // if the system rejected SessionParams due to signature/UID restrictions
        if (!installSuccess) {
            val pmInstallRes = runShizukuShellCommand(
                command = arrayOf(
                    "pm",
                    "install",
                    "-r",
                    "-i",
                    targetInstaller,
                    "-S",
                    apkFile.length().toString()
                ),
                stdinFile = apkFile
            )
            if (pmInstallRes.isSuccess) {
                installSuccess = true
                methodUsed = "أمر Shizuku البديل (pm install -i $targetInstaller)"
                installStatusMsg =
                    "تم التثبيت وتزوير الهوية بنجاح عبر الخطة البديلة (pm install -i $targetInstaller)."
            } else {
                val fallbackErr = pmInstallRes.exceptionOrNull()?.message ?: "خطأ غير معروف"
                return ApkInstallReport(
                    packageName = stagedApk.packageName,
                    appName = stagedApk.appLabel,
                    requestedInstallerPackage = targetInstaller,
                    actualInstallerPackageAfter = null,
                    isApkInstalledSuccess = false,
                    isInstallerSpoofedSuccess = false,
                    installMethodUsed = "فشل عبر الجلسة وعبر pm install -i",
                    installerStatusMessage = "تعذر إكمال التثبيت: $fallbackErr" +
                        (sessionErrorDetail?.let { "\n(سبب رفض الجلسة الأولية: $it)" } ?: ""),
                    grantedPermissions = emptyList(),
                    failedPermissions = emptyList(),
                    overallErrorMessage = "فشل تثبيت ملف APK: $fallbackErr"
                )
            }
        }

        // Also reinforce installerPackageName via IPackageManager.setInstallerPackageName if supported
        try {
            val ipm = ShizukuPackageManager.getIPackageManager()
            ipm.setInstallerPackageName(stagedApk.packageName, targetInstaller)
        } catch (_: Throwable) {
            // Optional reinforcement; `pm install -i` or SessionParams already set the installer
        }

        // Verify actual installer recorded by Android PackageManager
        val verifiedInstaller = ShizukuPackageManager.getVerifiedInstallerPackage(
            context = context,
            packageName = stagedApk.packageName
        )
        val spoofSucceeded = verifiedInstaller == targetInstaller || installSuccess

        if (verifiedInstaller != null && verifiedInstaller != targetInstaller) {
            installStatusMsg += " (المثبت المسجل في النظام حالياً: $verifiedInstaller)"
        }

        // Step 2: Read installed package permissions via PackageManager.getPackageInfo(GET_PERMISSIONS)
        // and grant all Dangerous Runtime Permissions via Shizuku IPackageManager.grantRuntimePermission
        val (grantedList, failedList) = if (autoGrantPermissions) {
            grantDangerousPermissionsForInstalledPackage(
                context = context,
                packageName = stagedApk.packageName,
                fallbackPermissionsFromApk = stagedApk.dangerousPermissions
            )
        } else {
            Pair(emptyList(), emptyList())
        }

        return ApkInstallReport(
            packageName = stagedApk.packageName,
            appName = stagedApk.appLabel,
            requestedInstallerPackage = targetInstaller,
            actualInstallerPackageAfter = verifiedInstaller ?: targetInstaller,
            isApkInstalledSuccess = true,
            isInstallerSpoofedSuccess = spoofSucceeded,
            installMethodUsed = methodUsed,
            installerStatusMessage = installStatusMsg,
            grantedPermissions = grantedList,
            failedPermissions = failedList,
            overallErrorMessage = null
        )
    }

    /**
     * Spoofs the installer package name (`com.android.vending` or custom store) AND automatically
     * grants all ungranted dangerous runtime permissions for an ALREADY installed application!
     */
    fun spoofInstallerAndGrantForExistingApp(
        context: Context,
        packageName: String,
        appName: String,
        installerPackageName: String
    ): ApkInstallReport {
        val targetInstaller = installerPackageName.trim().ifEmpty { "com.android.vending" }

        if (!Shizuku.pingBinder()) {
            val msg = "خدمة Shizuku غير مفعّلة حالياً. يرجى تشغيل Shizuku عبر ADB اللاسلكي أو USB."
            return ApkInstallReport(
                packageName = packageName,
                appName = appName,
                requestedInstallerPackage = targetInstaller,
                actualInstallerPackageAfter = ShizukuPackageManager.getVerifiedInstallerPackage(context, packageName),
                isApkInstalledSuccess = true,
                isInstallerSpoofedSuccess = false,
                installMethodUsed = "فشل الاتصال بـ Shizuku",
                installerStatusMessage = msg,
                grantedPermissions = emptyList(),
                failedPermissions = emptyList(),
                overallErrorMessage = msg
            )
        }

        var spoofSuccess = false
        var methodUsed = ""
        var statusMsg = ""

        // 1. Try IPackageManager.setInstallerPackageName via ShizukuBinderWrapper
        try {
            val ipm = ShizukuPackageManager.getIPackageManager()
            ipm.setInstallerPackageName(packageName, targetInstaller)
            spoofSuccess = true
            methodUsed = "IPackageManager.setInstallerPackageName عبر ShizukuBinderWrapper"
            statusMsg = "تم تعيين هوية المثبت إلى ($targetInstaller) عبر IPackageManager بنجاح."
        } catch (e: SecurityException) {
            // 2. Fallback to `pm set-installer <packageName> <targetInstaller>` via Shizuku.newProcess
            val fallbackCmd = runShizukuShellCommand(
                command = arrayOf("pm", "set-installer", packageName, targetInstaller),
                stdinFile = null
            )
            if (fallbackCmd.isSuccess) {
                spoofSuccess = true
                methodUsed = "أمر Shizuku البديل (pm set-installer $packageName $targetInstaller)"
                statusMsg = "تم تزوير هوية المثبت بنجاح عبر أمر Shizuku (pm set-installer)."
            } else {
                methodUsed = "IPackageManager / pm set-installer"
                statusMsg =
                    "Shizuku لا يملك صلاحية تعديل المثبت لحزمة مثبتة مسبقاً بملكية متجر آخر على هذا الإصدار من أندرويد (لتجاوز ذلك، اختر ملف الـ APK وثبّته من الأعلى عبر زر تثبيت APK). التفاصيل: ${
                        ShizukuPackageManager.formatShizukuException(e)
                    }"
            }
        } catch (e: RemoteException) {
            statusMsg = ShizukuPackageManager.formatShizukuException(e)
        } catch (e: Throwable) {
            statusMsg = "تعذر تعديل هوية المثبت: ${e.localizedMessage}"
        }

        val verifiedAfter = ShizukuPackageManager.getVerifiedInstallerPackage(context, packageName)
        if (verifiedAfter == targetInstaller) {
            spoofSuccess = true
        }

        // Grant all dangerous runtime permissions via IPackageManager.grantRuntimePermission
        val (grantedList, failedList) = grantDangerousPermissionsForInstalledPackage(
            context = context,
            packageName = packageName,
            fallbackPermissionsFromApk = emptyList()
        )

        return ApkInstallReport(
            packageName = packageName,
            appName = appName,
            requestedInstallerPackage = targetInstaller,
            actualInstallerPackageAfter = verifiedAfter,
            isApkInstalledSuccess = true,
            isInstallerSpoofedSuccess = spoofSuccess,
            installMethodUsed = methodUsed,
            installerStatusMessage = statusMsg,
            grantedPermissions = grantedList,
            failedPermissions = failedList,
            overallErrorMessage = if (!spoofSuccess && grantedList.isEmpty() && failedList.isNotEmpty()) {
                statusMsg
            } else {
                null
            }
        )
    }

    /**
     * Reads `PackageManager.getPackageInfo(packageName, GET_PERMISSIONS)` for the installed app,
     * compares requested dangerous permissions with currently granted ones, and invokes
     * `IPackageManager.grantRuntimePermission(packageName, permissionName, userId)` via Shizuku.
     */
    fun grantDangerousPermissionsForInstalledPackage(
        context: Context,
        packageName: String,
        fallbackPermissionsFromApk: List<String>
    ): Pair<List<PostInstallPermissionResult>, List<PostInstallPermissionResult>> {
        val pm = context.packageManager
        val userId = ShizukuPackageManager.currentUserId()

        val requestedPerms: List<String> = try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            pkgInfo.requestedPermissions?.toList() ?: fallbackPermissionsFromApk
        } catch (_: Throwable) {
            fallbackPermissionsFromApk
        }

        val dangerousPerms = filterDangerousPermissions(pm, requestedPerms)
        val grantedResults = mutableListOf<PostInstallPermissionResult>()
        val failedResults = mutableListOf<PostInstallPermissionResult>()

        for (permName in dangerousPerms) {
            val meta = SensitivePermissionsCatalog.resolveMeta(permName)
            val shortName = permName.substringAfterLast('.')

            val alreadyGranted = ShizukuPackageManager.isPermissionGranted(
                context = context,
                packageName = packageName,
                permissionName = permName
            )

            if (alreadyGranted) {
                grantedResults.add(
                    PostInstallPermissionResult(
                        permissionName = permName,
                        shortName = shortName,
                        arabicTitle = meta.arabicTitle,
                        isGranted = true,
                        wasAlreadyGranted = true,
                        failureReason = null
                    )
                )
                continue
            }

            try {
                ShizukuPackageManager.grantPermissionViaShizuku(
                    packageName = packageName,
                    permissionName = permName,
                    userId = userId
                )
                grantedResults.add(
                    PostInstallPermissionResult(
                        permissionName = permName,
                        shortName = shortName,
                        arabicTitle = meta.arabicTitle,
                        isGranted = true,
                        wasAlreadyGranted = false,
                        failureReason = null
                    )
                )
            } catch (e: SecurityException) {
                failedResults.add(
                    PostInstallPermissionResult(
                        permissionName = permName,
                        shortName = shortName,
                        arabicTitle = meta.arabicTitle,
                        isGranted = false,
                        wasAlreadyGranted = false,
                        failureReason = ShizukuPackageManager.formatShizukuException(e, permName)
                    )
                )
            } catch (e: RemoteException) {
                failedResults.add(
                    PostInstallPermissionResult(
                        permissionName = permName,
                        shortName = shortName,
                        arabicTitle = meta.arabicTitle,
                        isGranted = false,
                        wasAlreadyGranted = false,
                        failureReason = ShizukuPackageManager.formatShizukuException(e, permName)
                    )
                )
            } catch (e: Throwable) {
                failedResults.add(
                    PostInstallPermissionResult(
                        permissionName = permName,
                        shortName = shortName,
                        arabicTitle = meta.arabicTitle,
                        isGranted = false,
                        wasAlreadyGranted = false,
                        failureReason = e.localizedMessage ?: "تعذر منح الصلاحية"
                    )
                )
            }
        }

        return Pair(grantedResults, failedResults)
    }

    /**
     * Creates a privileged `PackageInstaller.SessionParams` with:
     * - `setInstallerPackageName(targetInstaller)`
     * - `setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)` on Android 13+
     * Opens the session via Shizuku-wrapped `IPackageInstaller` and writes the APK payload.
     */
    private fun tryCreateSpoofedSessionAndWrite(
        apkFile: File,
        targetInstaller: String
    ): Result<Int> {
        return try {
            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            sessionParams.setSize(apkFile.length())
            sessionParams.setInstallerPackageName(targetInstaller)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sessionParams.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }

            // Obtain IPackageInstaller from IPackageManager wrapped with ShizukuBinderWrapper
            val rawPackageBinder = SystemServiceHelper.getSystemService("package")
                ?: throw RemoteException("تعذر الوصول إلى خدمة الحزم (package).")
            val wrappedPackageBinder = ShizukuBinderWrapper(rawPackageBinder)
            val ipmStub = Class.forName("android.content.pm.IPackageManager\$Stub")
            val ipm = ipmStub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrappedPackageBinder)

            val getPackageInstallerMethod = ipm.javaClass.getMethod("getPackageInstaller")
            val rawInstallerInterface = getPackageInstallerMethod.invoke(ipm)
                ?: throw RemoteException("تعذر الحصول على IPackageInstaller.")
            val asBinderMethod = rawInstallerInterface.javaClass.getMethod("asBinder")
            val rawInstallerBinder = asBinderMethod.invoke(rawInstallerInterface) as IBinder
            val wrappedInstallerBinder = ShizukuBinderWrapper(rawInstallerBinder)

            val ipiStub = Class.forName("android.content.pm.IPackageInstaller\$Stub")
            val ipi = ipiStub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrappedInstallerBinder)

            val userId = ShizukuPackageManager.currentUserId()
            // createSession(SessionParams params, String installerPackageName, String attributionTag, int userId)
            val createSessionId: Int = try {
                val m = ipi.javaClass.getMethod(
                    "createSession",
                    PackageInstaller.SessionParams::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                m.invoke(ipi, sessionParams, targetInstaller, null, userId) as Int
            } catch (_: NoSuchMethodException) {
                val m = ipi.javaClass.getMethod(
                    "createSession",
                    PackageInstaller.SessionParams::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                m.invoke(ipi, sessionParams, targetInstaller, userId) as Int
            }

            // Stream the APK bytes into the created session via `pm install-write -S <size> <sessionId> base.apk`
            val writeRes = runShizukuShellCommand(
                command = arrayOf(
                    "pm",
                    "install-write",
                    "-S",
                    apkFile.length().toString(),
                    createSessionId.toString(),
                    "base.apk"
                ),
                stdinFile = apkFile
            )
            if (writeRes.isFailure) {
                return Result.failure(
                    writeRes.exceptionOrNull()
                        ?: RemoteException("فشل كتابة ملف APK في جلسة التثبيت.")
                )
            }

            Result.success(createSessionId)
        } catch (e: InvocationTargetException) {
            val target = e.targetException ?: e
            Result.failure(target)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Executes a shell command (`pm install -i ...`, `pm set-installer ...`, etc.) via
     * `Shizuku.newProcess` and optionally streams [stdinFile] to its standard input.
     */
    private fun runShizukuShellCommand(
        command: Array<String>,
        stdinFile: File?
    ): Result<String> {
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, command, null, null) as java.lang.Process

            if (stdinFile != null) {
                FileInputStream(stdinFile).use { fileIn ->
                    process.outputStream.use { procOut ->
                        fileIn.copyTo(procOut)
                        procOut.flush()
                    }
                }
            } else {
                try {
                    process.outputStream.close()
                } catch (_: Throwable) {
                }
            }

            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor(60, TimeUnit.SECONDS)
            val exitCode = process.exitValue()

            val combined = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
            if (exitCode == 0 && (combined.contains("Success", ignoreCase = true) || stderr.isEmpty())) {
                Result.success(combined.ifBlank { "Success" })
            } else {
                Result.failure(SecurityException(combined.ifBlank { "Exit code $exitCode" }))
            }
        } catch (e: InvocationTargetException) {
            Result.failure(e.targetException ?: e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun filterDangerousPermissions(
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
}
