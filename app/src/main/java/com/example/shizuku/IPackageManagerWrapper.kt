package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException

/**
 * Interface representing the privileged methods on Android's hidden IPackageManager AIDL
 * accessed through ShizukuBinderWrapper.
 */
interface IPackageManagerBridge {
    @Throws(RemoteException::class, SecurityException::class)
    fun checkPermission(permName: String, pkgName: String, userId: Int): Int

    @Throws(RemoteException::class, SecurityException::class)
    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int)

    @Throws(RemoteException::class, SecurityException::class)
    fun setInstallerPackageName(targetPackage: String, installerPackageName: String)
}

/**
 * Provides access to Android's system IPackageManager wrapped with [ShizukuBinderWrapper]
 * so runtime permissions can be inspected and granted and installer package names can be
 * modified with Shizuku (ADB/Root) privileges without opening system settings manually.
 */
object ShizukuPackageManager {

    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    const val MIN_SHIZUKU_VERSION = 11

    /**
     * Resolves the current Android user ID (e.g. 0 for primary user).
     */
    fun currentUserId(): Int {
        return try {
            val myUid = Process.myUid()
            myUid / 100000
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Obtains an [IPackageManagerBridge] backed by [ShizukuBinderWrapper] around the
     * system "package" service binder (`IPackageManager$Stub.asInterface(...)`).
     */
    @Throws(RemoteException::class, SecurityException::class)
    fun getIPackageManager(): IPackageManagerBridge {
        if (!Shizuku.pingBinder()) {
            throw RemoteException("خدمة Shizuku غير مفعّلة أو غير متصلة حالياً.")
        }
        if (Shizuku.isPreV11()) {
            throw SecurityException("إصدار Shizuku قديم جداً (أقل من الإصدار 11). يرجى تحديث تطبيق Shizuku.")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("لم يتم منح تطبيق Permission Grantor إذن استخدام Shizuku بعد.")
        }

        val rawPackageBinder: IBinder = SystemServiceHelper.getSystemService("package")
            ?: throw RemoteException("تعذر الوصول إلى خدمة الحزم (package service) في النظام.")

        // Wrap the system package manager binder with ShizukuBinderWrapper
        val shizukuWrappedBinder = ShizukuBinderWrapper(rawPackageBinder)

        val iPackageManagerInstance: Any = try {
            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterfaceMethod.invoke(null, shizukuWrappedBinder)
                ?: throw RemoteException("فشل إنشاء واجهة IPackageManager عبر ShizukuBinderWrapper.")
        } catch (e: InvocationTargetException) {
            rethrowTargetException(e)
        } catch (e: Throwable) {
            throw RemoteException("فشل ربط IPackageManager: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }

        return object : IPackageManagerBridge {
            override fun checkPermission(permName: String, pkgName: String, userId: Int): Int {
                try {
                    val method = iPackageManagerInstance.javaClass.getMethod(
                        "checkPermission",
                        String::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                    val result = method.invoke(iPackageManagerInstance, permName, pkgName, userId)
                    return (result as? Int) ?: PackageManager.PERMISSION_DENIED
                } catch (e: InvocationTargetException) {
                    rethrowTargetException(e)
                } catch (e: SecurityException) {
                    throw e
                } catch (e: RemoteException) {
                    throw e
                } catch (e: Throwable) {
                    throw RemoteException("فشل فحص الصلاحية عبر Shizuku: ${e.localizedMessage}")
                }
            }

            override fun grantRuntimePermission(
                packageName: String,
                permissionName: String,
                userId: Int
            ) {
                try {
                    val method = iPackageManagerInstance.javaClass.getMethod(
                        "grantRuntimePermission",
                        String::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                    method.invoke(iPackageManagerInstance, packageName, permissionName, userId)
                } catch (e: InvocationTargetException) {
                    rethrowTargetException(e)
                } catch (e: SecurityException) {
                    throw e
                } catch (e: RemoteException) {
                    throw e
                } catch (e: Throwable) {
                    throw RemoteException("تعذر تنفيذ grantRuntimePermission: ${e.localizedMessage}")
                }
            }

            override fun setInstallerPackageName(
                targetPackage: String,
                installerPackageName: String
            ) {
                try {
                    val method = iPackageManagerInstance.javaClass.getMethod(
                        "setInstallerPackageName",
                        String::class.java,
                        String::class.java
                    )
                    method.invoke(iPackageManagerInstance, targetPackage, installerPackageName)
                } catch (e: InvocationTargetException) {
                    rethrowTargetException(e)
                } catch (e: SecurityException) {
                    throw e
                } catch (e: RemoteException) {
                    throw e
                } catch (e: Throwable) {
                    throw RemoteException("تعذر تعديل هوية المثبت عبر IPackageManager: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Checks whether a specific permission is granted to [packageName].
     * Uses Shizuku's [IPackageManagerBridge.checkPermission] when Shizuku is active and authorized,
     * and falls back to standard [PackageManager.checkPermission] so the user can still inspect
     * permissions even before starting Shizuku.
     */
    fun isPermissionGranted(
        context: Context,
        packageName: String,
        permissionName: String
    ): Boolean {
        val userId = currentUserId()
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    val ipm = getIPackageManager()
                    return ipm.checkPermission(permissionName, packageName, userId) ==
                        PackageManager.PERMISSION_GRANTED
                }
            } catch (_: Throwable) {
                // Fall back to standard PackageManager check below
            }
        }
        return try {
            context.packageManager.checkPermission(permissionName, packageName) ==
                PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Grants a runtime permission immediately via Shizuku's [IPackageManagerBridge.grantRuntimePermission].
     * Explicitly propagates [SecurityException] and [RemoteException] for caller error handling.
     */
    @Throws(RemoteException::class, SecurityException::class)
    fun grantPermissionViaShizuku(
        packageName: String,
        permissionName: String,
        userId: Int = currentUserId()
    ) {
        val ipm = getIPackageManager()
        ipm.grantRuntimePermission(packageName, permissionName, userId)
    }

    /**
     * Reads back the current installer package name for [packageName] from the system.
     */
    fun getVerifiedInstallerPackage(context: Context, packageName: String): String? {
        val pm = context.packageManager
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

    /**
     * Converts [SecurityException] or [RemoteException] into clear, user-friendly Arabic explanations.
     */
    fun formatShizukuException(throwable: Throwable, permissionName: String? = null): String {
        val shortPerm = permissionName?.substringAfterLast('.') ?: ""
        return when (throwable) {
            is RemoteException -> {
                val msg = throwable.message.orEmpty()
                if (msg.contains("غير مفعّلة") || !Shizuku.pingBinder()) {
                    "خدمة Shizuku غير مفعّلة حالياً. يرجى تشغيل خدمة Shizuku من تطبيق Shizuku عبر ADB اللاسلكي أو USB ثم المحاولة مجدداً."
                } else {
                    "انقطع الاتصال بخدمة Shizuku أثناء تنفيذ الأمر (RemoteException): $msg"
                }
            }
            is SecurityException -> {
                val msg = throwable.message.orEmpty()
                when {
                    !Shizuku.pingBinder() ->
                        "خدمة Shizuku غير مفعّلة حالياً."
                    msg.contains("لم يتم منح تطبيق") ->
                        "يرجى الموافقة على نافذة إذن Shizuku أولاً للسماح للتطبيق بتنفيذ أوامر المنح والتثبيت."
                    msg.contains("installerPackageName", ignoreCase = true) ||
                        msg.contains("not signed with", ignoreCase = true) ->
                        "Shizuku لا يملك صلاحية تعديل المثبت مباشرة عبر الجلسة على هذا الإصدار من أندرويد؛ يتم استخدام خطة pm install -i البديلة."
                    msg.contains("not a changeable permission type", ignoreCase = true) ||
                        msg.contains("fixed", ignoreCase = true) ||
                        msg.contains("protection level", ignoreCase = true) ->
                        "الصلاحية ($shortPerm) محمية من النظام أو ثابتة ولا يسمح أندرويد بتغييرها عبر مستوى ADB، بل تتطلب تدخل المستخدم يدوياً من إعدادات النظام."
                    else ->
                        "رفض النظام العملية بسبب قيود الحماية (SecurityException): يوفّر Shizuku صلاحيات بمستوى ADB وليس روت كامل. التفاصيل: $msg"
                }
            }
            else -> {
                "حدث خطأ غير متوقع: ${throwable.localizedMessage ?: throwable.javaClass.simpleName}"
            }
        }
    }

    @Throws(RemoteException::class, SecurityException::class)
    internal fun rethrowTargetException(e: InvocationTargetException): Nothing {
        when (val target = e.targetException) {
            is SecurityException -> throw target
            is RemoteException -> throw target
            is RuntimeException -> throw SecurityException(target.message ?: "خطأ أمني من النظام", target)
            else -> throw RemoteException(target?.message ?: e.message ?: "خطأ في اتصال Binder")
        }
    }
}
