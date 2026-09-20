package com.kawamonn.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kawamonn.store.data.api.AppSummaryDto
import com.kawamonn.store.util.formatCount

@Composable
fun AppIcon(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size / 4.5f)
    if (url == null) {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    }
}

@Composable
fun RatingLabel(avg: Double, count: Int) {
    if (count == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Star,
            contentDescription = "評価",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            String.format(java.util.Locale.US, "%.1f", avg),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
fun AppRow(app: AppSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app.iconUrl, 56.dp)
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.shortDesc.isNotBlank()) {
                Text(
                    app.shortDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RatingLabel(app.ratingAvg, app.ratingCount)
                Text(
                    "${formatCount(app.downloadCount)} DL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                app.category?.let {
                    Text(it.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun <T> LoadContent(state: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("再読み込み") }
        }
        is Load.Ready -> content(state.data)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
