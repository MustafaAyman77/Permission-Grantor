package com.example.model

import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents the live state of Shizuku installation, version, binder connection, and runtime authorization.
 */
data class ShizukuConnectionState(
    val isShizukuInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val isBinderAlive: Boolean = false,
    val serverApiVersion: Int = -1,
    val isVersionAtLeast11: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val serverUid: Int = -1,
    val lastCheckedTimestamp: Long = 0L
) {
    /**
     * Service is actively running and at least version 11.
     */
    val isServiceConnected: Boolean
        get() = isBinderAlive && isVersionAtLeast11

    /**
     * Ready to execute IPackageManager.grantRuntimePermission calls immediately.
     */
    val isReadyToGrant: Boolean
        get() = isServiceConnected && isPermissionGranted

    val connectionModeLabel: String
        get() = when {
            !isBinderAlive -> "غير متصل"
            serverUid == 0 -> "متصل (وضع Root - UID 0)"
            serverUid == 2000 -> "متصل (وضع ADB - UID 2000)"
            else -> "متصل (UID $serverUid)"
        }

    val statusHeadline: String
        get() = when {
            isReadyToGrant -> "متصل وجاهز لمنح الصلاحيات"
            isServiceConnected && !isPermissionGranted -> "متصل - بانتظار منح إذن Shizuku"
            isBinderAlive && !isVersionAtLeast11 -> "إصدار Shizuku قديم (أقل من 11)"
            isShizukuInstalled && !isBinderAlive -> "غير متصل (خدمة Shizuku متوقفة)"
            else -> "غير متصل (تطبيق Shizuku غير مثبت)"
        }

    val statusDescription: String
        get() = when {
            isReadyToGrant ->
                "خدمة Shizuku تعمل بالإصدار $serverApiVersion وتم منح التطبيق إذن الوصول إلى IPackageManager بنجاح."
            isServiceConnected && !isPermissionGranted ->
                "خدمة Shizuku تعمل حالياً، ولكن يجب السماح لتطبيق Permission Grantor باستخدام Shizuku أولاً."
            isBinderAlive && !isVersionAtLeast11 ->
                "يتطلب التطبيق إصدار Shizuku رقم 11 أو أحدث، بينما الإصدار الحالي هو $serverApiVersion."
            isShizukuInstalled && !isBinderAlive ->
                "تطبيق Shizuku مثبت على جهازك (${installedVersionName ?: "v11+"}) لكن الخدمة غير مفعّلة حالياً. شغّلها عبر ADB اللاسلكي أو USB."
            else ->
                "لم يتم العثور على تطبيق Shizuku على الجهاز، أو أن الخدمة غير قيد التشغيل حالياً."
        }
}

/**
 * Filter categories for the installed applications list.
 */
enum class AppFilterMode(val arabicLabel: String) {
    SIDELOADED_AND_USER("تطبيقات المستخدم وخارج المتجر"),
    NEEDS_PERMISSIONS("لديها صلاحيات غير ممنوحة"),
    ALL_APPS("جميع التطبيقات (مع النظام)")
}

/**
 * Filter tabs inside a target application's permissions detail screen.
 */
enum class PermissionFilterTab(val arabicLabel: String) {
    UNGRANTED("غير ممنوحة"),
    ALL("كل الصلاحيات الحساسة"),
    GRANTED("ممنوحة")
}

/**
 * Represents an installed application inspected via PackageManager with QUERY_ALL_PACKAGES.
 */
data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isSideloaded: Boolean,
    val installerLabel: String,
    val appIcon: Drawable?,
    val requestedPermissions: List<String>,
    val sensitivePermissionsTotal: Int,
    val sensitivePermissionsGranted: Int,
    val sensitivePermissionsUngranted: Int
)

/**
 * Represents a sensitive/runtime permission on the selected target application.
 */
data class PermissionStatusItem(
    val permissionName: String,
    val shortName: String,
    val arabicTitle: String,
    val arabicDescription: String,
    val categoryLabel: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isGranting: Boolean = false,
    val justGrantedCount: Int = 0,
    val isProtectedNote: String? = null,
    val errorMessage: String? = null
)

/**
 * Session log item for granted or failed permission operations.
 */
data class GrantOperationLog(
    val id: Long,
    val appName: String,
    val packageName: String,
    val permissionShortName: String,
    val success: Boolean,
    val message: String,
    val timeFormatted: String
)

/**
 * Metadata and Arabic localization for Android's sensitive permissions.
 */
data class SensitivePermissionMeta(
    val permission: String,
    val arabicTitle: String,
    val arabicDescription: String,
    val categoryLabel: String,
    val icon: ImageVector,
    val protectedHint: String? = null
)

object SensitivePermissionsCatalog {

    val KNOWN_SENSITIVE_PERMISSIONS: Map<String, SensitivePermissionMeta> = listOf(
        SensitivePermissionMeta(
            permission = "android.permission.CAMERA",
            arabicTitle = "الكاميرا والتصوير (CAMERA)",
            arabicDescription = "السماح للتطبيق بالتقاط الصور وتسجيل الفيديو عبر كاميرا الجهاز.",
            categoryLabel = "الوسائط والكاميرا",
            icon = Icons.Filled.CameraAlt
        ),
        SensitivePermissionMeta(
            permission = "android.permission.RECORD_AUDIO",
            arabicTitle = "تسجيل الصوت والميكروفون (RECORD_AUDIO)",
            arabicDescription = "السماح للتطبيق بالتقاط وتسجيل الصوت عبر الميكروفون المدمج أو الخارجي.",
            categoryLabel = "الصوت والميكروفون",
            icon = Icons.Filled.Mic
        ),
        SensitivePermissionMeta(
            permission = "android.permission.ACCESS_FINE_LOCATION",
            arabicTitle = "الموقع الجغرافي الدقيق (ACCESS_FINE_LOCATION)",
            arabicDescription = "الوصول إلى الموقع الدقيق عبر نظام GPS والأقمار الصناعية والشبكات.",
            categoryLabel = "الموقع الجغرافي",
            icon = Icons.Filled.LocationOn
        ),
        SensitivePermissionMeta(
            permission = "android.permission.ACCESS_COARSE_LOCATION",
            arabicTitle = "الموقع الجغرافي التقريبي (ACCESS_COARSE_LOCATION)",
            arabicDescription = "الوصول إلى الموقع التقريبي اعتماداً على أبراج الاتصال وشبكة Wi-Fi.",
            categoryLabel = "الموقع الجغرافي",
            icon = Icons.Filled.LocationOn
        ),
        SensitivePermissionMeta(
            permission = "android.permission.ACCESS_BACKGROUND_LOCATION",
            arabicTitle = "الموقع في الخلفية (ACCESS_BACKGROUND_LOCATION)",
            arabicDescription = "الوصول إلى الموقع الجغرافي حتى أثناء عدم استخدام التطبيق في الواجهة.",
            categoryLabel = "الموقع الجغرافي",
            icon = Icons.Filled.LocationOn,
            protectedHint = "ملاحظة: قد يفرض النظام منح صلاحية الموقع الأساسي أولاً."
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_EXTERNAL_STORAGE",
            arabicTitle = "قراءة مساحة التخزين (READ_EXTERNAL_STORAGE)",
            arabicDescription = "قراءة الملفات والصور والمستندات المحفوظة في ذاكرة الجهاز المشتركة.",
            categoryLabel = "الملفات والتخزين",
            icon = Icons.Filled.Folder
        ),
        SensitivePermissionMeta(
            permission = "android.permission.WRITE_EXTERNAL_STORAGE",
            arabicTitle = "الكتابة في مساحة التخزين (WRITE_EXTERNAL_STORAGE)",
            arabicDescription = "إنشاء وتعديل وحذف الملفات في وحدة التخزين المشتركة للجهاز.",
            categoryLabel = "الملفات والتخزين",
            icon = Icons.Filled.Folder
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_MEDIA_IMAGES",
            arabicTitle = "قراءة الصور (READ_MEDIA_IMAGES)",
            arabicDescription = "الوصول إلى الصور المحفوظة في المعرض (أندرويد 13 فما فوق).",
            categoryLabel = "الوسائط والملفات",
            icon = Icons.Filled.Image
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_MEDIA_VIDEO",
            arabicTitle = "قراءة مقاطع الفيديو (READ_MEDIA_VIDEO)",
            arabicDescription = "الوصول إلى مقاطع الفيديو المحفوظة على الجهاز (أندرويد 13 فما فوق).",
            categoryLabel = "الوسائط والملفات",
            icon = Icons.Filled.Image
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_MEDIA_AUDIO",
            arabicTitle = "قراءة الملفات الصوتية (READ_MEDIA_AUDIO)",
            arabicDescription = "الوصول إلى المقاطع الصوتية والموسيقى على الجهاز (أندرويد 13 فما فوق).",
            categoryLabel = "الوسائط والملفات",
            icon = Icons.Filled.Mic
        ),
        SensitivePermissionMeta(
            permission = "android.permission.POST_NOTIFICATIONS",
            arabicTitle = "إرسال الإشعارات (POST_NOTIFICATIONS)",
            arabicDescription = "عرض التنبيهات والإشعارات الفورية في شريط الحالة (أندرويد 13+).",
            categoryLabel = "التنبيهات",
            icon = Icons.Filled.Notifications
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_CONTACTS",
            arabicTitle = "قراءة جهات الاتصال (READ_CONTACTS)",
            arabicDescription = "الوصول إلى قائمة الأسماء وأرقام الهواتف المحفوظة على الجهاز.",
            categoryLabel = "جهات الاتصال",
            icon = Icons.Filled.Contacts
        ),
        SensitivePermissionMeta(
            permission = "android.permission.WRITE_CONTACTS",
            arabicTitle = "تعديل جهات الاتصال (WRITE_CONTACTS)",
            arabicDescription = "إضافة أو تعديل جهات الاتصال المحفوظة في دفتر العناوين.",
            categoryLabel = "جهات الاتصال",
            icon = Icons.Filled.Contacts
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_PHONE_STATE",
            arabicTitle = "قراءة حالة الهاتف (READ_PHONE_STATE)",
            arabicDescription = "معرفة حالة المكالمات والشبكة الخلوية ومعلومات الشريحة.",
            categoryLabel = "الهاتف والمكالمات",
            icon = Icons.Filled.Call
        ),
        SensitivePermissionMeta(
            permission = "android.permission.CALL_PHONE",
            arabicTitle = "إجراء المكالمات الهاتفية (CALL_PHONE)",
            arabicDescription = "بدء مكالمات هاتفية مباشرة دون المرور بتطبيق الاتصال الافتراضي.",
            categoryLabel = "الهاتف والمكالمات",
            icon = Icons.Filled.Call
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_CALL_LOG",
            arabicTitle = "قراءة سجل المكالمات (READ_CALL_LOG)",
            arabicDescription = "الاطلاع على سجل المكالمات الواردة والصادرة والفائتة.",
            categoryLabel = "الهاتف والمكالمات",
            icon = Icons.Filled.Call,
            protectedHint = "صلاحية مقيدة في أندرويد 13+ للتطبيقات المثبتة من خارج المتجر وقديفك قفلها عبر Shizuku."
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_SMS",
            arabicTitle = "قراءة الرسائل النصية (READ_SMS)",
            arabicDescription = "قراءة الرسائل النصية القصيرة الواردة والمحفوظة.",
            categoryLabel = "الرسائل النصية",
            icon = Icons.Filled.Sms,
            protectedHint = "صلاحية مقيدة في أندرويد 13+ للتطبيقات المثبتة من خارج المتجر."
        ),
        SensitivePermissionMeta(
            permission = "android.permission.SEND_SMS",
            arabicTitle = "إرسال الرسائل النصية (SEND_SMS)",
            arabicDescription = "إرسال رسائل نصية قصيرة SMS مباشرة من التطبيق.",
            categoryLabel = "الرسائل النصية",
            icon = Icons.Filled.Sms
        ),
        SensitivePermissionMeta(
            permission = "android.permission.READ_CALENDAR",
            arabicTitle = "قراءة التقويم والمواعيد (READ_CALENDAR)",
            arabicDescription = "الاطلاع على الأحداث والمواعيد المسجلة في تقويم المستخدم.",
            categoryLabel = "التقويم",
            icon = Icons.Filled.CalendarMonth
        ),
        SensitivePermissionMeta(
            permission = "android.permission.WRITE_CALENDAR",
            arabicTitle = "تعديل التقويم والمواعيد (WRITE_CALENDAR)",
            arabicDescription = "إضافة أو تعديل المواعيد والتذكيرات في التقويم.",
            categoryLabel = "التقويم",
            icon = Icons.Filled.CalendarMonth
        ),
        SensitivePermissionMeta(
            permission = "android.permission.BLUETOOTH_SCAN",
            arabicTitle = "البحث عن أجهزة البلوتوث (BLUETOOTH_SCAN)",
            arabicDescription = "اكتشاف أجهزة البلوتوث القريبة والملحقات اللاسلكية.",
            categoryLabel = "الاتصال القريب",
            icon = Icons.Filled.Bluetooth
        ),
        SensitivePermissionMeta(
            permission = "android.permission.BLUETOOTH_CONNECT",
            arabicTitle = "الاتصال بأجهزة البلوتوث (BLUETOOTH_CONNECT)",
            arabicDescription = "التواصل مع أجهزة البلوتوث المقترنة كالسماعات والساعات الذكية.",
            categoryLabel = "الاتصال القريب",
            icon = Icons.Filled.Bluetooth
        ),
        SensitivePermissionMeta(
            permission = "android.permission.NEARBY_WIFI_DEVICES",
            arabicTitle = "الأجهزة القريبة عبر Wi-Fi (NEARBY_WIFI_DEVICES)",
            arabicDescription = "اكتشاف الأجهزة القريبة والاتصال بها عبر Wi-Fi Direct.",
            categoryLabel = "الاتصال القريب",
            icon = Icons.Filled.Wifi
        ),
        SensitivePermissionMeta(
            permission = "android.permission.ACTIVITY_RECOGNITION",
            arabicTitle = "التعرف على النشاط البدني (ACTIVITY_RECOGNITION)",
            arabicDescription = "تتبع الخطوات والحركة والأنشطة الرياضية للمستخدم.",
            categoryLabel = "المستشعرات والصحة",
            icon = Icons.Filled.DirectionsRun
        ),
        SensitivePermissionMeta(
            permission = "android.permission.BODY_SENSORS",
            arabicTitle = "مستشعرات الجسم الحيوية (BODY_SENSORS)",
            arabicDescription = "الوصول إلى بيانات مستشعرات نبضات القلب والمؤشرات الحيوية.",
            categoryLabel = "المستشعرات والصحة",
            icon = Icons.Filled.MonitorHeart
        )
    ).associateBy { it.permission }

    fun resolveMeta(permissionName: String, fallbackDescription: String? = null): SensitivePermissionMeta {
        KNOWN_SENSITIVE_PERMISSIONS[permissionName]?.let { return it }
        val shortName = permissionName.substringAfterLast('.')
        return SensitivePermissionMeta(
            permission = permissionName,
            arabicTitle = "صلاحية حساسة ($shortName)",
            arabicDescription = fallbackDescription?.takeIf { it.isNotBlank() }
                ?: "صلاحية وقت تشغيل (Runtime Permission) يطلبها التطبيق للوصول إلى موارد النظام.",
            categoryLabel = "صلاحيات إضافية",
            icon = Icons.Filled.Security
        )
    }
}
