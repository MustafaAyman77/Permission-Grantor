package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.OnAmberWarningContainer
import com.example.ui.theme.OnCrimsonErrorContainer
import com.example.ui.theme.OnEmeraldSuccessContainer

/**
 * Safely converts an Android [Drawable] to an [ImageBitmap] and renders it in Compose.
 */
@Composable
fun AppIconImage(
    drawable: Drawable?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val imageBitmap: ImageBitmap? = remember(drawable) {
        drawable?.let { d ->
            try {
                if (d is BitmapDrawable && d.bitmap != null) {
                    return@let d.bitmap.asImageBitmap()
                }
                val width = d.intrinsicWidth.coerceAtLeast(96).coerceAtMost(256)
                val height = d.intrinsicHeight.coerceAtLeast(96).coerceAtMost(256)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                d.setBounds(0, 0, canvas.width, canvas.height)
                d.draw(canvas)
                bitmap.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

/**
 * Animated Connection Status Pill ("متصل" / "غير متصل").
 */
@Composable
fun ConnectionStatusBadge(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isConnected) EmeraldSuccessContainer else CrimsonErrorContainer,
        label = "connBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isConnected) OnEmeraldSuccessContainer else OnCrimsonErrorContainer,
        label = "connContent"
    )
    val dotColor by animateColorAsState(
        targetValue = if (isConnected) EmeraldSuccess else CrimsonError,
        label = "connDot"
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .border(
                width = 1.dp,
                color = dotColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50)
            )
            .testTag("shizuku_connection_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(if (isConnected) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = if (isConnected) {
                    stringResource(R.string.status_connected)
                } else {
                    stringResource(R.string.status_disconnected)
                },
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Permission Status Badge ("ممنوحة" / "غير ممنوحة").
 */
@Composable
fun PermissionStatusBadge(
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isGranted) EmeraldSuccessContainer else AmberWarningContainer,
        label = "permBadgeBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isGranted) OnEmeraldSuccessContainer else OnAmberWarningContainer,
        label = "permBadgeText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isGranted) EmeraldSuccess.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f),
        label = "permBadgeBorder"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(50))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = if (isGranted) "ممنوحة" else "غير ممنوحة",
                tint = if (isGranted) EmeraldSuccess else AmberWarning,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (isGranted) {
                    stringResource(R.string.badge_granted)
                } else {
                    stringResource(R.string.badge_ungranted)
                },
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
        }
    }
}

/**
 * Mandatory In-App Note Card clarifying that Shizuku provides ADB-level access (not full root)
 * and some system-protected permissions may still require manual user intervention.
 */
@Composable
fun ShizukuAdbLevelNoticeCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("adb_level_notice_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "ملاحظة تقنية",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.shizuku_note_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.shizuku_note_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Dismissible alert banner for success or error feedback (e.g. SecurityException / RemoteException).
 */
@Composable
fun StatusFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isError) CrimsonErrorContainer else EmeraldSuccessContainer
    val textColor = if (isError) OnCrimsonErrorContainer else OnEmeraldSuccessContainer
    val accentColor = if (isError) CrimsonError else EmeraldSuccess

    Card(
        onClick = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
            .testTag("status_feedback_banner"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = if (isError) "رسالة خطأ" else "رسالة نجاح",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
