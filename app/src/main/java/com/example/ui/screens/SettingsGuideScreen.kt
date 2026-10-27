package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ShizukuConnectionState
import com.example.shizuku.ShizukuPackageManager
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.ShizukuAdbLevelNoticeCard

private const val ADB_START_COMMAND =
    "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

@Composable
fun SettingsGuideScreen(
    shizukuState: ShizukuConnectionState,
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onRecheckShizuku: () -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToDashboard)

    val context = LocalContext.current
    var commandCopied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_guide_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الإعدادات ودليل تفعيل Shizuku",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "خطوات تشغيل الخدمة عبر ADB اللاسلكي أو كابل USB لمن لا يعرفها",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConnectionStatusBadge(isConnected = shizukuState.isServiceConnected)
            }
        }

        // Quick Tools & Theme Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                imageVector = if (darkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = "المظهر",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "المظهر الليلي الاحترافي (Cyber Dark)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "التبديل بين الوضع الداكن والوضع الفاتح",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = { onToggleDarkTheme() },
                            modifier = Modifier.testTag("theme_toggle_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { openShizukuAppOrStore(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_shizuku_app_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = "فتح Shizuku",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فتح تطبيق Shizuku")
                        }

                        OutlinedButton(
                            onClick = { openDeveloperOptions(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_developer_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeveloperMode,
                                contentDescription = "خيارات المطور",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خيارات المطور")
                        }
                    }
                }
            }
        }

        // Method 1: Wireless Debugging (Android 11+)
        item {
            GuideSectionCard(
                title = "الطريقة 1: التفعيل عبر ADB اللاسلكي (بدون كمبيوتر)",
                subtitle = "موصى بها لأجهزة أندرويد 11 فما فوق (Wireless Debugging)",
                icon = Icons.Filled.Wifi,
                steps = listOf(
                    "افتح إعدادات الهاتف > حول الهاتف، واضغط 7 مرات متتالية على (رقم الإصدار / Build Number) لتفعيل خيارات المطور.",
                    "اتصل بشبكة Wi-Fi، ثم ادخل إلى (خيارات المطور) وفعّل خيار (التصحيح اللاسلكي / Wireless Debugging).",
                    "افتح تطبيق Shizuku واضغط على زر (الاقتران / Pairing) ثم اختر (الاقتران باستخدام رمز الاقتران).",
                    "من شاشة التصحيح اللاسلكي في الإعدادات، اضغط على (إقران الجهاز باستخدام رمز الإقران) وأدخل الرمز المكون من 6 أرقام في إشعار Shizuku.",
                    "ارجع إلى تطبيق Shizuku واضغط على (بدء / Start)؛ ستتحول حالة الاتصال هنا إلى (متصل) فوراً!"
                )
            )
        }

        // Method 2: USB Debugging via PC
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("usb_adb_guide_card"),
                shape = MaterialTheme.shapes.medium,
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Usb,
                                contentDescription = "ADB USB",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "الطريقة 2: التفعيل عبر كابل USB والكمبيوتر",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "متوافقة مع جميع إصدارات أندرويد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    StepBullet(1, "فعّل (تصحيح أخطاء USB / USB Debugging) من خيارات المطور في هاتفك.")
                    StepBullet(2, "وصّل الهاتف بالكمبيوتر عبر كابل USB ووافق على نافذة الوثوق بالكمبيوتر.")
                    StepBullet(3, "نفّذ الأمر التالي في موجه الأوامر (Terminal / CMD) على الكمبيوتر:")

                    // Terminal Command Box
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = "أمر ADB",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = ADB_START_COMMAND,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, ADB_START_COMMAND)
                                    commandCopied = true
                                },
                                modifier = Modifier.testTag("copy_adb_command_button")
                            ) {
                                Icon(
                                    imageVector = if (commandCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                    contentDescription = "نسخ أمر ADB",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (commandCopied) {
                        Text(
                            text = "تم نسخ أمر ADB إلى الحافظة بنجاح!",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Technical Architecture Explanation Card
        item {
            GuideSectionCard(
                title = "كيف يعمل Permission Grantor تقنياً؟",
                subtitle = "ربط ShizukuBinderWrapper مع IPackageManager",
                icon = Icons.Filled.Build,
                steps = listOf(
                    "يستعلم التطبيق عن الحزم المثبتة وصلاحياتها الحساسة باستخدام PackageManager وصلاحية QUERY_ALL_PACKAGES.",
                    "يتم تغليف Binder خدمة الحزم في النظام (package) عبر ShizukuBinderWrapper للوصول إلى واجهة IPackageManager بصلاحيات Shizuku.",
                    "عند الضغط على (منح الصلاحية عبر Shizuku)، يتم استدعاء grantRuntimePermission(packageName, permissionName, userId) مباشرة دون الحاجة لفتح إعدادات النظام يدوياً."
                )
            )
        }

        // Mandatory In-App Note
        item {
            ShizukuAdbLevelNoticeCard()
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GuideSectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    steps: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            steps.forEachIndexed { index, stepText ->
                StepBullet(number = index + 1, text = stepText)
            }
        }
    }
}

@Composable
private fun StepBullet(
    number: Int,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun openDeveloperOptions(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Throwable) {
        }
    }
}

private fun openShizukuAppOrStore(context: Context) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(
            ShizukuPackageManager.SHIZUKU_PACKAGE_NAME
        )
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        }
    } catch (_: Throwable) {
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("ADB Command", text))
    } catch (_: Throwable) {
    }
}
