package eu.kanade.tachiyomi.ui.reader.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun BatteryTimeBar(
    progressPercent: Int,
    showBatteryAndTime: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    val ctx = LocalContext.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var timeText by remember { mutableStateOf(LocalTime.now().format(timeFormatter)) }
    var batteryPct by remember { mutableStateOf(-1) }

    if (showBatteryAndTime) {
        DisposableEffect(showBatteryAndTime) {
            if (!showBatteryAndTime) {
                onDispose { }
            } else {
                val timeReceiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        timeText = LocalTime.now().format(timeFormatter)
                    }
                }
                val batteryReceiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                        batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale) * 100).toInt().coerceIn(0, 100) else -1
                    }
                }

                // Initial sticky battery intent
                ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))?.let { initial ->
                    val level = initial.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = initial.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale) * 100).toInt().coerceIn(0, 100) else -1
                }

                // Register receivers
                ctx.registerReceiver(timeReceiver, android.content.IntentFilter(android.content.Intent.ACTION_TIME_TICK))
                ctx.registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

                onDispose {
                    try {
                        ctx.unregisterReceiver(timeReceiver)
                    } catch (_: Exception) {}
                    try {
                        ctx.unregisterReceiver(batteryReceiver)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = modifier.background(backgroundColor),
    ) {
        if (showBatteryAndTime) {
            Text(
                text = if (batteryPct >= 0) "$batteryPct%" else "--%",
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
            )
            Text(
                text = "$progressPercent%",
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = timeText,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                )
            }
        }
    }
}
