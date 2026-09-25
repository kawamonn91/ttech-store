package com.ttech.travelwishlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { content() }
    else Card(modifier = modifier.fillMaxWidth(), colors = colors) { content() }
}

/** 星の色(琥珀色) */
val StarColor = androidx.compose.ui.graphics.Color(0xFFF59E0B)

/** 星の表示(count 個ぶん塗る) */
@Composable
fun Stars(count: Int, max: Int = 3, size: Dp = 16.dp) {
    Row {
        repeat(max) { i ->
            Icon(
                if (i < count) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i < count) StarColor else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** 星を押して選ぶ入力。同じ星をもう一度押すと、0に戻す([allowZero] のとき) */
@Composable
fun StarInput(value: Int, onChange: (Int) -> Unit, max: Int = 3, allowZero: Boolean = false, size: Dp = 32.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(max) { i ->
            val on = i < value
            Icon(
                if (on) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "${i + 1}",
                tint = if (on) StarColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size).clickable { onChange(if (allowZero && value == i + 1) 0 else i + 1) },
            )
        }
    }
}
