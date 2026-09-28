package com.ttech.navi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.navi.domain.Maneuver
import com.ttech.navi.domain.ManeuverKind
import java.util.Locale
import kotlin.math.roundToInt

/** 数字を大きく、ラベルを小さく並べた、1項目の表示 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueSize: TextUnit = 22.sp) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = valueSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 画面用の距離(例: 「320 m」「1.2 km」「12 km」) */
fun shortDistance(m: Double): String {
    val v = m.coerceAtLeast(0.0)
    return when {
        v < 1000 -> "${((v / 10).roundToInt() * 10).coerceAtLeast(0)} m"
        v < 10_000 -> "${String.format(Locale.US, "%.1f", v / 1000)} km"
        else -> "${(v / 1000).roundToInt()} km"
    }
}

/** 曲がる方向・分岐などを表す矢印の絵 */
fun maneuverIcon(m: Maneuver?): ImageVector = when (m?.kind) {
    null -> Icons.Filled.Straight
    ManeuverKind.Arrive -> Icons.Filled.Flag
    ManeuverKind.Roundabout -> Icons.AutoMirrored.Filled.RotateRight
    ManeuverKind.OnRamp, ManeuverKind.OffRamp, ManeuverKind.Fork -> when (m.modifier) {
        "left", "sharp left", "slight left" -> Icons.Filled.TurnSlightLeft
        "right", "sharp right", "slight right" -> Icons.Filled.TurnSlightRight
        else -> Icons.Filled.Merge
    }
    ManeuverKind.Turn -> when (m.modifier) {
        "left", "sharp left" -> Icons.Filled.TurnLeft
        "right", "sharp right" -> Icons.Filled.TurnRight
        "slight left" -> Icons.Filled.TurnSlightLeft
        "slight right" -> Icons.Filled.TurnSlightRight
        "uturn" -> Icons.Filled.UTurnLeft
        else -> Icons.Filled.Straight
    }
}
