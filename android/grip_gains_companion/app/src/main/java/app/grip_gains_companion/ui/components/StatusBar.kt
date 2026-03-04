package app.grip_gains_companion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.grip_gains_companion.ui.theme.GripGainsTheme

@Composable
fun StatusBar(
    force: Double,
    engaged: Boolean,
    calibrating: Boolean,
    waitingForSamples: Boolean,
    calibrationTimeRemaining: Long,
    weightMedian: Double,
    targetWeight: Double?,
    isOffTarget: Boolean,
    offTargetDirection: Double?,
    sessionMean: Double,
    sessionStdDev: Double,
    useLbs: Boolean,
    expanded: Boolean,
    deviceShortName: String,
    onUnitToggle: () -> Unit,
    onSettingsTap: () -> Unit
) {
    // Force dark mode just for this bar so it perfectly matches the web canvas
    GripGainsTheme(darkTheme = true) {
        val targetColor = when {
            calibrating -> MaterialTheme.colorScheme.tertiary
            engaged -> {
                if (isOffTarget) {
                    if (offTargetDirection != null && offTargetDirection > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                } else {
                    MaterialTheme.colorScheme.primary
                }
            }
            // Hardcode the Grip Gains gray instead of using the theme surface
            else -> Color(0xFF1A2231)
        }

        val animatedColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "StatusBarColor"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(animatedColor)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (calibrating) {
                        Text(
                            "CALIBRATING...",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Hold steady for ${calibrationTimeRemaining / 1000}s",
                            color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    } else {
                        val displayForce = if (useLbs) force * 2.20462 else force
                        val unitStr = if (useLbs) "LBS" else "KG"

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                String.format("%.1f", displayForce),
                                color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onUnitToggle() }
                            )
                            Text(
                                unitStr,
                                color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }

                        if (targetWeight != null) {
                            val displayTarget = if (useLbs) targetWeight * 2.20462 else targetWeight
                            val statusText = when {
                                !engaged -> "Target: ${String.format("%.1f", displayTarget)} $unitStr"
                                isOffTarget -> if (offTargetDirection != null && offTargetDirection > 0) "TOO HIGH!" else "TOO LOW!"
                                else -> "ON TARGET"
                            }
                            Text(
                                statusText,
                                color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                IconButton(onClick = onSettingsTap) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (expanded && !calibrating) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        label = "MEDIAN",
                        value = if (useLbs) weightMedian * 2.20462 else weightMedian,
                        engaged = engaged,
                        isOffTarget = isOffTarget
                    )
                    StatItem(
                        label = "MEAN",
                        value = if (useLbs) sessionMean * 2.20462 else sessionMean,
                        engaged = engaged,
                        isOffTarget = isOffTarget
                    )
                    StatItem(
                        label = "DEV",
                        value = if (useLbs) sessionStdDev * 2.20462 else sessionStdDev,
                        engaged = engaged,
                        isOffTarget = isOffTarget
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "DEVICE",
                            color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            deviceShortName,
                            color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Double, engaged: Boolean, isOffTarget: Boolean) {
    Column {
        Text(
            label,
            color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            String.format("%.1f", value),
            color = if (engaged && !isOffTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}